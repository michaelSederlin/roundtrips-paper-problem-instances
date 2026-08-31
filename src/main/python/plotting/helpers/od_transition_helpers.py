import gzip
from collections import Counter
from pathlib import Path
from typing import Optional
from xml.etree import ElementTree as ET
import re

import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import numpy as np
import pandas as pd
import seaborn as sns

from .partition_discovery import discover_partition_dirs, states_xml_path
from .burn_in import post_burn_in_iterations, equalize_post_burn_in
from ..plotting_utilities import save_show
from ..common_parameters import barplot_size, scatterplot_size



# --------------------------------------------------------------------------
# Node-cycle OD extraction: legs are consecutive node coordinates within a
# RoundTrip's Nodes sequence, closing the loop back to the start. Kept
# separate from construct_od_matrix()'s process_states()-based per-episode
# from/to columns below -- a distinct, independently-verified concept, not
# to be merged.
# --------------------------------------------------------------------------

_node_pat = re.compile(r"\(([^)]*)\)\[[^\]]*\]")


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


def _parse_states_xml(xml_path: Path) -> ET.ElementTree:
    if xml_path.suffix == ".gz":
        return ET.parse(gzip.open(xml_path))
    return ET.parse(xml_path)


def extract_od_matrix(xml_path: Path) -> pd.DataFrame:
    """OD matrix (origin rows, destination cols) from the last sample of a states file.
    Legs are consecutive node coordinates, closing the loop back to the start."""
    trip = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")[-1]
    od = pd.Series(_sample_od(trip), dtype=float)
    return _od_series_to_matrix(od)


def construct_od_matrix(
    df: pd.DataFrame,
    from_col: str = "from",
    to_col: str = "to",
    from_col_replace: str = "",
    to_col_replace: str = "",
) -> pd.DataFrame:
    df_out = df.copy()
    if from_col_replace:
        df_out[from_col] = df_out[from_col].apply(lambda x: re.sub(from_col_replace, "", x))
    if to_col_replace:
        df_out[to_col] = df_out[to_col].apply(lambda x: re.sub(to_col_replace, "", x))

    od = (
        df_out.groupby([from_col, to_col])
        .size()
        .reset_index(name="count")
        .pivot(index=from_col, columns=to_col, values="count")
        .fillna(0)
        .astype(int)
    )
    zones = sorted(set(od.index).union(set(od.columns)))
    return od.reindex(index=zones, columns=zones, fill_value=0)


def compute_transition_matrix(od_counts: pd.DataFrame, alpha: float = 0.0):
    n_cols = od_counts.shape[1]
    row_sum = od_counts.sum(axis=1)
    p = (od_counts + alpha).div(row_sum + alpha * n_cols, axis=0)
    zero_rows = row_sum == 0
    if zero_rows.any():
        p.loc[zero_rows, :] = 1.0 / n_cols
    return p, row_sum


def summarize_simulation_vs_truth(
    sim_trips: pd.DataFrame,
    tru_trips_last: pd.DataFrame,
    iterations: np.ndarray,
    alpha: float,
    replace_regex: str,
) -> dict:
    tru_od = construct_od_matrix(
        tru_trips_last,
        from_col="from",
        to_col="to",
        from_col_replace=replace_regex,
        to_col_replace=replace_regex,
    )
    zones = tru_od.index.tolist()

    sim_recent = sim_trips[sim_trips["iteration"].isin(iterations)]

    od_mats, p_mats = [], []
    for _, df_iter in sim_recent.groupby("iteration"):
        od = construct_od_matrix(
            df_iter,
            from_col="from",
            to_col="to",
            from_col_replace=replace_regex,
            to_col_replace=replace_regex,
        ).reindex(index=zones, columns=zones, fill_value=0)
        p_iter, _ = compute_transition_matrix(od, alpha=alpha)
        od_mats.append(od)
        p_mats.append(p_iter)

    od_stack = np.stack(od_mats, axis=0)
    p_stack = np.stack(p_mats, axis=0)

    od_mean = pd.DataFrame(od_stack.mean(axis=0), index=zones, columns=zones)
    od_std = pd.DataFrame(od_stack.std(axis=0, ddof=1), index=zones, columns=zones)
    p_mean = pd.DataFrame(p_stack.mean(axis=0), index=zones, columns=zones)
    p_std = pd.DataFrame(p_stack.std(axis=0, ddof=1), index=zones, columns=zones)
    p_tru, _ = compute_transition_matrix(tru_od, alpha=alpha)

    eps = 1e-12
    z_od = (tru_od - od_mean) / (od_std + eps)
    z_p = (p_tru - p_mean) / (p_std + eps)

    return {
        "tru_od": tru_od,
        "p_tru": p_tru,
        "od_mean": od_mean,
        "od_std": od_std,
        "od_t": od_stack,
        "p_t": p_stack,
        "p_mean": p_mean,
        "p_std": p_std,
        "z_od": z_od,
        "z_p": z_p,
    }


