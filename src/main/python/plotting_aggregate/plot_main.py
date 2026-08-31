import os
from pathlib import Path

import pandas as pd

from plotting_aggregate.plot_locations import plot_locations
from plotting_aggregate.plot_nStops_histogram import plot_nStops_histogram
from plotting_aggregate.plot_od import plot_od
from plotting_aggregate.plot_utils import determine_burn_in

if __name__ == "__main__":

    current_file = Path(__file__).resolve()



    ### burn in
    convergence_stats_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/convergenceStats.tsv"
    burn_in_iteration = determine_burn_in(convergence_stats_file)



    ### nstops
    measurement_states_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/measurement/_final_states_merged.xml"
    validation_states_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/validation/_final_states_merged.xml"
    locationCounts_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/locationCounts.log"
    output_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/01_nStops_histogram.pdf"
    plot_nStops_histogram(locationCounts_file, measurement_states_file, validation_states_file, burn_in_iteration, output_file)



    ### od
    odTarget_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/odTarget.csv"
    od_validation_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/validation/25_od.csv"
    output_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/02_od.pdf"
    plot_od(odTarget_file, od_validation_file, burn_in_iteration, output_file)



    ### home locations
    homeLocationTargets_path = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/"
    homeLocationTargets_files = [f for f in os.listdir(homeLocationTargets_path) if os.path.isfile(os.path.join(homeLocationTargets_path, f))]
    home_target_dfs = {}
    for file in homeLocationTargets_files:
        if "homeTarget" in file:
            df = pd.read_csv(homeLocationTargets_path / file, sep="\t")
            group_name = file[:-4].split('_')[-1]
            home_target_dfs[group_name] = df

    homeLocations_validation_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/validation/25_homeLocations.csv"
    output_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/03_homeLocation.pdf"

    plot_locations(
        home_target_dfs,
        homeLocations_validation_file,
        burn_in_iteration,
        output_file,
    )

    ### work locations
    workLocationsTarget_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/workTarget_all.csv"
    work_target_dfs = {}
    df = pd.read_csv(workLocationsTarget_file, sep="\t")
    group_name = Path(workLocationsTarget_file).stem.split('_')[-1]  # "workTarget_all" → "all"
    work_target_dfs[group_name] = df

    workLocations_validation_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/validation/25_workLocations_all.csv"
    output_file = current_file.parent.parent.parent.parent.parent / "data/aggregated_calibration/calibration/2026 08 28/03_workLocation.pdf"

    plot_locations(
        work_target_dfs,
        workLocations_validation_file,
        burn_in_iteration,
        output_file,
    )

