from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd


def _load_convergence_df(convergence_stats_fp: Path):
    """Parse convergenceStats.tsv into a DataFrame sorted by iteration, or None
    (with a warning) if the file is missing, unparsable, empty, or lacks the
    burnInIteration column."""
    if not convergence_stats_fp.exists():
        print(f"WARNING: {convergence_stats_fp} not found; falling back to fixed --window.")
        return None
    try:
        df = pd.read_csv(convergence_stats_fp, sep="\t")
    except Exception as exc:
        print(f"WARNING: could not parse {convergence_stats_fp} ({exc}); falling back to fixed --window.")
        return None
    if df.empty or "burnInIteration" not in df.columns:
        print(
            f"WARNING: {convergence_stats_fp} has no usable rows "
            f"(run may have terminated before the minimum sample count needed for a "
            f"convergence check); falling back to fixed --window."
        )
        return None
    return df.sort_values("iteration")


def burn_in_and_converged(convergence_stats_fp: Path) -> tuple[Optional[int], bool]:
    """Validated burn-in cut and convergence flag from convergenceStats.tsv
    (written by BlockAverageTerminationCriterion), in the same raw MCMC-iteration
    units as the `iteration` attribute in states.xml -- no unit conversion needed.

    The block criterion writes a `stabilized` flag per row and, on the row where
    it first declares stationarity, the *validated* burnInIteration; on
    non-stabilized rows burnInIteration is only a rolling 50%-of-chain
    placeholder. MinimumSampledCriterion latches on that first stabilized row and
    terminates minStationaryIntervals samples later, so the run's own cut is the
    FIRST stabilized=true row -- not the last row, which after post-latch drift
    of the noisy block test is often stabilized=false again.

    Returns (burn_in, converged):
      - first stabilized=true row -> (its burnInIteration, True). If the last row
        is stabilized=false (post-latch drift), an info note is logged; the latch
        still stands.
      - no stabilized=true row anywhere -> (last row's placeholder burnInIteration,
        False) with a warning, or (None, False) if that value is NaN.
      - unusable file -> (None, False).
    """
    df = _load_convergence_df(convergence_stats_fp)
    if df is None:
        return None, False

    stabilized = df["stabilized"].astype(str).str.strip().str.lower() == "true"
    stabilized_rows = df[stabilized & df["burnInIteration"].notna()]
    if len(stabilized_rows) > 0:
        latch = stabilized_rows.iloc[0]  # first stabilized row = the latch
        burn_in = int(latch["burnInIteration"])
        last_stabilized = str(df.iloc[-1].get("stabilized", "")).strip().lower() == "true"
        if not last_stabilized:
            print(
                f"INFO: {convergence_stats_fp.parent} stabilized at iteration "
                f"{int(latch['iteration'])} (burnInIteration={burn_in}) but was not stable at "
                f"its final row (post-latch drift of the block test); latched cut retained."
            )
        return burn_in, True

    # No validated stabilization anywhere -> fall back to the last row's guess.
    burn_in = df.iloc[-1]["burnInIteration"]
    if pd.isna(burn_in):
        print(f"WARNING: last row of {convergence_stats_fp} has no burnInIteration; falling back to fixed --window.")
        return None, False
    print(
        f"WARNING: {convergence_stats_fp.parent} has no stabilized row "
        f"(never reached stationarity); using best-guess burnInIteration={int(burn_in)} anyway."
    )
    return int(burn_in), False


def read_burn_in_iteration(convergence_stats_fp: Path) -> Optional[int]:
    """Back-compat wrapper returning just the validated burn-in cut (or None).
    See burn_in_and_converged for the full semantics."""
    burn_in, _ = burn_in_and_converged(convergence_stats_fp)
    return burn_in


def post_burn_in_iterations(
    all_iterations: np.ndarray,
    convergence_stats_fp: Path,
    window: int,
) -> tuple[np.ndarray, bool]:
    """Iterations to aggregate over for a single partition, plus a `converged`
    flag.

    Returns (iterations, converged) where iterations is all logged iterations
    >= the burn-in cutoff if convergenceStats.tsv is usable and at least one
    such iteration exists, otherwise the last `window` logged iterations (the
    fixed-window fallback used elsewhere).

    `converged` is True only when the selection came from a validated (first
    stabilized=true) burn-in cut. It is False for the no-stabilized-row
    best-guess cut and for every fallback path (missing/unusable stats, or no
    logged iterations past the cut). Callers use it to warn when an
    under-converged partition drives the cross-partition sample count.
    """
    burn_in_iteration, converged = burn_in_and_converged(convergence_stats_fp)
    if burn_in_iteration is not None:
        post_burn_in = all_iterations[all_iterations >= burn_in_iteration]
        if len(post_burn_in) > 0:
            return post_burn_in, converged
        print(
            f"WARNING: no logged iterations >= burnInIteration={burn_in_iteration} "
            f"(msg-interval coarser than the remaining post-burn-in run?); "
            f"falling back to fixed --window={window}."
        )

    fallback = all_iterations[-window:] if len(all_iterations) >= window else all_iterations
    return fallback, False


def select_post_burn_in_or_window(
    all_iterations: np.ndarray,
    convergence_stats_fp: Path,
    window: int,
) -> np.ndarray:
    """Iterations to aggregate over: all logged iterations >= the burn-in cutoff
    if convergenceStats.tsv is usable and at least one such iteration exists,
    otherwise the last `window` logged iterations (same as the fixed-window
    behavior used elsewhere). Thin wrapper over post_burn_in_iterations for
    single-partition callers that do not need the convergence flag."""
    iterations, _ = post_burn_in_iterations(all_iterations, convergence_stats_fp, window)
    return iterations


def equalize_post_burn_in(
    arrays_by_label: dict[str, np.ndarray],
    converged_by_label: dict[str, bool],
) -> dict[str, set]:
    """Trim every partition's post-burn-in iteration array to a common count N
    so all partitions contribute the same number of samples to a
    cross-partition aggregate.

    N = min number of post-burn-in samples across partitions; each partition
    keeps its most recent N iterations (a[-N:]). N is naturally >= the run-time
    min_stationary_intervals for a fully converged run (the stopping criterion
    guarantees at least that many post-stationarity samples per partition and
    the burn-in cut is no later than the stationarity iteration); a partition
    that hit --maxIter without stabilizing, or that fell back to the fixed
    window, can drop N below that baseline, so we warn.

    Returns {label: set(kept_iterations)}. Empty input yields {}.
    """
    if not arrays_by_label:
        return {}

    counts = {label: len(a) for label, a in arrays_by_label.items()}
    n = min(counts.values())
    setter = min(counts, key=lambda label: counts[label])
    median_count = float(np.median(list(counts.values())))

    print(
        f"Equalizing post-burn-in samples across {len(counts)} partition(s) to "
        f"N={n} (set by partition '{setter}'); per-partition counts: "
        f"{ {label: counts[label] for label in sorted(counts)} }."
    )
    if not converged_by_label.get(setter, False):
        print(
            f"WARNING: partition '{setter}' sets the common sample count N={n} but has no "
            f"validated stabilization (no stabilized row in its convergenceStats.tsv); "
            f"the equalized sample count may be below min_stationary_intervals."
        )
    if median_count > 0 and n < 0.5 * median_count:
        print(
            f"WARNING: common sample count N={n} is well below the across-partition "
            f"median of {median_count:.0f}; partition '{setter}' is an outlier dragging "
            f"the analysis sample count down."
        )

    return {label: set(a[-n:].tolist()) for label, a in arrays_by_label.items()}
