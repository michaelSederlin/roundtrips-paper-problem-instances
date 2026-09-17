from ast import literal_eval
from collections import Counter, defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from .argument_parser import ArgumentParser
from .helpers.burn_in import post_burn_in_iterations, equalize_post_burn_in
from .helpers.od_transition_helpers import save_show
from .common_parameters import (barplot_size, scatterplot_size, format_axis,
                                format_categorical_axis, sort_legend_entries,
                                MEASUREMENT_COLOR, VALIDATION_COLOR,
                                legend_fontsize)
from .helpers.partition_discovery import discover_partition_dirs, states_xml_path
from .plotting_utilities import (stay_activity, departure_hours,
                                 parse_states_xml as _parse_states_xml, 
                                 plot_bars)

MAX_NSTOPS = 10
LOCATION_ACTIVITIES = ["HOME", "WORK", "EDUCATION", "OTHER"]

def sample_metrics(mrt, time_bin_size):
    """Population-level counts of one MultiRoundTrip sample."""
    counts = {"departures": Counter(), "nstops": Counter(),
              "participation": Counter(), "visits": Counter()}
    for rt in mrt.findall("RoundTrip"):
        counts["departures"].update(departure_hours(rt, time_bin_size))
        stays = rt.findall("Episodes/StayEpisode")
        counts["nstops"][len(stays)] += 1
        for stay in stays:
            act = stay_activity(stay)
            counts["participation"][act] += 1
            counts["visits"][(act, (stay.get("loc") or "").split("[")[0])] += 1
    return counts


def calibration_metrics(results_dir, convergence_filename, window, time_bin_size):
    """Post-burn-in mean of each metric per partition, summed across partitions
    (partitions are disjoint subpopulations, so population-level expected
    values are the sums of the per-partition expected values)."""
    totals = {}

    parsed = {}
    arrays, converged = {}, {}
    for pdir in discover_partition_dirs(results_dir):
        xml_path = states_xml_path(pdir)
        if xml_path is None:
            continue
        mrts_all = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")
        all_iterations = np.sort(np.array([int(m.get("iteration")) for m in mrts_all]))
        post_burn_in, conv = post_burn_in_iterations(all_iterations, pdir / convergence_filename, window)
        parsed[pdir.name] = mrts_all
        arrays[pdir.name] = post_burn_in
        converged[pdir.name] = conv

    keep_sets = equalize_post_burn_in(arrays, converged)

    # Pass 2: keep each partition's common-N most-recent samples and aggregate.
    for label, mrts_all in parsed.items():
        keep = keep_sets[label]
        samples = [sample_metrics(m, time_bin_size) for m in mrts_all if int(m.get("iteration")) in keep]

        subsets = {k: samples for k in ("departures", "nstops", "participation")}
        subsets["visits"] = samples
        for key, sub in subsets.items():
            mean = pd.DataFrame([s[key] for s in sub]).fillna(0).mean()
            totals[key] = mean if key not in totals else totals[key].add(mean, fill_value=0)
    return totals


def ground_truth_metrics(xml_path, time_bin_size):
    """Last-sample metrics of a ground-truth states file."""
    mrt = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")[-1]
    return {k: pd.Series(v, dtype=float).sort_index() for k, v in sample_metrics(mrt, time_bin_size).items()}


def _marginal_durations_of_sample(mrt, activities):
    """Per-activity marginal durations of one MultiRoundTrip sample: for every
    RoundTrip, sum its StayEpisode durations per activity and record one value
    per activity, using 0.0 when the agent does not do the activity (so the
    values are marginal, not conditional). Returns {activity: [dur, ...]}."""
    out = {a: [] for a in activities}
    for rt in mrt.findall("RoundTrip"):
        per_act = defaultdict(float)
        for stay in rt.findall("Episodes/StayEpisode"):
            per_act[stay_activity(stay)] += float(stay.get("end")) - float(stay.get("start"))
        for act in activities:
            out[act].append(per_act.get(act, 0.0))
    return out


