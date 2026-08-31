import gzip
import re
from typing import List
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET
import numpy as np
import pandas as pd
from matplotlib.backends.backend_pdf import PdfPages
from plotting.common_parameters import *



def plot_nStops_histogram(location_counts_file,
                          measurement_states_file,
                          validation_states_file,
                          burn_in_iteration,
                          output_file
                          ):

    ####################### Step 1: Prepare data #######################

    df_locationCounts = pd.read_csv(location_counts_file, header=None, sep="\t")
    measurement_nStops_abs = np.array(count_nStops(measurement_states_file))
    validation_nStops_abs = np.array(count_nStops(validation_states_file))

    last_n = len(df_locationCounts) - burn_in_iteration
    sample_abs = df_locationCounts.tail(last_n).mean(axis=0).to_numpy()
    max_len = len(sample_abs)
    measurement_nStops_abs = np.pad(measurement_nStops_abs,
                                    (0, max(0, max_len - len(measurement_nStops_abs))),
                                    'constant')[:max_len]
    validation_nStops_abs = np.pad(validation_nStops_abs,
                                   (0, max(0, max_len - len(validation_nStops_abs))),
                                   'constant')[:max_len]

    # upper bound of 10 on the x-axis
    sample_abs = sample_abs[:11]
    measurement_nStops_abs = measurement_nStops_abs[:11]
    validation_nStops_abs = validation_nStops_abs[:11]


    ####################### Step 2: Plot nStops histogram #######################
    x = np.arange(len(sample_abs))
    labels = [str(i) for i in x]

    fig, ax = plt.subplots(figsize=barplot_size)

    ax.bar(x - bar_width, measurement_nStops_abs, width=bar_width,
           color=MEASUREMENT_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
           label="Measurement Data Set")

    ax.bar(x, validation_nStops_abs, width=bar_width,
           color=VALIDATION_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
           label="Validation Data Set")

    ax.bar(x + bar_width, sample_abs, width=bar_width,
           color=ESTIMATION_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
           label="Calibration")

    format_categorical_axis(ax,
                            xlabel="Number of Stops",
                            ylabel="Frequency",
                            xticks=x,
                            xticklabels=labels)
    sort_legend_entries(ax)


    ####################### Step 3: Output #######################
    save_dir = Path(output_file).parent
    save_dir.mkdir(parents=True, exist_ok=True)
    with PdfPages(output_file) as pdf:
        pdf.savefig(fig, pad_inches=savefig_pad)
    plt.close(fig)


def count_nStops(xml_path: str) -> List[int]:
    # Parse XML (supports .gz)
    if str(xml_path).endswith(".gz"):
        with gzip.open(xml_path, "rt") as f:
            tree = ET.parse(f)
    else:
        tree = ET.parse(xml_path)
    root = tree.getroot()

    # Use the last MultiRoundTrip
    multi_round_trips = root.findall(".//MultiRoundTrip")
    if not multi_round_trips:
        return []
    last_trip = multi_round_trips[-1]

    hist = defaultdict(int)
    pattern_node = re.compile(r"\([^)]*\)\[[^\]]*\]")

    for round_trip in last_trip.findall(".//RoundTrip"):
        state_elem = round_trip.find("State")
        if state_elem is None:
            node_count = 0
        else:
            state_text = "".join(state_elem.itertext()).strip()
            start = state_text.find("nodes[")
            node_count = 0
            if start != -1:
                after = state_text[start + len("nodes["):]
                end_bins = after.find("],bins")
                end_bracket = after.find("]")
                if end_bins != -1:
                    nodes_blob = after[:end_bins]
                elif end_bracket != -1:
                    nodes_blob = after[:end_bracket]
                else:
                    nodes_blob = ""
                node_count = len(pattern_node.findall(nodes_blob))

        hist[node_count] += 1

    max_nodes = max(hist.keys(), default=0)
    counts = [0] * (max_nodes + 1)
    for k, v in hist.items():
        counts[k] = v
    return counts