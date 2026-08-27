"""Population-level reproduction plots for a finished AMAL run: departure-hour
structure, nStops, per-activity participation totals, and per-zone location
visit counts, aggregated across all partitions and compared against
measurement/validation ground truth.
OD/transition-matrix reproduction lives in plot_od_and_transitions.py.
"""
import gzip
import re
from ast import literal_eval
from collections import Counter, defaultdict
from pathlib import Path

import lxml.etree
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns

from .argument_parser import ArgumentParser
from .helpers.burn_in import post_burn_in_iterations, equalize_post_burn_in
from .helpers.od_transition_helpers import save_show
from .helpers.partition_discovery import discover_partition_dirs, states_xml_path
from .plotting_utilities import (process_states, stay_activity, departure_hours,
                                 parse_states_xml as _parse_states_xml)

MAX_NSTOPS = 10
LOCATION_ACTIVITIES = ["HOME", "WORK", "EDUCATION", "OTHER"]
# plots.ipynb drops the first half of the post-burn-in iterations for
# location reproduction specifically.
LOCATION_EXTRA_BURN = 0


def sort_legend_entries(ax: plt.Axes):
    # fontsize = ax.get_legend().get_texts()[0].get_fontsize()
    handles, labels = ax.get_legend_handles_labels()
    order = ['Measurement Data Set', 'Validation Data Set', 'Calibration']
    ordered_handles = [handles[labels.index(label)] for label in order if label in labels]
    ordered_labels = [label for label in order if label in labels]
    ax.legend(ordered_handles, ordered_labels)
    return ax 

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

    # Pass 1: parse partitions and collect post-burn-in iteration arrays so the
    # sample count is equalized across partitions before summing their means.
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
        subsets["visits"] = samples[int(len(samples) * LOCATION_EXTRA_BURN):]
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
    """Per-activity marginal durations from the last MultiRoundTrip sample of a
    ground-truth states file (same last-sample convention as ground_truth_metrics)."""
    mrt = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")[-1]
    return _marginal_durations_of_sample(mrt, activities)


def plot_marginal_activity_durations(est_durs, meas_durs, val_durs, activities, save_dir,
                                     horizon: float = 24.0, save: bool = True,
                                     show: bool = False, output_suffix: str = ".pdf"):
    """One panel per activity: overlaid marginal duration densities of calibration
    vs measurement (vs validation), pooled across agents/samples. Each activity
    panel is a grouped bar histogram over shared hourly duration bins, styled to
    match the other population bar plots (plot_bars)."""
    series = [("Calibration", est_durs, "steelblue"),
              ("Measurement Data Set", meas_durs, MEASUREMENT_COLOR)]
    if val_durs is not None:
        series.append(("Validation Data Set", val_durs, VALIDATION_COLOR))

    bins = np.arange(0, int(horizon) + 1)  # hourly duration bins over the horizon
    centers = bins[:-1] + 0.5
    width = 0.8 / len(series)
    offsets = (np.arange(len(series)) - (len(series) - 1) / 2) * width

    # ncols = 2
    # nrows = -(-len(activities) // ncols)  # ceil
    # fig, axes = plt.subplots(nrows, ncols, figsize=(6 * ncols, 4 * nrows), squeeze=False)
    for act in activities:
        fig, ax = plt.subplots()
    # for ax, act in zip(axes.flat, activities):
        for (label, durs, color), off in zip(series, offsets):
            values = np.asarray(durs.get(act, []), dtype=float)
            heights = (np.histogram(values, bins=bins, density=True)[0]
                       if values.size else np.zeros(len(centers)))
            ax.bar(centers + off, heights, width=width, color=color,
                   alpha=0.75, edgecolor="black", label=label)
        # ax.set_title(act)
        ax.set_xlabel("Duration [h]")
        ax.set_ylabel("Density")
        ax.set_ylim(bottom=0)
        ax.grid(axis="y", linestyle="--", alpha=0.5)

        # for ax in axes.flat[len(activities):]:
        #     ax.set_visible(False)
        sort_legend_entries(ax)

        # handles, labels = axes.flat[0].get_legend_handles_labels()
        # if handles and labels:
        #     fig.legend(handles, labels, loc="upper right")
        # fig.suptitle("Population marginal activity-duration reproduction")
        # fig.tight_layout(rect=[0, 0, 1, 0.97])
        save_show(fig, save_dir / f"06_activity_durations_{act}.pdf",
                  save=save, show=show, output_suffix=output_suffix)


