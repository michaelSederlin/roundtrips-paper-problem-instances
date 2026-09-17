# Roundtrips Problem Instances

## Generic probabilistic spatiotemporal transport model

**Gunnar Flötteröd**
_Linköping University; Swedish National Road and Transport Research Institute_

**Franz-Xaver Rupprecht**
_BOKU University_

**Michael Sederlin**
_Linköping University; Swedish National Road and Transport Research Institute_



**Synthesis** simulates a ground-truth
population on a grid world; **survey generation** turns it into a survey that looks like
observed travel data; **disaggregate calibration** tries to recover the population from
that survey alone.

## Steps

1. `mvn package` — build
2. **Synthesis** — `synthesis.SynthesisRunner @measurement-data-parameters.txt` (and the validation variant)
3. **Merging and plotting** partition output — `merge_population_csv` + `merge_states_xml` 
        Diagnostic plots of the synthesis can be generated with `./plot_synthesis.sh` which calls python functions
4. **Survey** — `./generate_survey_data.sh` calls python scripts to generate the survey data
5. **Calibration** — `disaggregate.Runner @disaggregate-calibration.txt`
6. **Results** - Generate plots for paper using `src/main/python/plot_results.py`

Arguments live in `*.txt` files at the repo root, passed with picocli's `@file` syntax —
configure there, not on the command line. Java 17; Python steps need the `renate` conda
env (`PY` is set at the top of each shell script).

## 2. Synthesis

Produces the "true" population. Two instances: measurement (the survey is drawn from it)
and validation (held out).

```bash
java -cp target/<jar> synthesis.SynthesisRunner @measurement-data-parameters.txt
java -cp target/<jar> synthesis.SynthesisRunner @validation-data-parameters.txt
```

Key settings: `--population-size`, `--grid-size`, `--maxIter`, activity opening hours.
Writes one numbered subdirectory per thread into `--output-dir`. Results used below are
`output/measurement_data/` and `output/validation_data/`.

## 3. Merge partitions

Synthesis writes per-partition output; the survey step needs it merged. Both scripts are
modules under `src/main/python/plotting/`:

```bash
PY=/opt/homebrew/Caskroom/miniconda/base/envs/renate/bin/python
DATA_DIR=calibration/measurement_data

"$PY" -m src.main.python.plotting.merge_population_csv \
  --results-dir "$DATA_DIR" --population-filename population.csv \
  --target-filename population_merged.tsv

"$PY" -m src.main.python.plotting.merge_states_xml \
  --results-dir "$DATA_DIR" --population-filename population.csv \
  --convergence-stats-filename convergenceStats.tsv --window 10
```

Both land in `partitions_merged/`: `population_merged.tsv` (person attributes) and
`final_states_merged.xml` (all round trips, globally ordered, with episodes).
`plot_synthesis.sh` runs these same two steps, so if you plot first you can skip this.

Partitions each number their people from 0, so the merge qualifies `idx` as
`{partition}_{idx}` (`0_0`, `0_1`, …) to get a globally unique key. Note the merged file
is **tab**-separated while the per-partition `population.csv` are comma-separated — the
delimiter comes from the file suffix.

## 4. Survey generation

```bash
./generate_survey_data.sh     # set DATA_DIR / OUTPUT_DIR at the top
```

Runs `src/main/python/create_amal_survey.py` over the merged output, writing to
`data/survey/`:

| File | Contents |
| --- | --- |
| `upbd.csv` | One row per respondent: `UENR`, `AGE`, `INKUP`, `BOST_S`, activity durations |
| `mddr.csv` | One row per trip leg: origin, destination, times, destination `activity` |
| `states.xml` | Round trips for the selected respondents |
| `finalState.json` | Global warm-start state |
| `nodes.tsv`, `edges.tsv`, `od_matrix.tsv`, `opportunity_weights.csv` | Network and weights |

`UENR` is the `{partition}_{idx}` id — a **string**. Never cast it to `int`: Python reads
`_` as a digit separator, so `int("0_0")` silently returns `0` instead of raising.

Two synthesis XML formats exist here and differ in where the activity lives:

| Format | `MoveEpisode to=` | `StayEpisode loc=` | extra |
| --- | --- | --- | --- |
| RENATE (current, `output/`) | `"(1,3)"` | `"(1,3)[WORK]"` | `labels="[WORK]"` |
| AMAL (older, `data/ground_truth/`) | `"(1,3)[WORK]"` | `"(1,3)[WORK]"` | `activity="WORK"` |

`StayEpisode.loc` is the only field both populate identically — read the activity there
to support both. `build_mddr` pairs each `MoveEpisode` with the `StayEpisode` after it;
the final leg returns home and wraps to the first stay, giving `HOME`.

## 5. Disaggregate calibration

```bash
java -cp target/<jar> disaggregate.Runner @disaggregate-calibration.txt
```

`--scenario-dir` points at the survey directory. The runner loads the survey, splits
agents across threads, runs each partition, then merges back into `partitions_merged/`.

Remote runs: `./run_disaggregate_ws.sh {build|push|run|status|logs|clean}` builds the
image, ships it to the workstation named by `MACHINE`, and runs it via
`docker-compose.yml`. Override the argument file with `ARGS=other-args.txt`.

### Warm starting

`--warm-start-file data/survey/finalState.json` starts from the synthesis end state
instead of cold.

The file is a flat JSON list indexed `0..n-1`, **positionally aligned with `upbd.csv` row
order** — the runner reads `getRoundTrip(partFrom + j)` and ignores person ids. The
per-partition `final_state.json` files cannot be used directly, as each is re-indexed
from 0; `create_amal_survey.py` builds the global file from `final_states_merged.xml`.

Alignment survives partitioning (`splitEvenly` takes contiguous in-order slices,
`partFrom` tracks the offset) and is unaffected by `--n-respondents`, which subsets
respondents rather than agents. It breaks if a person is **skipped at load** — no legs in
`mddr.csv`, or a failed `Person.validate()` — since every later agent then shifts by one.
Check the log line `Loaded N of M persons.`: if N < M, the warm start is misaligned.

## 6. Plotting

```bash
./plot_synthesis.sh     # set RESULTS_DIR at the top
```

Merges partition output and writes plots to `<RESULTS_DIR>/plots`. Individual scripts are
in `src/main/python/plotting/`.

## Notes

- **Gender is optional throughout.** `SurveyReader` yields `null` when `upbd.csv` has no
  `SEX` column, `Person.validate()` warns rather than throws, and `TravelerKernel` skips
  its gender term when either side is null. The `synthesis` package's gender code is
  commented out, so synthetic travelers have no gender and that term is currently inert.
- **Python failures here are silent.** A wrong separator, XPath, or id cast yields empty
  or truncated output with a zero exit code. Check row counts and spot-check values
  rather than trusting that the script finished.

### Open items

- Remove duplicate home node enforcer weight
