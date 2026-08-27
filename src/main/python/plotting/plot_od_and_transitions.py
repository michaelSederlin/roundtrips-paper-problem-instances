from collections import Counter
import gzip
import math
from pathlib import Path
import re
from matplotlib import pyplot as plt
import numpy as np
import pandas as pd
from xml.etree import ElementTree as ET 

from .helpers import ArgumentParser
from .plotting_utilities import save_show

# Globals
fontsize = 10 
scatter_kwds = dict(fmt = 'o', markersize = 6, capsize = 3)
_node_pat = re.compile(r"\(([^)]*)\)\[[^\]]*\]")

# Helpers
def to_transition(mat):
    return mat.div(mat.sum(axis=1).replace(0, np.nan), axis=0).fillna(0)

def format_axis(ax, xlabel, ylabel):
    ax.set_ylabel(xlabel, fontsize=fontsize)
    ax.set_xlabel(ylabel, fontsize=fontsize)
    ax.set_xlim(left=0)
    ax.set_ylim(bottom=0)
    ax.grid(True)
    ax.tick_params(axis='x', labelsize=fontsize)
    ax.tick_params(axis='y', labelsize=fontsize)
    return ax 

def get_burn_in_iteration_from_convergence_statistics(fp: Path, msg_interval: int = 5000) -> int:
    df_convergence = pd.read_csv(fp, sep='\t')
    last_row = df_convergence.sort_values('iteration').iloc[-1]
    return math.ceil(last_row['burnInIteration'] / msg_interval)

def _nodes(rt):
    n = rt.find("Nodes")
    if n is not None and n.text:
        return _node_pat.findall(n.text)
    s = rt.find("State")
    if s is None:
        return []
    t = "".join(s.itertext())
    i = t.find("nodes[")
    if i == -1:
        return []
    blob = t[i + len("nodes["):]
    cut = blob.find("],bins")
    blob = blob[:cut] if cut != -1 else blob[:blob.find("]")]
    return _node_pat.findall(blob)

def _sample_od(trip) -> Counter:
    """OD leg counts for a single MultiRoundTrip sample (loop closed back to start)."""
    od = Counter()
    for rt in trip.findall(".//RoundTrip"):
        seq = _nodes(rt)
        if not seq:
            continue
        ring = seq + seq[:1]
        for o, d in zip(ring, ring[1:]):
            od[(o, d)] += 1
    return od

def _od_series_to_matrix(od: pd.Series) -> pd.DataFrame:
    """Long (origin, destination) -> value series into a square OD matrix."""
    locs = sorted({l for pair in od.index for l in pair},
                  key=lambda s: tuple(int(x) for x in re.findall(r"-?\d+", s)))
    mat = pd.DataFrame(0.0, index=locs, columns=locs)
    for (o, d), c in od.items():
        mat.at[o, d] = c
    return mat

def extract_od_matrix(states_tree: ET.ElementTree) -> pd.DataFrame:
    """OD matrix (origin rows, destination cols) from the last sample of a states file.
    Legs are consecutive node coordinates, closing the loop back to the start."""
    trip = states_tree.getroot().findall(".//MultiRoundTrip")[-1]
    od = pd.Series(_sample_od(trip), dtype=float)
    return _od_series_to_matrix(od)

def extract_estimated_od_matrix(estimation_dir: Path, convergence_filename: str,
                                msg_interval: int) -> pd.DataFrame:
    """Average OD flows across post-burn-in samples within each partition,
    then sum the partition matrices (disjoint trips) into the full OD matrix."""
    
    partition_files = [p for p in estimation_dir.glob("**/states.xml")
                       if p.parent.name.isdigit()]
    if not partition_files:
        raise ValueError(f"No partition states.xml found under {estimation_dir}")

    partition_avgs = []
    for states_file in partition_files:
        try:
            conv_file = states_file.parent / convergence_filename
            burn_in_index = get_burn_in_iteration_from_convergence_statistics(conv_file, msg_interval)
            samples = ET.parse(states_file).getroot().findall(".//MultiRoundTrip")[burn_in_index:]
            if not samples:
                raise ValueError(f"No samples left after burn-in ({burn_in_index}) for {states_file.parent.name}")
            part_avg = pd.concat([pd.Series(_sample_od(t), dtype=float) for t in samples],
                                axis=1).fillna(0).mean(axis=1)
            partition_avgs.append(part_avg)

        except Exception as e:
            print(f"{states_file} threw error: {e}")

    total = pd.concat(partition_avgs, axis=1).fillna(0).sum(axis=1)
    return _od_series_to_matrix(total)

def load_tree(fp: Path) -> ET.ElementTree: 
    if fp.suffix == '.gz':
        return ET.parse(gzip.open(fp))
    else:
        return ET.parse(fp)
    
