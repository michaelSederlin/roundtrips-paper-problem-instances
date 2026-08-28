import gzip
from pathlib import Path

from matplotlib import pyplot as plt
import pandas as pd
import lxml.etree
import re
from ast import literal_eval
import numpy as np

def save_show(fig, path: Path, save: bool = True, show: bool = False,
              output_suffix: str = ".pdf"):
    if save:
        fig.savefig(path.with_suffix(output_suffix), dpi=300, bbox_inches="tight")
    if show:
        plt.show()
    plt.close(fig)


def loc2activity(loc):
    return re.sub(".*\[|\]", "", loc)


def parse_states_xml(xml_path: Path):
    xml_path = Path(xml_path)
    if xml_path.suffix == ".gz":
        with gzip.open(xml_path) as f:
            return lxml.etree.parse(f)
    return lxml.etree.parse(str(xml_path))


def stay_activity(stay):
    # Handles differently formatted states.xml between renate and amal.
    return (stay.get('activity')
            or (stay.get('labels') or '').strip('[]')
            or re.sub(r'.*\[|\]', '', stay.get('loc') or ''))


def departure_hours(rt, time_bin_size):
    """Departure hour-of-day of one RoundTrip, from <Departures> (calibration
    output) or, failing that, <State> bins[...] (merged/measurement files).
    The raw values are bin indices (hour = index * time_bin_size); convert
    before binning so the histogram is correct for any time_bin_size."""
    raw = rt.findtext("Departures") or ""
    if not raw.strip():
        m = re.search(r"bins\[(.*?)\]", rt.findtext("State") or "")
        raw = m.group(1) if m else ""
    idx = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", raw)]
    return [int(i * time_bin_size) % 24 for i in idx]

def process_states(states, separate_trips_and_stays:bool = True):
    if isinstance(states, (Path, str)):
        path = Path(states)
        if path.suffix == ".gz":
            import gzip
            with gzip.open(path) as z:
                states = lxml.etree.parse(z)
        else:
            states = lxml.etree.parse(str(path))

    data = []
    for mrt in states.findall("MultiRoundTrip"):
        for rt in mrt.findall("RoundTrip"):
            for e in rt.findall("Episodes/"):
                if e.tag == "MoveEpisode":
                    ii = 0
                data.append({
                    "type": e.tag,
                    'iteration': int(mrt.get("iteration")),
                    'index': rt.get("index"),
                    'start': float(e.get("start")), 
                    'end': float(e.get("end")), 
                    "loc": e.get("loc", ""),
                    "from": e.get("from", ""), 
                    "to": e.get("to", ""), 
                    "duration": float(e.get("duration", np.nan)),
                    # "coords": literal_eval(e.get("loc", "()")),
                    "activity": loc2activity(e.get("loc", ""))
                })
    columns = ["type", "iteration", "index", "start", "end", "loc",
               "from", "to", "duration", "activity", "basicName", "coords"]
    if not data:
        # No episodes parsed (e.g. a states file whose MultiRoundTrip has no
        # RoundTrips). Return a well-formed empty frame so downstream column
        # access does not raise KeyError.
        states_df = pd.DataFrame(columns=columns)
    else:
        states_df = pd.DataFrame(data)
        states_df['basicName'] = states_df['loc'].apply(lambda x: re.sub("\[.*", "", x))
        states_df['coords'] = states_df['basicName'].apply(lambda x: () if not x else literal_eval(x))
    # states_df['duration'] = states_df['end'] - states_df['start']

    if not separate_trips_and_stays:        
        return states_df
    else:
        trips = states_df[states_df["type"] == "MoveEpisode"]
        stays = states_df[states_df["type"] == "StayEpisode"]    
        return trips.drop(columns = ["loc", "activity", "coords"]), stays.drop(columns = ["from", "to"])