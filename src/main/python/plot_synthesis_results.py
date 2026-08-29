
from pathlib import Path
from .plotting.merge_states_xml import main as merge_states_xml_main 
from .plotting.merge_population_csv import main as merge_population_csv_main
from .plotting.plot_preference_matching import main as plot_preference_matching_main 
from .plotting.plot_time_structure_histogram import main as plot_time_structure_histogram_main

if __name__ == "__main__":
    results_dir = Path("data/validation_data")
    save_dir = results_dir / "plots"
    merged_dir = results_dir / "partitions_merged"
    merged_states_xml = merged_dir / "final_states_merged.xml"
    merged_population = merged_dir / "population_merged.tsv"

    population_filename = "population.tsv"
    target_filename = "population_merged.tsv"
    convergence_stats_filename = "convergenceStats.tsv"
    window = 10 

    merge_states_xml_main(
        results_dir = results_dir, 
        population_filename = population_filename,
        convergence_stats_filename = convergence_stats_filename,
        window = window
    )

    merge_population_csv_main(
        population_filename=population_filename,
        target_filename=target_filename
    )

    plot_preference_matching_main(
        results_dir=results_dir,
        save_dir=save_dir,
        states_xml=merged_states_xml,
        population_csv=merged_population,
        window = window
        )

    plot_time_structure_histogram_main(
        states_xml=merged_states_xml,
        save_dir=save_dir
    )

    print("Done with plotting synthesis!..")



