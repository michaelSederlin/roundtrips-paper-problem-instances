
from matplotlib import pyplot as plt

figure_width = 6
barplot_size = (figure_width, 5)
scatterplot_size = (figure_width, figure_width)

axes_rect = dict(left = 0.2, right = 0.97, bottom = 0.15, top = 0.97)
savefig_pad = 0.02

label_fontsize = 15
ticklabel_fontsize = 13
legend_fontsize = 13

# Shared bar-plot styling (categorical comparison plots)
ESTIMATION_COLOR = "steelblue"
MEASUREMENT_COLOR = "mediumseagreen"
VALIDATION_COLOR = "coral"

bar_width = 0.26
bar_alpha = 0.75
bar_edgecolor = "black"

LEGEND_ORDER = ['Measurement', 'Validation', 'Calibration']

def format_axis(ax: plt.Axes, xlabel: str, ylabel: str, equal_lims: bool = False):
    xlabel, ylabel = xlabel.capitalize(), ylabel.capitalize()
    ax.set_ylabel(ylabel, fontsize=label_fontsize)
    ax.set_xlabel(xlabel, fontsize=label_fontsize)
    if equal_lims: 
        l = max([ax.get_xlim()[1], ax.get_ylim()[1]])
        ax.set_aspect('equal')
    else: 
        l = None
    ax.set_xlim(left=0, right = l)
    ax.set_ylim(bottom=0, top = l)
    ax.grid(True)
    ax.tick_params(axis='x', labelsize=ticklabel_fontsize)
    ax.tick_params(axis='y', labelsize=ticklabel_fontsize)
    ax.get_figure().subplots_adjust(**axes_rect)
    return ax


def format_categorical_axis(ax, xlabel: str, ylabel: str, xticks=None, xticklabels=None):
    """Axis formatting for categorical (bar) plots: same fonts as `format_axis`,
    but keeping the categorical x axis and the horizontal-only grid."""
    ax.set_xlabel(xlabel, fontsize=label_fontsize)
    ax.set_ylabel(ylabel, fontsize=label_fontsize)
    if xticks is not None:
        ax.set_xticks(xticks)
    if xticklabels is not None:
        ax.set_xticklabels(xticklabels)
    ax.set_ylim(bottom=0)
    ax.grid(axis="y", linestyle="--", alpha=0.5)
    ax.tick_params(axis='x', labelsize=ticklabel_fontsize)
    ax.tick_params(axis='y', labelsize=ticklabel_fontsize)
    ax.get_figure().subplots_adjust(**axes_rect)
    return ax


def sort_legend_entries(ax, **kwargs):
    """Legend with the shared dataset ordering and font size."""
    handles, labels = ax.get_legend_handles_labels()
    ordered_handles = [handles[labels.index(label)] for label in LEGEND_ORDER if label in labels]
    ordered_labels = [label for label in LEGEND_ORDER if label in labels]
    ax.legend(ordered_handles, ordered_labels, fontsize=legend_fontsize, **kwargs)
    return ax