def align_matrices(target_counts: pd.DataFrame, sim_counts: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    all_origins = sorted(set(target_counts.index.astype(str)) | set(sim_counts.index.astype(str)))
    all_destinations = sorted(set(target_counts.columns.astype(str)) | set(sim_counts.columns.astype(str)))

    target_aligned = target_counts.reindex(index=all_origins, columns=all_destinations, fill_value=0.0)
    sim_aligned = sim_counts.reindex(index=all_origins, columns=all_destinations, fill_value=0.0)
    return target_aligned, sim_aligned


# --------------------------------------------------------------------------
# Partition-aware aggregation: sums disjoint partitions' OD statistics into a
# population-level total, mirroring plot_population_reproduction.py's
# departures/nStops/location aggregation pattern.
# --------------------------------------------------------------------------

def extract_partitioned_od_stats(
    results_dir: Path,
    convergence_stats_filename: str,
    window: int,
) -> Optional[dict]:
    """Per-partition post-burn-in OD-count mean and variance, combined across
    all partitions of a run. Partitions are disjoint, independent
    subpopulations, so the combined mean is the sum of partition means and
    the combined variance is the sum of partition variances
    (Var(sum X_i) = sum(Var(X_i)) for independent X_i) -- no cross-partition
    covariance term is needed. Returns None if no partitions are found."""
    partition_dirs = discover_partition_dirs(results_dir)
    if not partition_dirs:
        return None

    # Pass 1: parse each partition and collect its post-burn-in iteration array
    # so the sample count is equalized across partitions before combining.
    parsed = {}
    arrays, converged = {}, {}
    for partition_dir in partition_dirs:
        xml_path = states_xml_path(partition_dir)
        if xml_path is None:
            continue
        try:
            samples = _parse_states_xml(xml_path).getroot().findall(".//MultiRoundTrip")
            all_iterations = np.sort(np.array([int(s.get("iteration")) for s in samples]))
            conv_fp = partition_dir / convergence_stats_filename
            post_burn_in, conv = post_burn_in_iterations(all_iterations, conv_fp, window)
            parsed[partition_dir.name] = samples
            arrays[partition_dir.name] = post_burn_in
            converged[partition_dir.name] = conv
        except Exception as e:
            print(f"{xml_path} threw error: {e}")

    keep_sets = equalize_post_burn_in(arrays, converged)

    # Pass 2: keep each partition's common-N most-recent samples and combine.
    partition_stats = []
    for label, samples in parsed.items():
        keep = keep_sets[label]
        selected = [s for s in samples if int(s.get("iteration")) in keep]
        if not selected:
            print(f"WARNING: no samples left after burn-in for partition {label}; skipping.")
            continue
        od_series = pd.concat(
            [pd.Series(_sample_od(t), dtype=float) for t in selected], axis=1
        ).fillna(0)
        mean_i = od_series.mean(axis=1)
        if od_series.shape[1] >= 2:
            var_i = od_series.var(axis=1, ddof=1)
        else:
            print(
                f"WARNING: {label} has only {od_series.shape[1]} "
                f"post-burn-in sample(s); its contribution to od_std is treated as 0."
            )
            var_i = mean_i * 0.0
        partition_stats.append((mean_i, var_i))

    if not partition_stats:
        return None

    total_mean = pd.Series(dtype=float)
    total_var = pd.Series(dtype=float)
    for mean_i, var_i in partition_stats:
        total_mean = total_mean.add(mean_i, fill_value=0.0)
        total_var = total_var.add(var_i, fill_value=0.0)

    return {
        "od_mean": _od_series_to_matrix(total_mean),
        "od_std": _od_series_to_matrix(total_var.pow(0.5)),
    }


def summarize_partitioned_simulation_vs_truth(od_stats: dict, tru_od: pd.DataFrame, alpha: float) -> dict:
    """Sibling to summarize_simulation_vs_truth() for the partition-aggregated_calibration
    OD estimate. Transition-probability std is NOT combined across
    partitions: probabilities are a nonlinear ratio of OD counts (row sums),
    so partition-level probability variances don't combine additively the way
    OD-count variances do (unlike od_std, which is exact -- see
    extract_partitioned_od_stats). p_std/z_p are therefore left as None; the
    plotting helpers below already treat a None std as "omit error bars",
    so no plotting code needs to change."""
    tru_od_aligned, od_mean_aligned = align_matrices(tru_od, od_stats["od_mean"])
    od_std_aligned = od_stats["od_std"].reindex(
        index=od_mean_aligned.index, columns=od_mean_aligned.columns, fill_value=0.0
    )

    p_mean, _ = compute_transition_matrix(od_mean_aligned, alpha=alpha)
    p_tru, _ = compute_transition_matrix(tru_od_aligned, alpha=alpha)

    eps = 1e-12
    z_od = (tru_od_aligned - od_mean_aligned) / (od_std_aligned + eps)

    return {
        "tru_od": tru_od_aligned,
        "p_tru": p_tru,
        "od_mean": od_mean_aligned,
        "od_std": od_std_aligned,
        "od_t": None,
        "p_t": None,
        "p_mean": p_mean,
        "p_std": None,
        "z_od": z_od,
        "z_p": None,
    }


def plot_transition_scatter(
    target_prob: pd.DataFrame,
    sim_prob: pd.DataFrame,
    sim_std: pd.DataFrame | None,
    out_dir: Path,
    args,
    ):
    target_flat = target_prob.stack().rename("target")
    sim_flat = sim_prob.stack().rename("sim")
    df = pd.concat([target_flat, sim_flat], axis=1).reset_index()
    df.columns = ["origin", "destination", "target", "sim"]

    if sim_std is not None:
        std_flat = sim_std.stack().rename("sim_std").reset_index(drop=True)
        df["sim_std"] = std_flat

    fig = plt.figure(figsize=scatterplot_size)
    if sim_std is not None:
        plt.errorbar(
            df["target"],
            df["sim"],
            yerr=df["sim_std"],
            fmt="none",
            ecolor="0.65",
            alpha=0.28,
            capsize=1.5,
            lw=0.8,
            zorder=1,
        )

    plt.scatter(
        df["target"],
        df["sim"],
        s=14,
        alpha=0.62,
        color="tab:blue",
        edgecolors="white",
        linewidths=0.25,
        zorder=2,
    )

    target_vals = df.loc[:, "target"].astype(float)
    sim_vals = df.loc[:, "sim"].astype(float)

    max_val = max(target_vals.max(), sim_vals.max())
    plt.plot([0, max_val], [0, max_val], "r--", lw=1.2, label="y = x")
    plt.xlabel("Target p(j|i)")
    plt.ylabel("Simulated p(j|i)")
    plt.title("Transition correspondence: target vs simulated")
    plt.grid(True, alpha=0.3)
    plt.legend(loc="upper left")

    corr = target_vals.corr(sim_vals, method="pearson")
    mae = np.mean(np.abs(sim_vals - target_vals))
    rmse = np.sqrt(np.mean((sim_vals - target_vals) ** 2))

    plt.text(
        0.98,
        0.02,
        f"Pearson={corr:.3f}\nMAE={mae:.4f}\nRMSE={rmse:.4f}",
        transform=plt.gca().transAxes,
        ha="right",
        va="bottom",
        fontsize=9,
        bbox=dict(boxstyle="round,pad=0.25", facecolor="white", alpha=0.85, edgecolor="0.7"),
    )

    save_show(fig, out_dir / "transition_probability_scatter.pdf", save=args.save, show=args.show,
              output_suffix=args.output_suffix)
    print(f"Transition scatter metrics: Pearson={corr:.4f}, MAE={mae:.6f}, RMSE={rmse:.6f}")


def plot_row_comparisons(
    target_prob: pd.DataFrame,
    sim_prob: pd.DataFrame,
    sim_std: pd.DataFrame | None,
    out_dir: Path,
    args,
    max_rows: int | None = None,
):
    origins = list(target_prob.index)
    if max_rows is not None:
        origins = origins[:max_rows]

    if not origins:
        return

    ncols = 2
    nrows_per_page = 4  # 4 rows x 2 cols = 8 origins per page
    plots_per_page = ncols * nrows_per_page

    pdf = PdfPages(out_dir / "transition_row_compare.pdf") if args.save else None

    for page_start in range(0, len(origins), plots_per_page):
        page_origins = origins[page_start:page_start + plots_per_page]

        fig, axes = plt.subplots(
            nrows=nrows_per_page,
            ncols=ncols,
            figsize=(barplot_size[0], barplot_size[1] * nrows_per_page),
            squeeze=False,
        )
        axes_flat = axes.ravel()

        for ax_idx, origin in enumerate(page_origins):
            ax = axes_flat[ax_idx]

            target_row = target_prob.loc[origin].to_numpy()
            sim_row = sim_prob.loc[origin].to_numpy()
            sim_std_row = (
                sim_std.loc[origin].to_numpy()
                if sim_std is not None
                else np.zeros_like(sim_row)
            )

            x = np.arange(len(target_prob.columns))
            width = 0.4

            ax.bar(x - width / 2, target_row, width=width, label="Target")
            ax.bar(
                x + width / 2,
                sim_row,
                width=width,
                yerr=sim_std_row,
                capsize=2,
                label="Simulated mean ± std",
            )

            ax.set_xticks(x)
            ax.set_xticklabels(target_prob.columns, rotation=90)
            ax.set_ylabel("p(j|i)")
            ax.set_xlabel("Destination")
            ax.set_title(f"Origin: {origin}")
            ax.grid(axis="y", alpha=0.3)

            if ax_idx == 0:
                ax.legend()

        # hide unused panels on last page
        for j in range(len(page_origins), len(axes_flat)):
            axes_flat[j].axis("off")

        fig.tight_layout()

        if args.save and pdf is not None:
            pdf.savefig(fig, bbox_inches="tight")
        if args.show:
            plt.show()
        plt.close(fig)

    if pdf is not None:
        pdf.close()


def rowwise_rmse(target_prob: pd.DataFrame, sim_prob: pd.DataFrame) -> pd.Series:
    diff = sim_prob - target_prob
    return np.sqrt((diff ** 2).mean(axis=1))


def total_rmse(target_prob: pd.DataFrame, sim_prob: pd.DataFrame) -> float:
    diff = sim_prob - target_prob
    return float(np.sqrt((diff.to_numpy() ** 2).mean()))


def evaluate_transition_fit(summary: dict, out_dir: Path, args):
    target_prob = summary["p_tru"].copy()
    sim_prob = summary["p_mean"].copy()
    sim_std = summary.get("p_std", None)

    target_prob, sim_prob = align_matrices(target_prob, sim_prob)

    if sim_std is not None:
        sim_std = sim_std.reindex(index=target_prob.index, columns=target_prob.columns, fill_value=0.0)

    plot_transition_scatter(target_prob, sim_prob, sim_std, out_dir, args)

    plot_row_comparisons(target_prob, sim_prob, sim_std, out_dir, args, max_rows=None)

    print("Total transition RMSE:", total_rmse(target_prob, sim_prob))
    print("Row-wise transition RMSE:")
    print(rowwise_rmse(target_prob, sim_prob).sort_values())


def plot_transition_heatmaps(summary: dict, out_dir: Path, args):
    fig, ax = plt.subplots(1, 3, figsize=(scatterplot_size[0] * 3, scatterplot_size[1]))
    sns.heatmap(summary["p_mean"], ax=ax[0])
    sns.heatmap(summary["p_tru"], ax=ax[1])
    sns.heatmap(summary["p_mean"] - summary["p_tru"], ax=ax[2])
    fig.tight_layout()
    save_show(fig, out_dir / "transition_probability_heatmaps.pdf", save=args.save, show=args.show,
              output_suffix=args.output_suffix)


def plot_od_count_heatmaps(summary: dict, out_dir: Path, args):
    fig, ax = plt.subplots(1, 3, figsize=(scatterplot_size[0] * 3, scatterplot_size[1]))
    sns.heatmap(summary["od_mean"], ax=ax[0])
    ax[0].set_title("Simulated OD counts (mean)")
    sns.heatmap(summary["tru_od"], ax=ax[1])
    ax[1].set_title("Target OD counts")
    sns.heatmap(summary["od_mean"] - summary["tru_od"], ax=ax[2])
    ax[2].set_title("Difference (sim - target)")
    fig.tight_layout()
    save_show(fig, out_dir / "od_count_heatmaps.pdf", save=args.save, show=args.show,
              output_suffix=args.output_suffix)
