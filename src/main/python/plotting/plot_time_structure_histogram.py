from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages

from .plotting_utilities import departure_hours, parse_states_xml
from .common_parameters import barplot_size


def main(states_xml: Path, save_dir: Path, save: bool = True, show: bool = False,
         time_bin_size_h: float = 1.0):

    ####################### Step 1: Count bins in final MultiRoundTrip #######################
    root = parse_states_xml(states_xml).getroot()

    multi_round_trips = root.findall(".//MultiRoundTrip")
    last_trip = multi_round_trips[-1]

    bin_counts = {i: 0 for i in range(24)}

    for round_trip in last_trip.findall(".//RoundTrip"):
        for hour in departure_hours(round_trip, time_bin_size_h):
            bin_counts[hour] += 1

    df_bins = pd.DataFrame([bin_counts])
    df_bins = df_bins[[i for i in range(24)]]

    ####################### Step 2: Plot time structure #######################
    fig, ax = plt.subplots(figsize=barplot_size)
    ax.bar(df_bins.columns, df_bins.iloc[0])
    ax.set_xlabel("Bin")
    ax.set_ylabel("Frequency")
    ax.set_title("Departures per time bin")
    ax.grid(axis='y', linestyle='--', alpha=0.7)
    ax.set_xticks(range(24))
    plt.tight_layout()

    ####################### Step 3: Output #######################
    if save:
        save_dir = Path(save_dir)
        save_dir.mkdir(parents=True, exist_ok=True)
        with PdfPages(save_dir / "time_structure_histogram.pdf") as pdf:
            pdf.savefig(fig)

    if show:
        plt.show()

    plt.close(fig)


if __name__ == "__main__":
    from .argument_parser import ArgumentParser
    parser = ArgumentParser()
    args = parser.parse()

    states_xml = args.states_xml
    if states_xml is None:
        if args.results_dir is None:
            parser.parser.error("either --states-xml or --results-dir is required")
        states_xml = args.results_dir / "partitions_merged" / "final_states_merged.xml"

    main(states_xml=states_xml,
         save_dir=args.save_dir,
         save=args.save,
         show=args.show,
         time_bin_size_h=args.time_bin_size_h)