def main(results_dir: Path,
         measurement_states_xml: Path,
         save_dir: Path = None,
         validation_states_xml: Path = None,
         convergence_stats_filename: str = "convergenceStats.tsv",
         msg_interval: int = 5000,
         save: bool = True,
         show: bool = False,
         output_suffix: str = ".pdf"):
    if results_dir is None:
        raise ValueError("results_dir is required")
    if measurement_states_xml is None:
        raise ValueError("measurement_states_xml is required")

    results_dir = Path(results_dir)
    if not results_dir.exists():
        raise FileNotFoundError(f"Results directory not found: {results_dir}")

    save_dir = results_dir if save_dir is None else Path(save_dir)
    save_kwds = dict(save=save, show=show, output_suffix=output_suffix)

    od_meas = extract_od_matrix(load_tree(measurement_states_xml))
    od_val = None if validation_states_xml is None else extract_od_matrix(load_tree(validation_states_xml))
    od_est = extract_estimated_od_matrix(results_dir, convergence_stats_filename, msg_interval)

    od_dict = {
        "measurement OD": od_meas.stack(), 
        "estimated OD": od_est.reindex(index = od_meas.index, columns = od_meas.columns).fillna(0).stack()
    }

    if od_val is not None:
        od_dict["validation OD"] = od_val.stack()
    
    df_plot_od = pd.concat(od_dict, axis = 1).fillna(0).rename_axis(["Origin", 'Destination']).reset_index()

    fig, ax = plt.subplots()

    if od_val is not None:
        ax.errorbar(
            df_plot_od['validation OD'], df_plot_od['estimated OD'], color='coral',
            alpha=0.6, label='Validation', **scatter_kwds)

    ax.errorbar(
        df_plot_od['measurement OD'], df_plot_od['estimated OD'], markerfacecolor='none', markeredgecolor='steelblue',
        color='none', ecolor='gray', label='Calibration', **scatter_kwds)

    ax.legend(fontsize = fontsize)
    format_axis(ax, "Estimated", 'Ground truth')
    save_show(fig, save_dir / "02_od.pdf", **save_kwds)

    fit_cols = ['measurement OD'] + (['validation OD'] if od_val is not None else [])
    x_fit = np.array([df_plot_od[fit_cols].min().min(), df_plot_od[fit_cols].max().max()])

    slope_meas, intercept_meas = np.polyfit(df_plot_od['measurement OD'], df_plot_od['estimated OD'], 1)
    kwds = {'linestyle': '--'}
    ax.plot(x_fit, slope_meas * x_fit + intercept_meas, color='steelblue',
              label=f'Regression (measurement): y={slope_meas:.2f}x+{intercept_meas:.2f}', **kwds)

    if od_val is not None:
        slope_val, intercept_val = np.polyfit(df_plot_od['validation OD'], df_plot_od['estimated OD'], 1)
        ax.plot(x_fit, slope_val * x_fit + intercept_val, color='coral',
                  label=f'Regression (validation): y={slope_val:.2f}x+{intercept_val:.2f}', **kwds)

    ax.legend(fontsize=fontsize)
    save_show(fig, save_dir / "02_od_with_regression.pdf", **save_kwds)

    # --- Plot Transition probabilities
    P_meas = to_transition(od_meas)
    P_est = to_transition(od_est.reindex(index=od_meas.index, columns=od_meas.columns).fillna(0))

    prob_dict = {
        "measurement OD": P_meas.stack(),
        "estimated OD": P_est.stack(),
    }
    if od_val is not None:
        prob_dict["validation OD"] = to_transition(od_val).stack()

    df_prob = pd.concat(prob_dict, axis=1).fillna(0).rename_axis(["Origin", "Destination"]).reset_index()

    fit_cols = ['measurement OD'] + (['validation OD'] if od_val is not None else [])
    x_fit = np.array([df_prob[fit_cols].min().min(), df_prob[fit_cols].max().max()])

    slope_meas, intercept_meas = np.polyfit(df_prob['measurement OD'], df_prob['estimated OD'], 1)

    fig, ax = plt.subplots()
    if od_val is not None:
        slope_val, intercept_val = np.polyfit(df_prob['validation OD'], df_prob['estimated OD'], 1)
        ax.errorbar(df_prob['validation OD'], df_prob['estimated OD'], color='coral', alpha=0.6, label='Validation', **scatter_kwds)
        
    ax.errorbar(df_prob['measurement OD'], df_prob['estimated OD'], markerfacecolor='none', markeredgecolor='steelblue',
                color='none', ecolor='gray', label='Calibration', **scatter_kwds)


    format_axis(ax, "Estimated", 'Ground truth')
    save_show(fig, save_dir / "03_transition_probabilities.pdf", **save_kwds)

    kwds = {'linestyle': '--'}

    if od_val is not None:
        ax.plot(x_fit, slope_val * x_fit + intercept_val, color='coral',
                  label=f'Regression (validation): y={slope_val:.2f}x+{intercept_val:.2f}', **kwds)
                  
    ax.plot(x_fit, slope_meas * x_fit + intercept_meas, color='steelblue',
              label=f'Regression (measurement): y={slope_meas:.2f}x+{intercept_meas:.2f}', **kwds)

    ax.legend(fontsize=fontsize)
    save_show(fig, save_dir / "03_transition_probabilities_with_regression.pdf", **save_kwds)


if __name__ == "__main__":
    parser = ArgumentParser()
    args = parser.parse()
    main(results_dir=args.results_dir,
         measurement_states_xml=args.measurement_states_xml,
         save_dir=args.save_dir,
         validation_states_xml=args.validation_states_xml,
         convergence_stats_filename=args.convergence_stats_filename,
         msg_interval=args.msg_interval,
         save=args.save,
         show=args.show,
         output_suffix=args.output_suffix)
