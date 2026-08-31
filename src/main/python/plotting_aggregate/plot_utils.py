import math

import numpy as np
import pandas as pd



def determine_burn_in(convergence_stats_file):
    df_convergence_stats = pd.read_csv(convergence_stats_file, sep="\t")
    last_row = df_convergence_stats.sort_values('iteration').iloc[-1]
    msg_interval = 5000
    burn_in_iteration = math.ceil(last_row['burnInIteration'] / msg_interval)
    return burn_in_iteration



def get_measurement_data(df_or_series, burn_in_iteration):
    """Computes mean, std, and extracts target/sample values aligned by name."""


    last_n = len(df_or_series) - burn_in_iteration
    # print(last_n)
    df_last = df_or_series.iloc[-last_n:]
    avg = df_last.mean()
    std = df_last.std()

    target_cols = [c for c in avg.index if c.endswith("_target")]
    pair_names = [c[:-7] for c in target_cols]  # strip '_target'

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


def get_validation_data(file_path, pair_names, parse_dur_dep=False):
    """Reads a validation matrix, flattens it, and aligns values to pair_names."""
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
        if parse_dur_dep:
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