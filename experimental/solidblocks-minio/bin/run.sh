#!/usr/bin/env bash

set -eu

if [[ -z "${MINIO_ADMIN_USER:-}" ]]; then
  echo "MINIO_ADMIN_USER not set"
  exit 1
fi

if [[ -z "${MINIO_ADMIN_PASSWORD:-}" ]]; then
  echo "MINIO_ADMIN_PASSWORD not set"
  exit 1
fi

if [[ -z "${MINIO_TLS_PRIVATE_KEY:-}" ]]; then
  echo "MINIO_TLS_PRIVATE_KEY not set"
  exit 1
fi

if [[ -z "${MINIO_TLS_PUBLIC_KEY:-}" ]]; then
  echo "MINIO_TLS_PUBLIC_KEY not set"
  exit 1
fi

if ! mount | grep "${LOCAL_STORAGE_DIR}"; then
    echo "storage dir '${LOCAL_STORAGE_DIR}' not mounted"
    exit 1
fi

export MINIO_ROOT_USER="${MINIO_ADMIN_USER}"
export MINIO_ROOT_PASSWORD="${MINIO_ADMIN_PASSWORD}"

MINIO_OPTS="--console-address :9001"

mkdir -p /minio/certificates
echo -n "${MINIO_TLS_PRIVATE_KEY}" > /minio/certificates/private.key
echo -n "${MINIO_TLS_PUBLIC_KEY}" > /minio/certificates/public.crt

MINIO_OPTS="${MINIO_OPTS} --certs-dir /minio/certificates --address :443"

/minio/bin/provision.sh "${BUCKET_SPECS:-}" &

exec /minio/bin/minio server ${MINIO_OPTS} "${DATA_DIR}"