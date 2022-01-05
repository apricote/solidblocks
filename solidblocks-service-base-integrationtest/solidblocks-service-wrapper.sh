#!/usr/bin/env bash

set -eu

DIR="$( cd "$(dirname "$0")" ; pwd -P )"

SOLIDBLOCKS_DIR="${DIR}/test"
export $(xargs < "${SOLIDBLOCKS_DIR}/instance/environment")

DOWNLOAD_DIR="${SOLIDBLOCKS_DIR}/download"
SOLIDBLOCKS_COMPONENT="solidblocks-service-base-integrationtest"

mkdir -p "${DOWNLOAD_DIR}"
curl "http://localhost:8081/pellepelster/solidblocks/solidblocks/${SOLIDBLOCKS_COMPONENT}/${SOLIDBLOCKS_VERSION}/${SOLIDBLOCKS_COMPONENT}-${SOLIDBLOCKS_VERSION}.tar" > "${DOWNLOAD_DIR}/${SOLIDBLOCKS_COMPONENT}-${SOLIDBLOCKS_VERSION}.tar"

#mkdir -p "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}"
(
  cd "${SOLIDBLOCKS_DIR}"
  tar -xf "${DOWNLOAD_DIR}/${SOLIDBLOCKS_COMPONENT}-${SOLIDBLOCKS_VERSION}.tar"
  rm -f "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}-active"
  ln -s "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}-${SOLIDBLOCKS_VERSION}" "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}-active"
)

cd "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}-active"
exec "${SOLIDBLOCKS_DIR}/${SOLIDBLOCKS_COMPONENT}-active/bin/${SOLIDBLOCKS_COMPONENT}"