"""
Converts a states.xml written in the current (RENATE) format into the older AMAL format,
so readers written against the old format can consume current synthesis calibration.

The two formats differ in three places:

  1. Round trip state
     new: <Nodes>[(1,4)[HOME], (1,3)[WORK]]</Nodes> + <Departures>[6, 15]</Departures>
     old: <State>index=0,nodes[(1,4)[HOME],(1,3)[WORK]],bins[6,15]</State>
  2. Stay episode activity
     new: labels="[WORK]"   old: activity="WORK"
  3. Move episode endpoints
     new: from="(1,3)"      old: from="(1,3)[WORK]"   (labelled)
"""

import xml.etree.ElementTree as ET
from pathlib import Path

from .plotting.merge_states_xml import _load_states_tree


def _parse_bracketed_list(text: str) -> list:
    # "[(1,4)[HOME], (1,3)[WORK]]" -> ["(1,4)[HOME]", "(1,3)[WORK]"]
    inner = text.strip()[1:-1].strip()
    return [p.strip() for p in inner.split(", ")] if inner else []


def _local_index(round_trip_index: str) -> str:
    # The old State line carries the partition-local index, not the global "0_0" key.
    return round_trip_index.split("_")[-1]


def _convert_round_trip(rt: ET.Element) -> ET.Element:
    out = ET.Element("RoundTrip", index=rt.get("index"))

    nodes = _parse_bracketed_list(rt.find("Nodes").text)
    bins = _parse_bracketed_list(rt.find("Departures").text)
    state = ET.SubElement(out, "State")
    state.text = "index={},nodes[{}],bins[{}]".format(
        _local_index(rt.get("index")), ",".join(nodes), ",".join(bins))

    eps = list(rt.find("Episodes"))
    episodes_out = ET.SubElement(out, "Episodes")
    for ii, ep in enumerate(eps):
        attrib = dict(ep.attrib)
        if ep.tag == "StayEpisode":
            attrib["activity"] = attrib.pop("labels").strip("[]").strip()
        elif ep.tag == "MoveEpisode":
            # Episodes alternate Stay/Move, so the surrounding stays carry the labelled
            # nodes. A bare node cannot be looked up in <Nodes> instead -- the same node
            # may appear there under more than one activity. The final move returns home
            # and wraps around to the first stay.
            attrib["from"] = eps[ii - 1].get("loc")
            attrib["to"] = eps[ii + 1].get("loc") if ii + 1 < len(eps) else eps[0].get("loc")
        ET.SubElement(episodes_out, ep.tag, attrib)

    return out


def convert_states_to_amal(input_fp: Path, output_fp: Path) -> Path:
    """Reads a new-format states file and writes it out in the old AMAL format."""
    input_fp, output_fp = Path(input_fp), Path(output_fp)
    tree = _load_states_tree(input_fp)

    root = ET.Element("MultiRoundTrips")
    for mrt in tree.getroot().findall("MultiRoundTrip"):
        mrt_out = ET.SubElement(root, "MultiRoundTrip", iteration=mrt.get("iteration"))
        for rt in mrt.findall("RoundTrip"):
            mrt_out.append(_convert_round_trip(rt))

    ET.indent(root)
    ET.ElementTree(root).write(output_fp, encoding="utf-8", xml_declaration=True)
    return output_fp

if __name__ == "__main__":
    from argparse import ArgumentParser
    parser = ArgumentParser() 
    parser.add_argument("--input", type=Path)
    parser.add_argument("--calibration", type=Path)
    args = parser.parse_args()
    convert_states_to_amal(args.input, args.output)