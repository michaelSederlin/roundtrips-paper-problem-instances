"""Age-binned per-activity reproduction: mean activity duration and activity
participation per age band, estimation vs measurement/validation ground truth.

Figure sizes, font sizes, bar colors/width and legend ordering all come from
common_parameters, so the paper figures can be retuned in one place.
"""
import gzip
import re
from ast import literal_eval
from collections import defaultdict
from pathlib import Path

import lxml.etree
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from .helpers import ArgumentParser
from .plotting_utilities import (stay_activity,
                                 parse_states_xml as _parse_states_xml)
from .helpers.od_transition_helpers import save_show
from .common_parameters import (
    barplot_size, 
    bar_width, 
    bar_alpha, 
    bar_edgecolor, 
    ESTIMATION_COLOR, 
    VALIDATION_COLOR, 
    MEASUREMENT_COLOR,
    sort_legend_entries, 
    format_categorical_axis
)

ACTIVITIES = ["HOME", "WORK", "EDUCATION", "OTHER"]
NON_HOME_ACTIVITIES = [a for a in ACTIVITIES if a != "HOME"]
AGE_BINS = np.arange(15, 91, 5)
INC_BINS = np.arange(0, 500, 50)


def parse_states(root, last=False):
    """Per (iteration, RoundTrip): activity set, nstops and per-activity durations."""
    rows = []
    mrts = root.findall('.//MultiRoundTrip')
    if last:
        mrts = mrts[-1:]

    for mrt in mrts:
        it = int(mrt.get('iteration'))
        for rt in mrt.findall('./RoundTrip'):
            nodes = rt.findtext('Nodes')
            if nodes is None:
                m = re.search(r'nodes\[(.*?)\],bins', rt.findtext('State') or '')
                nodes = m.group(1) if m else ''
            idx = rt.get('index')

            per_act = defaultdict(float)
            for stay in rt.findall('.//StayEpisode'):
                per_act[stay_activity(stay)] += float(stay.get('end')) - float(stay.get('start'))

            acts = re.findall(r'\[([A-Z]+)\]', nodes)
            row = {'iteration': it, 'idx': idx, 'nstops': len(acts)}
            for a in ACTIVITIES:
                row[a] = per_act.get(a, 0.0)                 # marginal duration (0 when absent)
                row[f'{a}_part'] = float(a in per_act)       # participation indicator
            rows.append(row)

    return pd.DataFrame(rows)

def plot_bars(est, meas, val, index, xlabel, ylabel, xticklabels, path,
              save: bool = True, show: bool = False, output_suffix: str = ".pdf"):
    """Shared bar styling from common_parameters."""
    x = np.arange(len(index))
    fig, ax = plt.subplots(figsize=barplot_size)
    ax.bar(x + bar_width, est.reindex(index, fill_value=0), width=bar_width,
           color=ESTIMATION_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
           label="Calibration")
    if val is not None:
        ax.bar(x, val.reindex(index, fill_value=0), width=bar_width,
               color=VALIDATION_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
               label="Validation Data Set")
    ax.bar(x - bar_width, meas.reindex(index, fill_value=0), width=bar_width,
           color=MEASUREMENT_COLOR, alpha=bar_alpha, edgecolor=bar_edgecolor,
           label="Measurement Data Set")
    format_categorical_axis(ax, xlabel, ylabel, xticks=x, xticklabels=xticklabels)
    sort_legend_entries(ax)
    save_show(fig, path, save=save, show=show, output_suffix=output_suffix)

def _by_age_bin(df, cols: str | list[str], age_by_name, bins):
    """Mean of `col` per age bin, after averaging over iterations per agent."""
    if isinstance(cols, str):
        cols = [cols]
    per_agent = df.groupby('name', as_index=False)[cols].mean()
    per_agent['age'] = per_agent['name'].map(age_by_name)
    per_agent['age_bin'] = pd.cut(per_agent['age'], bins)
    return per_agent.groupby('age_bin', observed=False)[cols].mean().sum(axis = 1)

