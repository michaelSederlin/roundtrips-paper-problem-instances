"""Driver for the plotting scripts: merges the partition calibration of each finished
scenario run and then produces the reproduction plots for it."""

from pathlib import Path
from plotting.merge_population_csv import main as population_merge_main
from plotting.merge_states_xml import main as states_merge_main
from plotting.plot_population_reproduction import main as population_reproduction_main
from plotting.plot_od_and_transitions import main as plot_od_main
from plotting.plot_age_bin_activity_durations import main as age_bins_main

DATA_DIR = Path("data/survey")

MEASUREMENT_STATES_XML = Path('data/measurement_data/partitions_merged/_final_states_merged.xml.gz')
VALIDATION_STATES_XML = Path('data/validation_data/partitions_merged/_final_states_merged.xml.gz')

POPULATION_FILENAME = 'population.tsv'
TARGET_FILENAME = 'population_merged.tsv'
CONVERGENCE_STATS_FILENAME = 'convergenceStats.tsv'
WINDOW = 1
TIME_BIN_SIZE_H = 1
HORIZON = 24
MSG_INTERVAL = 10000
SAVE = True
SHOW = False
OUTPUT_SUFFIX = '.pdf'

def plot_scenario(scenario):
    results_dir = Path(scenario)
    if not results_dir.exists():
        print("Results dir doesn't exist")
        raise FileNotFoundError("Results directory doesn't exist!")
    
    save_dir = results_dir / 'test_plots'
    save_dir.mkdir(parents=True, exist_ok=True)

    population_merge_main(results_dir=results_dir,
                          population_filename=POPULATION_FILENAME,
                          target_filename=TARGET_FILENAME)
    
    states_merge_main(results_dir=results_dir,
                      population_filename=POPULATION_FILENAME,
                      convergence_stats_filename=CONVERGENCE_STATS_FILENAME,
                      window=WINDOW)


    population_reproduction_main(results_dir=results_dir,
                                 measurement_states_xml=MEASUREMENT_STATES_XML,
                                 data_dir=DATA_DIR,
                                 save_dir=save_dir,
                                 validation_states_xml=VALIDATION_STATES_XML,
                                 convergence_stats_filename=CONVERGENCE_STATS_FILENAME,
                                 window=WINDOW,
                                 time_bin_size_h=TIME_BIN_SIZE_H,
                                 horizon=HORIZON,
                                 save=SAVE,
                                 show=SHOW,
                                 output_suffix=OUTPUT_SUFFIX)

    plot_od_main(results_dir=results_dir,
                 measurement_states_xml=MEASUREMENT_STATES_XML,
                 save_dir=save_dir,
                 validation_states_xml=VALIDATION_STATES_XML,
                 convergence_stats_filename=CONVERGENCE_STATS_FILENAME,
                 msg_interval=MSG_INTERVAL,
                 save=SAVE,
                 show=SHOW,
                 output_suffix=OUTPUT_SUFFIX)

    age_bins_main(results_dir=results_dir,
                  measurement_states_xml=MEASUREMENT_STATES_XML,
                  save_dir=save_dir,
                  validation_states_xml=VALIDATION_STATES_XML,
                  save=SAVE,
                  show=SHOW,
                  output_suffix=OUTPUT_SUFFIX)


if __name__ == "__main__":
    # scenario = Path("calibration/disaggregate-calibration")
    scenario = Path("output/new-results")
    plot_scenario(scenario)
    print("SCRIPT FINISHED")

