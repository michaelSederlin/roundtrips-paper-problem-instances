import pandas as pd
import argparse
from pathlib import Path

from plot_results_aggregated_utils import (
    determine_burn_in, plot_locations, plot_nStops_histogram,
    plot_matrix, plot_departures_histogram, join_pdfs
)

############  Helper Methods ############

def load_target_dfs(directory: Path, keyword: str) -> dict[str, pd.DataFrame]:
    dfs = {}
    for path in sorted(p for p in directory.iterdir() if p.is_file()):
        if keyword in path.name:
            group_name = path.stem.split('_')[-1]
            dfs[group_name] = pd.read_csv(path, sep="\t")
    if not dfs:
        raise FileNotFoundError(f"Keine '{keyword}'-Dateien in {directory}")
    return dfs


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generates the evaluation plots for the aggregated calibration."
    )
    parser.add_argument("--calibration", "-c", type=Path, required=True,
                        help="Directory containing the calibration outputs")
    parser.add_argument("--measurement", "-m", type=Path, required=True,
                        help="Directory containing the measurement data")
    parser.add_argument("--validation", "-v", type=Path, required=True,
                        help="Directory containing the validation data")
    parser.add_argument("--out", "-o", type=Path, required=True,
                        help="Output directory for the PDFs")
    parser.add_argument("--join", "-j", action="store_true",
                        help="Merge all generated PDFs into all_plots.pdf")
    return parser.parse_args()




############ Main Method ############

def main():

    ### burn-in
    convergence_stats_file = CALIBRATION_IN / "convergenceStats.tsv"
    if convergence_stats_file.is_file():
        burn_in_iteration = determine_burn_in(convergence_stats_file, 5000)
    else:
        df_locationCounts = pd.read_csv(CALIBRATION_IN / "locationCounts.log", header=None, sep="\t")
        burn_in_iteration = len(df_locationCounts) - 1


    ### nStops
    plot_nStops_histogram(
        CALIBRATION_IN / "locationCounts.log",
        measurement_states_file,
        validation_states_file,
        burn_in_iteration,
        BASE_OUT / "01_nStops_histogram.pdf",
    )


    ### od
    plot_matrix(
        CALIBRATION_IN / "odTarget.csv",
        VALIDATION_IN / "25_od.csv",
        burn_in_iteration,
        BASE_OUT / "02_od_target.pdf",
        False,
    )


    ### home locations
    plot_locations(
        load_target_dfs(CALIBRATION_IN, "homeTarget"),
        VALIDATION_IN / "25_homeLocations.csv",
        burn_in_iteration,
        BASE_OUT / "03_home_location_target.pdf",
    )


    ### work locations
    plot_locations(
        load_target_dfs(CALIBRATION_IN, "workTarget"),
        VALIDATION_IN / "25_workLocations.csv",
        burn_in_iteration,
        BASE_OUT / "04_work_location_target.pdf",
    )


    ### dd home
    plot_matrix(
        CALIBRATION_IN / "ddHomeTarget.csv",
        VALIDATION_IN / "25_ddHome.csv",
        burn_in_iteration,
        BASE_OUT / "05_dd_home_target.pdf",
        True,
    )


    ### dd work
    plot_matrix(
        CALIBRATION_IN / "ddWorkTarget.csv",
        VALIDATION_IN / "25_ddWork.csv",
        burn_in_iteration,
        BASE_OUT / "06_dd_work_target.pdf",
        True,
    )


    ### departures histogram
    plot_departures_histogram(
        CALIBRATION_IN / "departures.log",
        measurement_states_file,
        validation_states_file,
        burn_in_iteration,
        BASE_OUT / "07_departures_histogram.pdf",
    )

    if JOIN:
        join_pdfs(BASE_OUT)


############ Run Main Method ############

if __name__ == "__main__":

    ### Args hard coded
    # ROOT = Path(__file__).resolve().parent.parent.parent.parent
    # BASE_IN = ROOT / "data/aggregated_calibration/"
    # BASE_OUT = ROOT / "data/aggregated_calibration/plots/"
    # CALIBRATION_IN = BASE_IN / "calibration" / "2026 08 28"
    # MEASUREMENT_IN = BASE_IN / "measurement"
    # VALIDATION_IN  = BASE_IN / "validation"
    # JOIN = True

    # measurement_states_file = MEASUREMENT_IN / "_final_states_merged.xml"
    # validation_states_file  = VALIDATION_IN  / "_final_states_merged.xml"

    ### Args hard coded
    # BASE_IN = Path("./input/")
    # BASE_OUT = Path("./plots/")
    # CALIBRATION_IN = BASE_IN / "calibration"
    # MEASUREMENT_IN = BASE_IN / "measurement"
    # VALIDATION_IN  = BASE_IN / "validation"
    # JOIN = True

    # measurement_states_file = MEASUREMENT_IN / "_final_states_merged.xml"
    # validation_states_file  = VALIDATION_IN  / "_final_states_merged.xml"

    ### Args from command line
    args = parse_args()
    CALIBRATION_IN = args.calibration.expanduser().resolve()
    MEASUREMENT_IN = args.measurement.expanduser().resolve()
    VALIDATION_IN  = args.validation.expanduser().resolve()
    BASE_OUT       = args.out.expanduser().resolve()
    JOIN           = args.join

    measurement_states_file = MEASUREMENT_IN / "_final_states_merged.xml"
    validation_states_file  = VALIDATION_IN  / "_final_states_merged.xml"

    main()