def calibration_marginal_durations(results_dir, convergence_filename, window, activities):
    """Per-activity flat list of marginal per-agent durations pooled over all
    kept post-burn-in samples of all partitions. Same two-pass partition/burn-in
    aggregation as calibration_metrics; absences contribute 0.0 (marginal)."""
    pooled = {a: [] for a in activities}

    # Pass 1: parse partitions and collect post-burn-in iteration arrays so the
    # sample count is equalized across partitions before pooling.
    parsed = {}
    arrays, converged = {}, {}
    for pdir in discover_partition_dirs(results_dir):
        xml_path = states_xml_path(pdir)
        if xml_path is None:
            continue
        mrts_all = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")
        all_iterations = np.sort(np.array([int(m.get("iteration")) for m in mrts_all]))
        post_burn_in, conv = post_burn_in_iterations(all_iterations, pdir / convergence_filename, window)
        parsed[pdir.name] = mrts_all
        arrays[pdir.name] = post_burn_in
        converged[pdir.name] = conv

    keep_sets = equalize_post_burn_in(arrays, converged)

    # Pass 2: keep each partition's common-N most-recent samples and pool.
    for label, mrts_all in parsed.items():
        keep = keep_sets[label]
        for mrt in (m for m in mrts_all if int(m.get("iteration")) in keep):
            for act, durs in _marginal_durations_of_sample(mrt, activities).items():
                pooled[act].extend(durs)
    return pooled


def ground_truth_marginal_durations(xml_path, activities):
    mrt = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")[-1]
    return _marginal_durations_of_sample(mrt, activities)


def plot_marginal_activity_durations(est_durs, meas_durs, val_durs, activities, save_dir,
                                     horizon: float = 24.0, save: bool = True,
                                     show: bool = False, output_suffix: str = ".pdf"):
    series = [("Calibration", est_durs, "steelblue"),
              ("Measurement", meas_durs, MEASUREMENT_COLOR)]
    if val_durs is not None:
        series.append(("Validation", val_durs, VALIDATION_COLOR))

    bins = np.arange(0, int(horizon) + 1)  # hourly duration bins over the horizon
    centers = bins[:-1] + 0.5
    width = 0.8 / len(series)
    offsets = (np.arange(len(series)) - (len(series) - 1) / 2) * width

    for act in activities:
        fig, ax = plt.subplots(figsize=barplot_size)
        for (label, durs, color), off in zip(series, offsets):
            values = np.asarray(durs.get(act, []), dtype=float)
            heights = (np.histogram(values, bins=bins, density=True)[0]
                       if values.size else np.zeros(len(centers)))
            ax.bar(centers + off, heights, width=width, color=color,
                   alpha=0.75, edgecolor="black", label=label)
        format_categorical_axis(ax, "Duration [h]", "Density")
        sort_legend_entries(ax)
        save_show(fig, save_dir / f"06_activity_durations_{act}.pdf",
                  save=save, show=show, output_suffix=output_suffix)



def _zone_counts(datasets, act):
    """Visit counts per (row, col) zone for one activity, aligned across
    `datasets` on the union of zones, missing entries filled with 0."""
    cols = {}
    for name, s in datasets.items():
        idx = s.index if isinstance(s.index, pd.MultiIndex) else pd.MultiIndex.from_tuples(s.index)
        if act in idx.get_level_values(0):
            cols[name] = s.set_axis(idx).xs(act)
    if not cols:
        return None
    df = pd.concat(cols, axis=1, join="outer").fillna(0)
    df.index = pd.MultiIndex.from_tuples([literal_eval(z) for z in df.index], names=["row", "col"])
    return df


def plot_location_reproduction(est, meas, val, save_dir, save: bool = True,
                              show: bool = False, output_suffix: str = ".pdf"):
    datasets = {"calibration": est, "measurement": meas}
    if val is not None:
        datasets["validation"] = val

    subs = {act: _zone_counts(datasets, act) for act in LOCATION_ACTIVITIES}
    total = {name: None for name in datasets}
    for sub in subs.values():
        for name in datasets:
            if name in sub.columns:
                if total[name] is None:
                    total[name] = sub[name].copy()
                else:
                    total[name] = total[name].add(sub[name], fill_value = 0)
    total_df = pd.concat(total, axis = 1).fillna(0)

    fig, ax = plt.subplots(figsize=scatterplot_size)
    if "validation" in total_df.columns:
        ax.scatter(total_df["validation"], total_df["calibration"],
                   alpha=0.6, color="coral",
                   label="Validation")
    ax.scatter(total_df["measurement"], total_df["calibration"],
               alpha=0.4, facecolor="none", color="steelblue",
               label="Calibration")

    lim = total_df.max().max() * 1.05
    ax.plot([0, lim], [0, lim], ":", color="gray")
    format_axis(ax, xlabel = "Ground Truth", ylabel = "Calibrated", equal_lims=True)
    ax.legend(loc="upper left", fontsize=legend_fontsize)
    save_show(fig, save_dir / "03_location_reproduction_TOTAL.pdf",
              save=save, show=show, output_suffix=output_suffix)