def plot_bars(est, meas, val, index, xlabel, ylabel, xticklabels, path,
              save: bool = True, show: bool = False, output_suffix: str = ".pdf"):
    x = np.arange(len(index))
    width = 0.26
    fig, ax = plt.subplots()
    ax.bar(x + width, est.reindex(index, fill_value=0), width=width, color="steelblue",
           alpha=0.75, edgecolor="black", label="Calibration")
    if val is not None:
        ax.bar(x, val.reindex(index, fill_value=0), width=width, color="coral",
               alpha=0.75, edgecolor="black", label="Validation Data Set")
    ax.bar(x - width, meas.reindex(index, fill_value=0), width=width, color="mediumseagreen",
           alpha=0.75, edgecolor="black", label="Measurement Data Set")
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.set_xticks(x)
    ax.set_xticklabels(xticklabels)
    ax.set_ylim(bottom=0)
    ax.grid(axis="y", linestyle="--", alpha=0.5)
    ax.legend()
    sort_legend_entries(ax)
    fig.tight_layout()
    save_show(fig, path, save=save, show=show, output_suffix=output_suffix)


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

    total = {name: None for name in datasets}

    for act in LOCATION_ACTIVITIES:
        sub = _zone_counts(datasets, act)
        if sub is None:
            continue

        for name in datasets:
            if name in sub.columns:
                if total[name] is None:
                    total[name] = sub[name].copy()
                else:
                    total[name] = total[name].add(sub[name], fill_value = 0)

        fig, ax = plt.subplots()
        if "validation" in sub.columns:
            ax.scatter(sub["validation"], sub["calibration"], alpha=0.6, color="coral",
                       label="Calibration vs. Validation")
        ax.scatter(sub["measurement"], sub["calibration"], alpha=0.4, facecolor="none",
                   color="steelblue", label="Calibration vs. Measurement")
        lim = sub.max().max() * 1.05
        ax.plot([0, lim], [0, lim], ":", color="gray", label="y = x")
        ax.set_xlim(0, lim)
        ax.set_ylim(0, lim)
        ax.set_xlabel("Locations per Zone — Measurement / Validation")
        ax.set_ylabel("Locations per Zone — Calibration")
        ax.grid(True)
        ax.legend(loc="upper left")
        save_show(fig, save_dir / f"03_location_reproduction_{act}.pdf",
                  save=save, show=show, output_suffix=output_suffix)

    total_df = pd.concat(total, axis = 1).fillna(0)
    fig, ax = plt.subplots()
    if "validation" in total_df.columns:
        ax.scatter(total_df["validation"], total_df["calibration"],
                   alpha=0.6, color="coral",
                   label="Validation")
    ax.scatter(total_df["measurement"], total_df["calibration"],
               alpha=0.4, facecolor="none", color="steelblue",
               label="Calibration")

    lim = total_df.max().max() * 1.05
    ax.plot([0, lim], [0, lim], ":", color="gray")
    ax.set_xlim(0, lim)
    ax.set_ylim(0, lim)
    ax.set_xlabel("Ground Truth")
    ax.set_ylabel("Estimated")
    ax.grid(True)
    ax.legend(loc="upper left")

    save_show(fig, save_dir / "03_location_reproduction_TOTAL.pdf",
              save=save, show=show, output_suffix=output_suffix)