def _by_inc_bin(df, cols, inc_by_name, bins):
    """Mean of `col` per age bin, after averaging over iterations per agent."""
    if isinstance(cols, str):
        cols = [cols]
    per_agent = df.groupby('name', as_index=False)[cols].mean()
    per_agent['inc'] = per_agent['name'].map(inc_by_name)
    per_agent['inc_bin'] = pd.cut(per_agent['inc'], bins)
    return per_agent.groupby('inc_bin', observed=False)[cols].mean().sum(axis = 1)

def _by_gender(df, cols, gender_by_name):
    if isinstance(cols, str): 
        cols = [cols]
    per_agent = df.groupby('name', as_index = False)[cols].mean()
    per_agent['gender'] = per_agent['name'].map(gender_by_name)
    return per_agent.groupby('gender', observed=False)[cols].mean().sum(axis = 1)


def main(results_dir: Path,
         measurement_states_xml: Path,
         save_dir: Path = None,
         validation_states_xml: Path = None,
         save: bool = True,
         show: bool = False,
         output_suffix: str = ".pdf"):
    if measurement_states_xml is None:
        raise ValueError("measurement_states_xml is required")

    results_dir = Path(results_dir)
    save_dir = results_dir if save_dir is None else Path(save_dir)
    save_kwds = dict(save=save, show=show, output_suffix=output_suffix)

    merged = results_dir / 'partitions_merged'
    pop = pd.read_csv(merged / 'population_merged.tsv', sep='\t')
    pop['acts'] = pop['legs'].apply(lambda l: {d['destinationActivity'] for d in literal_eval(l)})
    idx2name = pop.set_index(pop['idx'].astype(str))['name'].to_dict()

    age_by_name = pop.set_index('name')['age'].to_dict()
    inc_by_name = pop.set_index('name')['inc'].to_dict()
    gender_by_name = pop.set_index('name')['gender'].to_dict()

    est = parse_states(_parse_states_xml(merged / 'final_states_merged.xml').getroot())
    est['name'] = est['idx'].astype(str).map(idx2name)

    meas = parse_states(_parse_states_xml(measurement_states_xml).getroot(), last=True)
    meas['name'] = meas['idx']

    val = None
    if validation_states_xml is not None:
        val = parse_states(_parse_states_xml(validation_states_xml).getroot(), last=True)
        val['name'] = val['idx']

    cats = pd.IntervalIndex.from_breaks(AGE_BINS)
    inc_cats = pd.IntervalIndex.from_breaks(INC_BINS)
    ticklabels = [f"{int(c.left)}" if i % 2 == 0 else "" for i, c in enumerate(cats)]
    inc_ticklabels = [f"{int(c.left)}" if i % 2 == 0 else "" for i, c in enumerate(inc_cats)]

    gender_cats = pd.Index(sorted(pop['gender'].dropna().unique()))
    gender_ticklabels = gender_cats.astype(str).tolist()    

    for act in ACTIVITIES:
        # mean marginal duration per age bin
        plot_bars(_by_age_bin(est, act, age_by_name, AGE_BINS),
                  _by_age_bin(meas, act, age_by_name, AGE_BINS),
                  _by_age_bin(val, act, age_by_name, AGE_BINS) if val is not None else None,
                  cats, "Age", f"{act} Duration [h]", ticklabels,
                  save_dir / f"08_age_duration_{act}.pdf", **save_kwds)
        

        # participation rate per age bin
        plot_bars(_by_age_bin(est, f'{act}_part', age_by_name, AGE_BINS) * 100,
                  _by_age_bin(meas, f'{act}_part', age_by_name, AGE_BINS) * 100,
                  (_by_age_bin(val, f'{act}_part', age_by_name, AGE_BINS) * 100)
                  if val is not None else None,
                  cats, "Age", f"{act} Participation [%]", ticklabels,
                  save_dir / f"08_age_participation_{act}.pdf", **save_kwds)

        plot_bars(_by_inc_bin(est, act, inc_by_name, INC_BINS), 
                  _by_inc_bin(meas, act, inc_by_name, INC_BINS),
                  _by_inc_bin(val, act, inc_by_name, INC_BINS)
                    if val is not None else None, 
                    inc_cats, "Income [ksek]", f"{act} Duration [h]", inc_ticklabels,
                    save_dir / f"09_inc_duration_{act}.pdf", **save_kwds)
        
        plot_bars(_by_inc_bin(est, f'{act}_part', inc_by_name, INC_BINS) * 100, 
                  _by_inc_bin(meas, f'{act}_part', inc_by_name, INC_BINS) * 100,
                  _by_inc_bin(val, f'{act}_part', inc_by_name, INC_BINS) * 100
                    if val is not None else None, 
                    inc_cats, "Income [ksek]", f"{act} Participation [%]", inc_ticklabels,
                    save_dir / f"09_inc_participation_{act}.pdf", **save_kwds)

        plot_bars(_by_gender(est, act, gender_by_name), 
                  _by_gender(meas, act, gender_by_name),
                  _by_gender(val, act, gender_by_name)
                        if val is not None else None, 
                    gender_cats, "Gender", f"{act} Duration [h]", gender_ticklabels,
                    save_dir / f"10_gender_duration_{act}.pdf", **save_kwds)

    # total non-HOME duration per age bin
    plot_bars(_by_age_bin(est, NON_HOME_ACTIVITIES, age_by_name, AGE_BINS),
              _by_age_bin(meas, NON_HOME_ACTIVITIES, age_by_name, AGE_BINS),
              _by_age_bin(val, NON_HOME_ACTIVITIES, age_by_name, AGE_BINS) if val is not None else None,
              cats, "Age", "Away-from-Home Duration [h]", ticklabels,
              save_dir / "08_age_duration_away_from_home.pdf", **save_kwds)


    # total non-HOME duration per age bin
    plot_bars(_by_inc_bin(est, NON_HOME_ACTIVITIES, inc_by_name, INC_BINS),
            _by_inc_bin(meas, NON_HOME_ACTIVITIES, inc_by_name, INC_BINS),
            _by_inc_bin(val, NON_HOME_ACTIVITIES, inc_by_name, INC_BINS) if val is not None else None,
            inc_cats, "Income [ksek]", "Away-from-Home Duration [h]", inc_ticklabels,  
            save_dir / "09_inc_duration_away_from_home.pdf", **save_kwds)

    plot_bars(_by_gender(est, NON_HOME_ACTIVITIES, gender_by_name), 
                _by_gender(meas, NON_HOME_ACTIVITIES, gender_by_name),
                _by_gender(val, NON_HOME_ACTIVITIES, gender_by_name)
                    if val is not None else None, 
                gender_cats, "Gender", "Away-from-Home Duration [h]", gender_ticklabels,
                save_dir / f"10_gender_duration_away_from_home.pdf", **save_kwds)
    
    # nStops per age bin
    plot_bars(_by_age_bin(est, 'nstops', age_by_name, AGE_BINS),
              _by_age_bin(meas, 'nstops', age_by_name, AGE_BINS),
              _by_age_bin(val, 'nstops', age_by_name, AGE_BINS) if val is not None else None,
              cats, "Age", "Number of Stops", ticklabels,
              save_dir / "08_age_nStops.pdf", **save_kwds)

    print(f"Done. Age-binned reproduction plots written to: {save_dir}")


if __name__ == "__main__":
    parser = ArgumentParser()
    args = parser.parse()
    main(results_dir=args.results_dir,
         measurement_states_xml=args.measurement_states_xml,
         save_dir=args.save_dir,
         validation_states_xml=args.validation_states_xml,
         save=args.save,
         show=args.show,
         output_suffix=args.output_suffix)
