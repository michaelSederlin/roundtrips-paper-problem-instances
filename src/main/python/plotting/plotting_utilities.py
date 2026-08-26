from pathlib import Path

from matplotlib import pyplot as plt
import pandas as pd
import lxml.etree
import re
from ast import literal_eval
import numpy as np

def save_show(fig, path: Path, args):
    if args.save:
        fig.savefig(path.with_suffix(args.output_suffix), dpi=300, bbox_inches="tight")
    if args.show:
        plt.show()
    plt.close(fig)


def loc2activity(loc):
    return re.sub(".*\[|\]", "", loc)

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