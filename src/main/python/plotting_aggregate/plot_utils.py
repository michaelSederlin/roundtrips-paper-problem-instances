import gzip
import math
import re
from collections import defaultdict
from pathlib import Path
import numpy as np
import pandas as pd
from matplotlib import pyplot as plt
import xml.etree.ElementTree as ET




from matplotlib.backends.backend_pdf import PdfPages
from plotting.common_parameters import scatterplot_size, VALIDATION_COLOR, ESTIMATION_COLOR, format_axis, \
    sort_legend_entries, legend_fontsize, barplot_size, MEASUREMENT_COLOR, bar_width, bar_alpha, bar_edgecolor, \
    format_categorical_axis, savefig_pad


scatter_kwds = dict(fmt='o', markersize=6, capsize=3)



def plot_nStops_histogram(location_counts_file,measurement_states_file,validation_states_file,burn_in_iteration, output_file):

    # Prepare data
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


    # Plot nStops histogram
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


    # Output
    save_dir = Path(output_file).parent
    save_dir.mkdir(parents=True, exist_ok=True)
    with PdfPages(output_file) as pdf:
        pdf.savefig(fig, pad_inches=savefig_pad)
    plt.close(fig)




def plot_od(odTarget_file, od_validation_file, burn_in_iteration, output_file):

    # Ensure path types are standard Path objects
    odTarget_file = Path(odTarget_file)
    od_validation_file = Path(od_validation_file)

    df_odtarget = pd.read_csv(odTarget_file, sep="\t")

    (pair_names, sample_values, target_values_measurement, std_target_values_measurement,
     std_sample_values) = get_measurement_data(df_odtarget, burn_in_iteration)

    target_values_validation = get_validation_data(od_validation_file, pair_names, ddTarget=False    )

    df_od_plot = pd.DataFrame(
        {
            "sample": sample_values,
            "target measurement": target_values_measurement,
            "target validation": target_values_validation,
        },
        index=pair_names,
    )

    # Plot
    create_scatter_plot(df_od_plot, output_file)



def plot_locations(home_target_dfs,validation_file,burn_in_iteration,output_file):

    # Read validation matrix
    df_validation = pd.read_csv(validation_file, sep=';', header=0, index_col=0)
    df_validation = df_validation.apply(pd.to_numeric, errors='coerce').fillna(0.0)

    # Merge all groups
    all_samples, all_meas, all_val, all_pairs = [], [], [], []

    for group_name, df_group in home_target_dfs.items():
        (pair_names, sample_values, target_meas,
         _, _) = get_measurement_data(df_group, burn_in_iteration)

        target_val = np.array([
            float(df_validation.loc[group_name, p])
            if group_name in df_validation.index and p in df_validation.columns
            else 0.0
            for p in pair_names
        ], dtype=float)

        all_samples.extend(sample_values)
        all_meas.extend(target_meas)
        all_val.extend(target_val)
        all_pairs.extend(f"{group_name}_{p}" for p in pair_names)

    df_plot = pd.DataFrame({
        "sample": all_samples,
        "target measurement": all_meas,
        "target validation": all_val,
    }, index=all_pairs)

    # Plot
    create_scatter_plot(df_plot, output_file)




def plot_dd(ddTarget_file, dd_validation_file, burn_in_iteration, output_file):

    # # Ensure path types are standard Path objects
    # odTarget_file = Path(odTarget_file)
    # od_validation_file = Path(od_validation_file)

    df_odtarget = pd.read_csv(ddTarget_file, sep="\t")

    (pair_names,
     sample_values,
     target_values_measurement,
     std_target_values_measurement,
     std_sample_values,
     ) = get_measurement_data(df_odtarget, burn_in_iteration)

    target_values_validation = get_validation_data(
        dd_validation_file, pair_names, ddTarget=True
    )

    df_od_plot = pd.DataFrame(
        {
            "sample": sample_values,
            "target measurement": target_values_measurement,
            "target validation": target_values_validation,
        },
        index=pair_names,
    )

    # Plot
    create_scatter_plot(df_od_plot, output_file)




############################ Helper ############################

def count_nStops(xml_path):
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


def determine_burn_in(convergence_stats_file):
    df_convergence_stats = pd.read_csv(convergence_stats_file, sep="\t")
    last_row = df_convergence_stats.sort_values('iteration').iloc[-1]
    msg_interval = 5000
    burn_in_iteration = math.ceil(last_row['burnInIteration'] / msg_interval)
    return burn_in_iteration



def get_measurement_data(df_or_series, burn_in_iteration):

    last_n = len(df_or_series) - burn_in_iteration
    # print(last_n)
    df_last = df_or_series.iloc[-last_n:]
    avg = df_last.mean()
    std = df_last.std()

    target_cols = [c for c in avg.index if c.endswith("_target")]
    pair_names = [c[:-7] for c in target_cols]

    sample_map = {c[:-7]: avg[c] for c in avg.index if c.endswith("_sample")}
    std_sample_map = {c[:-7]: std[c] for c in std.index if c.endswith("_sample")}

    target_values_measurement = np.array([avg[c] for c in target_cols], dtype=float)
    sample_values = np.array([sample_map[p] for p in pair_names], dtype=float)

    std_target_values_measurement = np.array([std[c] for c in target_cols], dtype=float)
    std_sample_values = np.array([std_sample_map[p] for p in pair_names], dtype=float)

    return (
        pair_names,
        sample_values,
        target_values_measurement,
        std_target_values_measurement,
        std_sample_values,
    )




def get_validation_data(file_path, pair_names, ddTarget):

    df_validation_matrix = pd.read_csv(file_path, sep=";", header=0, index_col=0)
    df_validation_matrix = df_validation_matrix.apply(
        pd.to_numeric, errors="coerce"
    ).fillna(0.0)

    # Flatten validation matrix to match keys like "origin->destination"
    validation_matrix_dict = {}
    for origin in df_validation_matrix.index:
        for destination in df_validation_matrix.columns:
            validation_matrix_dict[f"{origin}->{destination}"] = float(
                df_validation_matrix.loc[origin, destination]
            )

    target_values_validation = []
    for p in pair_names:
        if ddTarget:
            # Match ['dur13', 'dep6'] and '13'-'6'
            parts = p.split("-")
            origin = parts[0].replace("dur", "")
            destination = parts[1].replace("dep", "")
            dict_key = f"{origin}->{destination}"
        else:
            # Direct match for od and location
            dict_key = p

        val = validation_matrix_dict.get(dict_key, 0.0)
        target_values_validation.append(val)

    return np.array(target_values_validation, dtype=float)




def create_scatter_plot(df_od_plot, output_file) :

    fig, ax = plt.subplots(figsize=scatterplot_size)

    # Validation (background layer)
    ax.errorbar(
        df_od_plot['target validation'].values,
        df_od_plot['sample'].values,
        color=VALIDATION_COLOR,
        alpha=0.6,
        label='Validation',
        **scatter_kwds,
    )

    # Calibration (foreground layer)
    ax.errorbar(
        df_od_plot['target measurement'].values,
        df_od_plot['sample'].values,
        markerfacecolor='none',
        markeredgecolor=ESTIMATION_COLOR,
        color='none',
        ecolor='gray',
        label='Calibration',
        **scatter_kwds,
    )

    format_axis(ax, 'Ground truth', 'Calibrated', equal_lims=True)
    ax.legend(fontsize=legend_fontsize)

    # Output
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with PdfPages(output_path) as pdf:
        pdf.savefig(fig)
    plt.close(fig)