def plot_location_maps(est, meas, val, save_dir, save: bool = True,
                       show: bool = False, output_suffix: str = ".pdf"):
    """Per-activity heatmaps of visits per zone on the (row, col) grid, with
    calibration, measurement and validation side by side on a shared color
    scale (same map style as activity_location_plotter.py)."""
    datasets = {"calibration": est, "Measurement": meas}
    if val is not None:
        datasets["Validation"] = val

    coords = {literal_eval(zone) for s in datasets.values()
              for _, zone in (s.index if isinstance(s.index, pd.MultiIndex) else pd.MultiIndex.from_tuples(s.index))}
    rows_full = np.arange(min(r for r, _ in coords), max(r for r, _ in coords) + 1)
    cols_full = np.arange(min(c for _, c in coords), max(c for _, c in coords) + 1)

    for act in LOCATION_ACTIVITIES:
        df = _zone_counts(datasets, act)
        if df is None:
            continue
        mats = {name: (df[name].unstack("col")
                       .reindex(index=rows_full, columns=cols_full).fillna(0))
                for name in df.columns}
        vmax = max(mat.values.max() for mat in mats.values())
        fig, axes = plt.subplots(1, len(mats), figsize=(7 * len(mats), 7))
        for ax, (name, mat) in zip(np.atleast_1d(axes), mats.items()):
            sns.heatmap(mat, ax=ax, cmap="viridis", square=True, annot=True,
                        fmt=".0f", cbar=True, vmin=0, vmax=vmax)
            ax.invert_yaxis()
            ax.set_title(name)
            ax.set_xlabel("col")
            ax.set_ylabel("row")
        fig.suptitle(f"Locations per Zone – activity: {act}")
        fig.tight_layout(rect=[0, 0, 1, 0.96])
        save_show(fig, save_dir / f"03_location_map_{act}.pdf",
                  save=save, show=show, output_suffix=output_suffix)


ACTIVITY_SHARE_ACTIVITIES = ["WORK", "EDUCATION", "OTHER"]
ACTIVITY_SHARE_COLORS = {"WORK": "k", "EDUCATION": "g", "OTHER": "b"}
# Ground-truth overlay colors, matching the other population plots (plot_bars).
MEASUREMENT_COLOR = "mediumseagreen"
VALIDATION_COLOR = "coral"


def _ground_truth_share_vs_sigma(gt_visits, activity, opp_act):
    """(sigma, visit_share) points for one activity from a ground-truth visits
    Series indexed by (activity, loc_string) -> count. Share is normalized within
    the activity; one point per location. Returns None if unavailable."""
    if gt_visits is None:
        return None
    try:
        act_counts = gt_visits.xs(activity, level=0)
    except (KeyError, TypeError):
        return None
    act_counts = act_counts[act_counts > 0]
    if act_counts.empty:
        return None
    total = act_counts.sum()
    df = pd.DataFrame({
        "location_id": [literal_eval(loc) for loc in act_counts.index],
        "visit_share": (act_counts / total).to_numpy(),
    })
    merged = pd.merge(df, opp_act, on="location_id", how="inner")
    return merged if not merged.empty else None


