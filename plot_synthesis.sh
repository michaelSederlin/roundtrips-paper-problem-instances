#!/usr/bin/env bash
# Merges the partition output of a synthesis run and produces the plots for it.
#
#   ./plot_synthesis.sh
#
# All paths and settings are configured in the block below.
set -euo pipefail

cd "$(dirname "$0")"

# ----------------------------- configuration -----------------------------
PY=/opt/homebrew/Caskroom/miniconda/base/envs/renate/bin/python

RESULTS_DIR=output/ProductionRun_1000
SAVE_DIR=output/ProductionRun_1000/plots
MERGED_DIR=output/ProductionRun_1000/partitions_merged
MERGED_STATES_XML=output/ProductionRun_1000/partitions_merged/final_states_merged.xml
MERGED_POPULATION=output/ProductionRun_1000/partitions_merged/population_merged.tsv

POPULATION_FILENAME=population.csv
TARGET_FILENAME=population_merged.tsv
CONVERGENCE_STATS_FILENAME=convergenceStats.tsv
WINDOW=10
# -------------------------------------------------------------------------

MOD=src.main.python.plotting

if [[ ! -d "${RESULTS_DIR}" ]]; then
  echo "Results directory not found: ${RESULTS_DIR}" >&2
  exit 1
fi

mkdir -p "${SAVE_DIR}"

echo
echo "=== [1/4] Merge population ==="
"${PY}" -m "${MOD}.merge_population_csv" \
  --results-dir "${RESULTS_DIR}" \
  --population-filename "${POPULATION_FILENAME}" \
  --target-filename "${TARGET_FILENAME}"
echo "Wrote ${MERGED_POPULATION}"

echo
echo "=== [2/4] Merge states ==="
"${PY}" -m "${MOD}.merge_states_xml" \
  --results-dir "${RESULTS_DIR}" \
  --population-filename "${POPULATION_FILENAME}" \
  --convergence-stats-filename "${CONVERGENCE_STATS_FILENAME}" \
  --window "${WINDOW}"
echo "Wrote ${MERGED_STATES_XML}"

echo
echo "=== [3/4] Preference matching ==="
"${PY}" -m "${MOD}.plot_preference_matching" \
  --results-dir "${RESULTS_DIR}" \
  --save-dir "${SAVE_DIR}" \
  --states-xml "${MERGED_STATES_XML}" \
  --population-csv "${MERGED_POPULATION}" \
  --window "${WINDOW}"

echo
echo "=== [4/4] Time structure histogram ==="
"${PY}" -m "${MOD}.plot_time_structure_histogram" \
  --results-dir "${RESULTS_DIR}" \
  --save-dir "${SAVE_DIR}" \
  --states-xml "${MERGED_STATES_XML}"

echo
echo "Done. Plots written to: ${SAVE_DIR}"
