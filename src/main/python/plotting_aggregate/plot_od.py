import pandas as pd
from pathlib import Path
from matplotlib import pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages

from plotting.common_parameters import scatterplot_size, format_axis, legend_fontsize
from plotting_aggregate.plot_utils import get_measurement_data, get_validation_data

scatter_kwds = dict(fmt='o', markersize=6, capsize=3)


def plot_od(odTarget_file,
            od_validation_file,
            burn_in_iteration,
            output_file):

    df_odtarget = pd.read_csv(odTarget_file, sep="\t")

    (pair_names,
     sample_values,
     target_values_measurement,
     std_target_values_measurement,
     std_sample_values,
     ) = get_measurement_data(df_odtarget, burn_in_iteration)

    target_values_validation = get_validation_data(od_validation_file, pair_names, parse_dur_dep=False)

    df_od_plot = pd.DataFrame(
        {
            "sample": sample_values,
            "target measurement": target_values_measurement,
            "target validation": target_values_validation,
        },
        index=pair_names,
    )

    scatter_plotter_with_validation(df_od_plot, output_file)


def scatter_plotter_with_validation(df_od_plot, output_file):

    fig, ax = plt.subplots(figsize=scatterplot_size)

    ### Validation (background)
    ax.errorbar(
        df_od_plot['target validation'].values,
        df_od_plot['sample'].values,
        color='coral',
        alpha=0.6,
        label='Validation',
        **scatter_kwds,
    )

    ### Calibration (foreground)
    ax.errorbar(
        df_od_plot['target measurement'].values,
        df_od_plot['sample'].values,
        markerfacecolor='none',
        markeredgecolor='steelblue',
        color='none',
        ecolor='gray',
        label='Calibration',
        **scatter_kwds,
    )

    ax.legend(fontsize=legend_fontsize)
    format_axis(ax, 'Ground truth', 'Calibrated', equal_lims=True)

    save_dir = Path(output_file).parent
    save_dir.mkdir(parents=True, exist_ok=True)
    with PdfPages(output_file) as pdf:
        pdf.savefig(fig)
    plt.close(fig)