# Roundtrips Problem Instances

## Generic probabilistic spatiotemporal transport model

**Gunnar Flötteröd**
_Linköping University; Swedish National Road and Transport Research Institute_

**Franz-Xaver Rupprecht**
_BOKU University_

**Michael Sederlin**
_Linköping University; Swedish National Road and Transport Research Institute_



**Synthesis** simulates a ground-truth population on a grid world; 
**survey generation** turns it into a survey that looks like observed travel data; 
**disaggregate calibration** tries to recover the population from that survey alone.
**aggregate calbration** 

## Running the simulation, step by step

Settings for the populations in the paper are in the 'args/' folder.

1. **Build the code.**
   ```bash
   mvn package
   ```

2. **Create the population.** 
This step makes up a "true" population of people and their daily trips, on a made-up grid. Run it twice, once for each settings file:
   ```bash
   java -cp target/<jar> synthesis.SynthesisRunner @args/measurement-data-parameters.txt
   java -cp target/<jar> synthesis.SynthesisRunner @args/validation-data-parameters.txt
   ```
   One population will be used to make the survey (measurement); the other is kept aside
   to check results later (validation).

4. **Merge and plot the population.** 
The population is created in separate pieces; join them into single files, and optionally make diagnostic plots:
   ```bash
   ./plot_synthesis.sh
   ```

### Disaggregate calibration path

1. **Create the survey.** 
Turn the synthesized population into something that looks like a real travel survey:
   ```bash
   ./generate_survey_data.sh
   ```

3. **Run the calibration.** 
This step tries to recover the population using only the survey, without knowing the true population from step 2 above:
   ```bash
   java -cp target/<jar> disaggregate.Runner @args/disaggregate-calibration.txt
   ```

4. **Make result plots.**
   ```bash
   python src/main/python/plot_disaggregate_results.py
   ```

### Aggregate calibration path

1. **Create the aggregate measures.** This turns the made-up population into summary
   counts and totals (trip counts between zones, home and work location counts,
   duration/departure tables, and so on), the kind of data you'd get from traffic counts
   or census tables rather than an individual-level survey.

   > TODO (author of the aggregate branch, please fill in): which script or manual step
   > produces the files in `data/aggregated_calibration/measurement/` from the
   > synthesized population?

2. **Run the calibration.**
   ```bash
   java -cp target/<jar> aggregate.RunnerRtAmalWithActivities
   ```

3. **Make result plots.**
   ```bash
   python src/main/python/plot_results_aggregated.py
   ```

## A note on splitting the work into pieces

Population creation and disaggregate calibration each split the population into separate pieces (partitions) and run each piece on its own, often at the same time on different processor cores. This is only correct because every person is handled completely on their own — one person's trips never depend on another person's trips. 
Splitting the population into pieces, running each piece separately, and joining the results back together therefore gives the exact same answer as running everyone together in one piece. The number of pieces is set in the arguments file and does not change the results, only how fast the run finishes.

## Technical notes

- Synthesis and calibration each write one numbered subdirectory per partition;
  `plot_synthesis.sh` merges these into `partitions_merged/`
  (`population_merged.tsv` and `final_states_merged.xml`).
- Partitions each number their people from 0, so the merge qualifies `idx` as
  `{partition}_{idx}` (`0_0`, `0_1`, …) to get a globally unique key. The merged file is
  **tab**-separated while the per-partition `population.csv` are comma-separated.
- Two synthesis XML formats exist and differ in where the activity lives: RENATE
  (current, `output/`) puts it on `StayEpisode loc="(1,3)[WORK]"` with a separate
  `labels="[WORK]"`; AMAL (older, `data/ground_truth/`) puts it on both `MoveEpisode` and
  `StayEpisode` via `activity="WORK"`. `StayEpisode.loc` is the only field both populate
  identically.
- `--warm-start-file` (e.g. `data/survey/finalState.json`) starts calibration from the
  synthesis end state instead of cold. The file is a flat JSON list indexed `0..n-1`,
  **positionally aligned with `upbd.csv` row order**. Alignment breaks if a person is
  **skipped at load** — no legs in `mddr.csv`, or a failed validation — since every later
  agent then shifts by one. Check the log line `Loaded N of M persons.`: if N < M, the
  warm start is misaligned.
- Remote runs: `./run_disaggregate_ws.sh {build|push|run|status|logs|clean}` builds the
  image, ships it to the workstation named by `MACHINE`, and runs it via
  `docker-compose.yml`. Override the argument file with `ARGS=other-args.txt`.

