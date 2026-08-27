#!/usr/bin/env bash
set -euo pipefail

HOST="${MACHINE:-win02032}"

case "${HOST}" in
  tal1)
    WS_DIR='C:/Users/TAL-User-1/OneDrive - VTI/Skrivbordet/Michael/disaggregate'
    WS_BASH="C:/Users/TAL-User-1/AppData/Local/Programs/Git/bin/bash.exe"
    ;;
  tal2)
    WS_DIR='C:/Users/tal-user-2/Desktop/Michael/disaggregate'
    WS_BASH="C:/Users/tal-user-2/AppData/Local/Programs/Git/bin/bash.exe"
    ;;
  win02032)
    WS_DIR='C:/Users/micse02/Desktop/michael/disaggregate'
    WS_BASH="C:\Program Files\Git\bin\bash.exe"
    ;;
  *)
    echo "unknown MACHINE: ${HOST}" >&2; exit 1 ;;
esac

ARGS_FILE="${ARGS:-amal-estimation-arguments.txt}"
LOCAL_OUTPUT="$(dirname "$0")/output/amal"

ssh_ws() {
  if [[ -n "${WS_BASH}" ]]; then
    ssh "${HOST}" "\"${WS_BASH}\" -lc \"$*\""
  else
    ssh "${HOST}" "$@"
  fi
}

build() {
  echo
  echo "=== [1/2] Maven package ==="
  mvn package

  echo
  echo "=== [2/2] Docker build ==="
  docker build --platform linux/amd64 -f dockerfiles/disaggregate/Dockerfile -t disaggregate .
}

push() {
  echo
  echo "=== Transfer image to WS ${HOST} ==="
  docker save disaggregate:latest \
    | dd status=progress bs=1M \
    | gzip \
    |  ssh_ws "gunzip | docker load"
}

run() {
  local service="${1:-disaggregate}"
  echo
  echo "=== Launch detached on WS ${HOST} (${service}) ==="
  scp "${ARGS_FILE}" "${HOST}:${WS_DIR}/${service}-arguments.txt"
  scp docker-compose.yml "${HOST}:${WS_DIR}/docker-compose.yml"
  ssh_ws "mkdir -p '${WS_DIR}/output/${service}'"
  ssh_ws "cd '${WS_DIR}' && docker compose up -d ${service}"

  echo
  echo "Launched in the background. Safe to disconnect."
  echo "  Check progress: $0 status   (or: $0 logs)"
  echo "  Fetch results:  $0 collect  (once status shows it has exited)"
}


status() {
  echo "=== Status of disaggregate on WS ${HOST} ==="
  ssh_ws "cd '${WS_DIR}' && docker compose ps"
}

logs() {
  local service="${1}"
  echo "=== Last 80 lines of logs from ${service} on WS ${HOST} ==="
  ssh_ws "cd '${WS_DIR}' && docker compose logs --tail 150 ${service}"
}


# Remove the stopped container on the WS once you've collected results.
clean() {
  local service="${1:-disaggregate}"
  echo "=== Removing ${service} container on WS ${HOST} ==="
  ssh_ws "cd '${WS_DIR}' && docker compose down"
}

# Stop the running container (keeps it for inspect/collect; use clean to remove).
shutdown() {
  local service="${1:-disaggregate}"
  echo "=== Stopping ${service} on WS ${HOST} ==="
  ssh_ws "cd '${WS_DIR}' && docker compose stop ${service}"
  echo "Container stopped. Run '$0 collect' to fetch results, '$0 clean' to remove it."
}

case "${1:-}" in
  build)    build         ;;
  push)     push          ;;
  run)      run "${2:-disaggregate}" ;;
  status)   status        ;;
  logs)     logs "${2}" ;;
  wait)     wait_done "${2:-disaggregate}" ;;
  clean)    clean "${2:-disaggregate}"     ;;
  shutdown) shutdown "${2:-disaggregate}"       ;;
  *) echo "usage: $0 {build|push|run|launch|sequence|sequence-status|status|logs|wait|collect|clean|shutdown}" >&2; exit 1 ;;
esac