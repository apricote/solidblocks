#!/usr/bin/env bash

set -eu

export DB_ADMIN_USERNAME="${USER}"
export DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-$(uuidgen)}"

function log() {
  echo "[solidblocks-rds-postgresql] $*"
}

function ensure_environment_variables() {
  for var in $@; do
    if [[ -z "${!var:-}" ]]; then
      log "'$var' is empty or not set"
      exit 1;
    fi
  done
}

ensure_environment_variables DB_INSTANCE_NAME

if [[ $((DB_BACKUP_S3 + DB_BACKUP_LOCAL)) == 0 ]]; then
  log "either 'DB_BACKUP_S3' or 'DB_BACKUP_LOCAL' has to be activated"
  exit 1
fi

if [[ ${DB_BACKUP_S3:-0} == 1 ]]; then
  ensure_environment_variables DB_BACKUP_S3_CA_PUBLIC_KEY DB_BACKUP_S3_HOST DB_BACKUP_S3_BUCKET DB_BACKUP_S3_ACCESS_KEY DB_BACKUP_S3_SECRET_KEY

  if [[ -n "${DB_BACKUP_S3_CA_PUBLIC_KEY:-}" ]]; then
    mkdir -p /rds/certificates
    echo -n "${DB_BACKUP_S3_CA_PUBLIC_KEY}" | base64 -d > /rds/certificates/ca.pem
  fi
fi

export DB_BACKUP_LOCAL_DIR="${DB_BACKUP_LOCAL_DIR:-/storage/backup}"

if [[ ${DB_BACKUP_LOCAL:-0} == 1 ]]; then
  ensure_environment_variables DB_BACKUP_LOCAL_DIR

  if ! mount | grep "${DB_BACKUP_LOCAL_DIR}"; then
      log "local backup dir '${DB_BACKUP_LOCAL_DIR}' not mounted"
      exit 1
  fi
fi

if ! mount | grep "${DATA_DIR}"; then
    log "storage dir '${DATA_DIR}' not mounted"
    exit 1
fi

log "starting..."

export PG_DATA_DIR="${DATA_DIR}/${DB_INSTANCE_NAME}"

gomplate --input-dir /rds/templates/config/ --output-map='/rds/config/{{ .in | strings.ReplaceAll ".template" "" }}'
gomplate --input-dir /rds/templates/bin/ --output-map='/rds/bin/{{ .in | strings.ReplaceAll ".template" "" }}'

POSTGRES_BASE_DIR="/usr/libexec/postgresql14"
POSTGRES_BIN_DIR="${POSTGRES_BASE_DIR}"

mkdir -p "${PG_DATA_DIR}"

function psql_execute() {
  local database=${1:-}
  local query=${2:-}
  psql -h /rds/socket --username "${DB_ADMIN_USERNAME}" --field-separator-zero --record-separator-zero --tuples-only --quiet -c "${query}" "${database}"
}

function pgbackrest_execute() {
  pgbackrest --config /rds/config/pgbackrest.conf --log-path=/rds/log --stanza=${DB_INSTANCE_NAME} "$@"
}

function psql_count() {
  psql_execute "${1}" "${2}" | tr -d '[:space:]'
}

function ensure_databases() {

    for database_var in "${!DB_DATABASE_@}"; do

      local database_id="${database_var#"DB_DATABASE_"}"
      local database="${!database_var:-}"
      if [[ -z "${database}" ]]; then
        echo "provided database name was empty"
        continue
      fi

      local database_user_var="DB_USERNAME_${database_id}"
      local username="${!database_user_var:-}"
      if [[ -z "${username}" ]]; then
        echo "no username provided for database '${database}'"
        continue
      fi


      local database_password_var="DB_PASSWORD_${database_id}"
      local password="${!database_password_var:-}"
      if [[ -z "${password}" ]]; then
        echo "no password provided for database '${database}'"
        continue
      fi

      echo "ensuring database '${database}' with user '${username}'"
      ensure_database "${database}"
      ensure_db_user "${database}" "${username}" "${password}"

    done
}

function ensure_database() {
  local database="${1:-}"

  if [[ $(psql_count "postgres" "SELECT count(datname) FROM pg_database WHERE datname = '${database}';") == "0" ]]; then
    log "creating database '${database}'"
    psql_execute "postgres" "CREATE DATABASE \"${database}\""
  fi
}

function ensure_permissions() {
  log "setting permissions for '${username}'"
  psql_execute "${database}" "GRANT ALL PRIVILEGES ON DATABASE \"${database}\" TO \"${username}\""
  psql_execute "${database}" "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"${username}\""
  psql_execute "${database}" "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"${username}\""
}

