import numpy as np
import pandas as pd
from pathlib import Path
from matplotlib import pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from plotting.common_parameters import scatterplot_size, format_axis, legend_fontsize
from plotting_aggregate.plot_utils import get_measurement_data

scatter_kwds = dict(fmt='o', markersize=6, capsize=3)


def plot_locations(home_target_dfs,
                   validation_file,
                   burn_in_iteration,
                   output_file):
    """
    Parameters
    ----------
    home_target_dfs : dict[str, pd.DataFrame]
        {group_name: homeTarget log DataFrame}, extracted by the caller.
    validation_file : Path
        CSV (sep=';') with groups as row index and zones as columns.
    burn_in_iteration : int
    output_file : Path
    """

    ####################### Step 1: Read validation matrix #######################
    df_validation = pd.read_csv(validation_file, sep=';', header=0, index_col=0)
    df_validation = df_validation.apply(pd.to_numeric, errors='coerce').fillna(0.0)

    ####################### Step 2: Merge all groups #######################
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

    ####################### Step 3: Plot #######################
    _scatter_plot(df_plot, output_file)


def _scatter_plot(df_plot, output_file):
    fig, ax = plt.subplots(figsize=scatterplot_size)

    ### Validation (background)
    ax.errorbar(
        df_plot['target validation'].values,
        df_plot['sample'].values,
        color='coral', alpha=0.6, label='Validation',
        **scatter_kwds)

    ### Calibration (foreground)
    ax.errorbar(
        df_plot['target measurement'].values,
        df_plot['sample'].values,
        markerfacecolor='none', markeredgecolor='steelblue',
        color='none', ecolor='gray', label='Calibration',
        **scatter_kwds)

    ax.legend(fontsize=legend_fontsize)
    format_axis(ax, 'Ground truth', 'Calibrated', equal_lims=True)

    ####################### Step 4: Output #######################
    save_dir = Path(output_file).parent
    save_dir.mkdir(parents=True, exist_ok=True)
    with PdfPages(output_file) as pdf:
        pdf.savefig(fig)
    plt.close(fig)