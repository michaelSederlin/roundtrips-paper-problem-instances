import os
from pathlib import Path
import pandas as pd
from plotting_aggregate.plot_utils import determine_burn_in, plot_locations, plot_nStops_histogram, plot_od, plot_dd


### Directory
current_file = Path(__file__).resolve()
BASE = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/"



### burn-in
convergence_stats_file = BASE / "calibration/2026 08 28/convergenceStats.tsv"
burn_in_iteration = determine_burn_in(convergence_stats_file)



### nstops
log_file = BASE / "calibration/2026 08 28/locationCounts.log"
measurement_file = BASE / "measurement/_final_states_merged.xml"
validation_file = BASE / "validation/_final_states_merged.xml"
output_file = BASE / "calibration/2026 08 28/01_nStops_histogram.pdf"

plot_nStops_histogram(log_file, measurement_file, validation_file, burn_in_iteration, output_file)



### od
target_file = BASE / "calibration/2026 08 28/odTarget.csv"
validation_file = BASE / "validation/25_od.csv"
output_file = BASE / "calibration/2026 08 28/02_od.pdf"

plot_od(target_file, validation_file, burn_in_iteration, output_file)



### home locations
targets_path = BASE / "calibration/2026 08 28/"
target_files = [f for f in os.listdir(targets_path) if os.path.isfile(os.path.join(targets_path, f))]
target_dfs = {}
for file in target_files:
    if "homeTarget" in file:
        df = pd.read_csv(targets_path / file, sep="\t")
        group_name = file[:-4].split('_')[-1]
        target_dfs[group_name] = df
validation_file = BASE / "validation/25_homeLocations.csv"
output_file = BASE / "calibration/2026 08 28/03_homeLocation.pdf"

plot_locations(target_dfs, validation_file, burn_in_iteration, output_file)



### work locations
targets_path = BASE / "calibration/2026 08 28/"
target_files = [f for f in os.listdir(targets_path) if os.path.isfile(os.path.join(targets_path, f))]
target_dfs = {}
for file in target_files:
    if "workTarget" in file:
        df = pd.read_csv(targets_path / file, sep="\t")
        group_name = file[:-4].split('_')[-1]
        target_dfs[group_name] = df

validation_file = BASE / "validation/25_workLocations_all.csv"
output_file = BASE / "calibration/2026 08 28/03_workLocation.pdf"

plot_locations(target_dfs, validation_file, burn_in_iteration, output_file)



### dd home
target_file = BASE / "calibration/2026 08 28/ddHomeTarget.csv"
validation_file = BASE / "validation/25_ddHome.csv"
output_file = BASE / "calibration/2026 08 28/04_ddHome.pdf"

plot_dd(target_file, validation_file, burn_in_iteration, output_file)




### dd work
target_file = BASE / "calibration/2026 08 28/ddWorkTarget.csv"
validation_file = BASE / "validation/25_ddWork.csv"
output_file = BASE / "calibration/2026 08 28/04_ddWork.pdf"

plot_dd(target_file, validation_file, burn_in_iteration, output_file)