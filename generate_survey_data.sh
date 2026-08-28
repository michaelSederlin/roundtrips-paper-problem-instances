#!/usr/bin/env bash
# Generates the survey (upbd.csv / mddr.csv) from a completed measurement run.
#
#   ./generate_survey_data.sh
#
# All paths and settings are configured in the block below.
set -euo pipefail

cd "$(dirname "$0")"

# ----------------------------- configuration -----------------------------
PY=/opt/homebrew/Caskroom/miniconda/base/envs/renate/bin/python

DATA_DIR=output/measurement_data
OUTPUT_DIR=data/survey
# -------------------------------------------------------------------------

if [[ ! -d "${DATA_DIR}" ]]; then
  echo "Data directory not found: ${DATA_DIR}" >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

"${PY}" -m src.main.python.create_amal_survey \
  --input "${DATA_DIR}" \
  --output "${OUTPUT_DIR}" \
  --states-xml-filename "final_states_merged.xml" \
  --population-filename population_merged.tsv

echo
echo "Done. Survey written to: ${OUTPUT_DIR}"