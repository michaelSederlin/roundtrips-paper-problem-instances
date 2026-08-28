from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt
# from input_amal_estimation.src.main.python.plotting.argument_parser import ArgumentParser

ACTIVITIES = ["HOME", "WORK", "EDUCATION", "OTHER"]

import pandas as pd 
import re 
import numpy as np 
from time import sleep

from .merge_population_csv import _delimiter
from .plotting_utilities import process_states


def safe_row_normalize(df):
    s = df.sum(axis=1)
    s_safe = s.replace(0, 1.0)
    return df.div(s_safe, axis=0)

def get_last_n_iterations(df, n, anchor_index=None, include_anchor=True):
    df = df.sort_index(level="iteration")
    iters = df.index.get_level_values("iteration").unique()
    if len(iters) == 0:
        return df.iloc[0:0]
    take = min(len(iters), n + 1)
    picked_iters = iters[-take:]
    return df.loc[pd.IndexSlice[picked_iters, :]]

### Main-Method
def main(results_dir: Path, save_dir: Path, states_xml: None | Path = None, population_csv: None | Path = None, window: int = 10, save: bool = True, show: bool = False):

    if results_dir is None:
        results_dir = Path("./output/ProductionRun_N1000M1000/0/")
    if save_dir is None:
        save_dir = Path("./output/ProductionRun_N1000M1000/0/plots/")
    # if args.show is False:
    #     args.show = True
    if population_csv is None:
        population_csv = results_dir / "population.csv"
    if states_xml is None:
        states_xml = results_dir / "states.xml.gz"


    # Read population.csv
    # BUGFIX: Changed "idx" to str type to handle mixed format strings/ints and drop inner headers safely.
    # Added converters for age to tolerate string header lines using pd.to_numeric logic.
    df = pd.read_csv(
        population_csv,
        sep=_delimiter(population_csv),
        dtype={"idx": str, "gender": str},
        converters={
            "age": lambda x: int(pd.to_numeric(x, errors='coerce') or 0),
            "income": lambda x: float(pd.to_numeric(x, errors='coerce') or 0.0)
        }
    )

    # BUGFIX: Drops inner repeated header rows if CSV files were concatenated directly together.
    df = df[df["idx"] != "idx"]

    target_cols_map = {
        "home": "HOME",
        "work": "WORK",
        "education": "EDUCATION",
        "other": "OTHER",
    }

    df_target_base = (
        df[["idx"] + list(target_cols_map.keys())]
        .rename(columns=target_cols_map)
        .set_index("idx")[ACTIVITIES]
    )


    df_target_base = safe_row_normalize(df_target_base)

    # Get stay episodes from states.xml
    _, stays = process_states(states_xml, separate_trips_and_stays=True)

    # BUGFIX: Cast the XML "index" column to a string to reliably match string/integer index formats.
    stays["index"] = stays["index"].astype(str)
    if stays["activity"].dtype == object:
        stays["activity"] = stays["activity"].str.upper()

    stays["duration"] = stays["end"] - stays["start"]
    total_duration = (
        stays.groupby(["iteration", "index", "activity"])["duration"]
        .sum()
        .reset_index()
    )

    df_sample = (
        total_duration
        .pivot(index=("iteration", "index"), columns="activity", values="duration")
        .reindex(columns=ACTIVITIES)
        .fillna(0)
    )
    df_sample = safe_row_normalize(df_sample)

    def align_target(sample):
        # BUGFIX: Explicitly convert the sample index slice values to string objects to prevent reindex alignment failures.
        idxs = sample.index.get_level_values("index").astype(str)
        target = df_target_base.reindex(idxs)
        target.index = sample.index
        target = target.fillna(0)
        return safe_row_normalize(target)

    df_diff = df_sample.sub(align_target(df_sample), axis=0)

    # last n iterations
    df_sample_window = get_last_n_iterations(df_sample, n=window)
    df_target_window = align_target(df_sample_window)

    df_sample_mean = df_sample_window.groupby(level="index").mean()
    df_target_mean = df_target_window.groupby(level="index").mean()

    # Plotting
    fig_prefs, axes_prefs = plt.subplots(2, 2, figsize=(12, 10))
    fig_lines, axes_lines = plt.subplots(2, 2, figsize=(12, 10))

    iters = df_sample_window.index.get_level_values("iteration").unique()
    for i, act in enumerate(ACTIVITIES):
        # Line diffs
        ax_lines = axes_lines.flat[i]
        if act in df_diff.columns:
            wide = df_diff[act].unstack(level="index")
            ax_lines.plot(wide.index, wide, alpha=.2, color='gray')
            ax_lines.plot(wide.index, wide.mean(axis=1), color='red', linewidth=2)
        ax_lines.axhline(0, color='black')
        ax_lines.set(title=act)

        # Scatter Sample vs Target
        ax_prefs = axes_prefs.flat[i]
        for it in iters:
            x = df_target_window.loc[(it, slice(None)), act]
            y = df_sample_window.loc[(it, slice(None)), act]
            mask = x.notna() & y.notna()
            if mask.any():
                ax_prefs.scatter(x[mask], y[mask], alpha=0.1)
        # Mean
        xm = df_target_mean[act]
        ym = df_sample_mean[act]
        maskm = xm.notna() & ym.notna()
        if maskm.any():
            ax_prefs.scatter(xm[maskm], ym[maskm], marker='x', color='black')
        ax_prefs.set(
            xlabel="Target activity shares",
            ylabel="Sampled activity shares",
            title=f"{act}: Sample vs. Target",
            xlim=(0, .6), ylim=(0, .6)
        )
        ax_prefs.grid(True)
        # print(len(xm))

    plt.tight_layout()

    if save:
        save_dir = Path(save_dir)
        save_dir.mkdir(parents=True, exist_ok=True)
        fig_prefs.savefig(save_dir / "preference_matching.pdf", dpi=300, bbox_inches='tight')
        fig_lines.savefig(save_dir / "preference_diff_lines.pdf", dpi=300, bbox_inches='tight')
    if show:
        plt.show()

    return fig_prefs


if __name__ == "__main__":
    from .argument_parser import ArgumentParser
    parser = ArgumentParser()
    args = parser.parse()
    main(results_dir=args.results_dir,
         save_dir=args.save_dir,
         states_xml=args.states_xml,
         population_csv=args.population_csv,
         window=args.window,
         save=args.save,
         show=args.show)