def plot_activity_share_vs_opportunity(results_dir, data_dir, save_dir,
                                       meas_visits=None, val_visits=None,
                                       save: bool = True, show: bool = False,
                                       output_suffix: str = ".pdf"):
    """Per-activity scatter of visit share vs opportunity weight over the joined
    (partitions_merged) states. Unlike the per-partition evaluation plot, this does
    NOT average across window iterations: every (iteration, location) pair is a
    separate scatter point, matching the other merged population-level plots.
    Measurement and validation ground truth (one point per location) are overlaid
    in the same figure with the same colors as the other population plots."""
    merged_xml = Path(results_dir) / "partitions_merged" / "final_states_merged.xml"
    if not merged_xml.exists():
        print(f"WARNING: joined states {merged_xml} not found; skipping "
              "activity-share vs opportunity plot.")
        return

    _, sim_stays = process_states(merged_xml)
    if sim_stays.empty:
        print(f"WARNING: joined states {merged_xml} parsed empty; skipping "
              "activity-share vs opportunity plot.")
        return

    opportunity_fp = Path(data_dir) / "opportunity_weights.csv"
    opp = pd.read_csv(opportunity_fp)
    opp["location_id"] = opp["samscode"].apply(literal_eval)
    opp = opp.rename(columns={"area": "sigma"})[["location_id", "sigma", "ANDAMAL"]]

    # sns.set_style("whitegrid")
    fig, axes = plt.subplots(1, len(ACTIVITY_SHARE_ACTIVITIES),
                             figsize=(16, 5), constrained_layout=True)

    for ax, activity in zip(np.atleast_1d(axes), ACTIVITY_SHARE_ACTIVITIES):
        sim_act = sim_stays[sim_stays["activity"] == activity]
        opp_act = opp[opp["ANDAMAL"] == activity]

        visits_by_iter = (
            sim_act.groupby(["iteration", "coords"]).size().reset_index(name="visit_count")
        )
        if visits_by_iter.empty:
            ax.set_title(f"{activity} (no data)")
            continue

        iter_totals = visits_by_iter.groupby("iteration")["visit_count"].sum().rename("iter_total")
        visits_by_iter = visits_by_iter.merge(iter_totals, on="iteration", how="left")
        visits_by_iter["visit_share"] = (
            visits_by_iter["visit_count"] / visits_by_iter["iter_total"].replace(0, np.nan)
        )
        visits_by_iter = visits_by_iter.rename(columns={"coords": "location_id"})

        # Keep every (iteration, location) row -- no averaging across the window.
        merged = pd.merge(visits_by_iter, opp_act, on="location_id", how="inner")
        if merged.empty:
            ax.set_title(f"{activity} (no data)")
            continue

        corr = merged["visit_share"].corr(merged["sigma"])
        ax.scatter(
            merged["sigma"], merged["visit_share"],
            s=30, alpha=0.45, color=ACTIVITY_SHARE_COLORS[activity],
            edgecolors="white", linewidths=0.4, zorder=2,
            label="Calibration",
        )

        if len(merged) > 1 and merged["sigma"].std() > 0:
            z = np.polyfit(merged["sigma"], merged["visit_share"], 1)
            p = np.poly1d(z)
            xs = np.sort(merged["sigma"].values)
            ax.plot(xs, p(xs), "--", color=ACTIVITY_SHARE_COLORS[activity], lw=1.8, alpha=0.95)

        # Ground-truth overlays: one point per location, same colors as the other
        # population plots. Skip a series when its data is missing for the activity.
        for gt_visits, color, gt_label in (
            (meas_visits, MEASUREMENT_COLOR, "Measurement Data Set"),
            (val_visits, VALIDATION_COLOR, "Validation Data Set"),
        ):
            gt = _ground_truth_share_vs_sigma(gt_visits, activity, opp_act)
            if gt is None:
                continue
            ax.scatter(
                gt["sigma"], gt["visit_share"],
                s=70, alpha=0.85, color=color,
                edgecolors="black", linewidths=0.5, marker="s", zorder=3,
                label=gt_label,
            )

        ax.set_title(f"{activity} (corr: {corr:.2f})")
        ax.set_xlabel("Opportunity Weight (Sigma)")
        ax.set_ylabel("Visit Share (per window iteration)")
        ax.grid(True, linestyle="--", linewidth=0.6, alpha=0.55)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        ax.legend()

    save_show(fig, save_dir / "activity_visit_share_vs_opportunity_weight.pdf",
              save=save, show=show, output_suffix=output_suffix)


def main(results_dir: Path,
         measurement_states_xml: Path,
         data_dir: Path,
         save_dir: Path = None,
         validation_states_xml: Path = None,
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
              save_dir / "01_nStops_histogram.pdf", **save_kwds)
    print(f"Avg nStops (calibration): {(est['nstops'].index * est['nstops']).sum() / est['nstops'].sum()}")

    activities = ([a for a in LOCATION_ACTIVITIES if a in est["participation"].index]
                  + sorted(set(est["participation"].index) - set(LOCATION_ACTIVITIES)))
    plot_bars(est["participation"], meas["participation"], v("participation"), activities,
              "Activity", "Number of Activity Episodes", activities,
              save_dir / "04_activity_participation.pdf", **save_kwds)

    plot_location_reproduction(est["visits"], meas["visits"], v("visits"), save_dir, **save_kwds)
    plot_location_maps(est["visits"], meas["visits"], v("visits"), save_dir, **save_kwds)
    plot_activity_share_vs_opportunity(results_dir, data_dir, save_dir,
                                       meas["visits"], v("visits"), **save_kwds)

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