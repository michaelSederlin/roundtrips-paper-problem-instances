import os
from pathlib import Path
import pandas as pd
from plot_results_aggregated_utils import determine_burn_in, plot_locations, plot_nStops_histogram, plot_matrix, plot_departures_histogram


if __name__ == "__main__":


    ### directory
    current_file = Path(__file__).resolve()
    BASE_IN = current_file.parent.parent.parent.parent / "data/aggregated_calibration/"
    BASE_OUT = current_file.parent.parent.parent.parent / "data/aggregated_calibration/plots/"



    ### burn-in
    convergence_stats_file = BASE_IN / "calibration/2026 08 28/convergenceStats.tsv"
    burn_in_iteration = determine_burn_in(convergence_stats_file)



    ### nstops
    log_file = BASE_IN / "calibration/2026 08 28/locationCounts.log"
    measurement_file = BASE_IN / "measurement/_final_states_merged.xml"
    validation_file = BASE_IN / "validation/_final_states_merged.xml"
    output_file = BASE_OUT / "nStops_histogram.pdf"

    plot_nStops_histogram(log_file, measurement_file, validation_file, burn_in_iteration, output_file)



    ### od
    target_file = BASE_IN / "calibration/2026 08 28/odTarget.csv"
    validation_file = BASE_IN / "validation/25_od.csv"
    output_file = BASE_OUT / "od_target.pdf"

    plot_matrix(target_file, validation_file, burn_in_iteration, output_file, False)



    ### home locations
    targets_path = BASE_IN / "calibration/2026 08 28/"
    target_files = [f for f in os.listdir(targets_path) if os.path.isfile(os.path.join(targets_path, f))]
    target_dfs = {}
    for file in target_files:
        if "homeTarget" in file:
            df = pd.read_csv(targets_path / file, sep="\t")
            group_name = file[:-4].split('_')[-1]
            target_dfs[group_name] = df
    validation_file = BASE_IN / "validation/25_homeLocations.csv"
    output_file = BASE_OUT / "home_location_target.pdf"

    plot_locations(target_dfs, validation_file, burn_in_iteration, output_file)



    ### work locations
    targets_path = BASE_IN / "calibration/2026 08 28/"
    target_files = [f for f in os.listdir(targets_path) if os.path.isfile(os.path.join(targets_path, f))]
    target_dfs = {}
    for file in target_files:
        if "workTarget" in file:
            df = pd.read_csv(targets_path / file, sep="\t")
            group_name = file[:-4].split('_')[-1]
            target_dfs[group_name] = df

    validation_file = BASE_IN / "validation/25_workLocations_all.csv"
    output_file = BASE_OUT / "work_location_target.pdf"

    plot_locations(target_dfs, validation_file, burn_in_iteration, output_file)



    ### dd home
    target_file = BASE_IN / "calibration/2026 08 28/ddHomeTarget.csv"
    validation_file = BASE_IN / "validation/25_ddHome.csv"
    output_file = BASE_OUT / "dd_home_target.pdf"

    plot_matrix(target_file, validation_file, burn_in_iteration, output_file, True)



    ### dd work
    target_file = BASE_IN / "calibration/2026 08 28/ddWorkTarget.csv"
    validation_file = BASE_IN / "validation/25_ddWork.csv"
    output_file = BASE_OUT / "dd_work_target.pdf"

    plot_matrix(target_file, validation_file, burn_in_iteration, output_file, True)



    ### departures histogram
    log_file = BASE_IN / "calibration/2026 08 28/departures.log"
    measurement_file = BASE_IN / "measurement/_final_states_merged.xml"
    validation_file = BASE_IN / "validation/_final_states_merged.xml"
    output_file = BASE_OUT / "departures_histogram.pdf"

    plot_departures_histogram(log_file, measurement_file, validation_file, burn_in_iteration,output_file)