function ensure_db_user() {
  local database="${1:-}"
  local username="${2:-}"
  local password="${3:-}"

  if [[ $(psql_count "${database}" "SELECT count(u.usename) FROM pg_catalog.pg_user u WHERE u.usename = '${username}';") == "0" ]]; then
    log "creating user '${username}'"
    psql_execute "${database}" "CREATE USER \"${username}\" WITH ENCRYPTED PASSWORD '${password}'"
  else
    log "setting password for '${username}'"
    psql_execute "${database}" "ALTER USER \"${username}\" WITH ENCRYPTED PASSWORD '${password}'"
  fi

  log "granting all privileges for '${username}' on '${database}'"

  if [[ -f "${PG_DATA_DIR}/solidblocks_current_db_username_${database}" ]]; then
    local last_db_username=$(cat "${PG_DATA_DIR}/solidblocks_current_db_username_${database}")

    if [[ "${last_db_username}" != "${username}" ]]; then
      log "reassigning ownerships from '${last_db_username}' to '${username}'"

      psql_execute "${database}" "REASSIGN OWNED BY \"${last_db_username}\" TO \"${username}\""
    fi
  fi

  ensure_permissions

  echo "${username}" > "${PG_DATA_DIR}/solidblocks_current_db_username_${database}"
}

function init_db() {
  log "initializing database instance"
  ${POSTGRES_BIN_DIR}/initdb --username="${DB_ADMIN_USERNAME}" --encoding=UTF8 --pwfile=<(echo "${DB_ADMIN_PASSWORD}") -D "${PG_DATA_DIR}" || true

  cp -v /rds/config/postgresql.conf "${PG_DATA_DIR}/postgresql.conf"
  cp -v /rds/config/pg_hba.conf "${PG_DATA_DIR}/pg_hba.conf"

  # make sure we only listen public when DB is ready to go
  ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" start --options="-c listen_addresses=''"

  pgbackrest --config /rds/config/pgbackrest.conf --log-path=/rds/log  --log-level-console=info --stanza=${DB_INSTANCE_NAME} stanza-create

  ensure_databases

  log "executing initial backup"
  pgbackrest_execute --log-level-console=info --type=full backup

  ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" stop
}

function pgbackrest_status_code() {
  PGBACKREST_INFO=$(pgbackrest_execute --output=json info)

  if [[ $(echo ${PGBACKREST_INFO} | jq length) -gt 0 ]]; then
    BACKUP_INFO=$(echo ${PGBACKREST_INFO} | jq ".[] | select(.name == \"${DB_INSTANCE_NAME}\")")
    echo ${BACKUP_INFO} | jq -r '.status.code'
  else
    echo "99"
  fi
}

if [[ ! "$(ls -A ${PG_DATA_DIR})" ]]; then
  log "data dir is empty"

  if [[ $(pgbackrest_status_code) -eq 0 ]]; then

    log "restoring database from backup"
    # make sure we only listen public when DB is ready to go
    pgbackrest_execute --db-path=${PG_DATA_DIR} restore --recovery-option="recovery_end_command=/rds/bin/recovery_complete.sh"

    sleep 5

    log "starting db for recovery"
    ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" start --options="-c listen_addresses=''"

    while [[ -f /tmp/recovery_complete ]]; do
      log "waiting for recovery completion"
      sleep 5
    done

    until [[ "$(psql_execute "postgres" 'SELECT pg_is_in_recovery();' | tr -d '[:space:]')" == "f" ]]; do
      log "waiting for server to be ready"
      sleep 5
    done

    ensure_databases

    ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" stop
  else
    init_db
  fi
else
  log "data dir is not empty"

  rm -f /rds/socket/*
  rm -f "${PG_DATA_DIR}/postmaster.pid"

  ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" start --options="-c listen_addresses=''"

  ensure_databases

  log "setting password for '${DB_ADMIN_USERNAME}'"
  psql_execute "postgres" "ALTER USER \"${DB_ADMIN_USERNAME}\" WITH ENCRYPTED PASSWORD '${DB_ADMIN_PASSWORD}'"

  ${POSTGRES_BIN_DIR}/pg_ctl -D "${PG_DATA_DIR}" stop
fi

cp -v /rds/config/postgresql.conf "${PG_DATA_DIR}/postgresql.conf"
cp -v /rds/config/pg_hba.conf "${PG_DATA_DIR}/pg_hba.conf"

log "provisioning completed"
exec ${POSTGRES_BIN_DIR}/postgres -D "${PG_DATA_DIR}"