ACTIVITY_SHARE_ACTIVITIES = ["WORK", "EDUCATION", "OTHER"]
ACTIVITY_SHARE_COLORS = {"WORK": "k", "EDUCATION": "g", "OTHER": "b"}
MEASUREMENT_COLOR = "mediumseagreen"
VALIDATION_COLOR = "coral"

def main(results_dir: Path,
         measurement_states_xml: Path,
         data_dir: Path,
         save_dir: Path | None = None,
         validation_states_xml: Path | None = None,
         convergence_stats_filename: str = "convergenceStats.tsv",
         window: int = 1,
         time_bin_size_h: float = 1.0,
         horizon: float = 24.0,
         save: bool = True,
         show: bool = False,
         output_suffix: str = ".pdf"):
    if measurement_states_xml is None:
        raise ValueError("measurement_states_xml is required")

    results_dir = Path(results_dir)
    save_dir = results_dir if save_dir is None else Path(save_dir)
    save_kwds = dict(save=save, show=show, output_suffix=output_suffix)

    est = calibration_metrics(results_dir, convergence_stats_filename,
                             window, time_bin_size_h)
    meas = ground_truth_metrics(measurement_states_xml, 1)
    val = (ground_truth_metrics(validation_states_xml, 1)
           if validation_states_xml is not None else None)

    def v(key):
        return val[key] if val is not None else None

    hours = list(range(int(horizon)))
    plot_bars(est["departures"], meas["departures"], v("departures"), hours,
              "Hour", "Frequency", [str(h) if h % 2 == 0 else "" for h in hours],
              save_dir / "05_departures.pdf", **save_kwds)

    n = min(MAX_NSTOPS, int(est["nstops"].index.max())) + 1
    plot_bars(est["nstops"], meas["nstops"], v("nstops"), range(n),
              "Number of Stops", "Frequency", [str(i) if i % 2 == 0 else "" for i in range(n)],
              path = save_dir / "01_nStops_histogram.pdf", legend_placement="upper left", **save_kwds)
    print(f"Avg nStops (calibration): {(est['nstops'].index * est['nstops']).sum() / est['nstops'].sum()}")

    activities = ([a for a in LOCATION_ACTIVITIES if a in est["participation"].index]
                  + sorted(set(est["participation"].index) - set(LOCATION_ACTIVITIES)))
    plot_bars(est["participation"], meas["participation"], v("participation"), activities,
              "Activity", "Number of Activity Episodes", activities,
              legend_placement="lower left",
              path = save_dir / "04_activity_participation.pdf", **save_kwds)

    plot_location_reproduction(est["visits"], meas["visits"], v("visits"), save_dir, **save_kwds)

    est_durs = calibration_marginal_durations(results_dir, convergence_stats_filename,
                                             window, activities)
    meas_durs = ground_truth_marginal_durations(measurement_states_xml, activities)
    val_durs = (ground_truth_marginal_durations(validation_states_xml, activities)
                if validation_states_xml is not None else None)
    plot_marginal_activity_durations(est_durs, meas_durs, val_durs, activities, save_dir,
                                     horizon=horizon, **save_kwds)

    print(f"Done. Population-level reproduction plots written to: {save_dir}")


if __name__ == "__main__":
    parser = ArgumentParser()
    args = parser.parse()
    main(results_dir=args.results_dir,
         measurement_states_xml=args.measurement_states_xml,
         data_dir=args.data_dir,
         save_dir=args.save_dir,
         validation_states_xml=args.validation_states_xml,
         convergence_stats_filename=args.convergence_stats_filename,
         window=args.window,
         time_bin_size_h=args.time_bin_size_h,
         horizon=args.horizon,
         save=args.save,
         show=args.show,
         output_suffix=args.output_suffix)