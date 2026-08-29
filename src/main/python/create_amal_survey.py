import gzip
import json
from pathlib import Path
import shutil
from lxml import etree

import pandas as pd
import logging

logger = logging.getLogger(__name__)

def decimal_to_hhmm(t: float) -> str:
    t = max(0.0, min(24.0, t))
    h = int(t) 
    m = int((t - h) * 60) 
    return f"{h:02d}{m:02d}"


def load_mrts(states_fp: Path, idx: None | int = None) -> list: 
    opener = gzip.open if states_fp.suffix == ".gz" else open
    with opener(states_fp, 'rb') as fid:
        states = etree.parse(fid)

    mrts = states.xpath("//MultiRoundTrip")
    if idx is not None: 
        return [mrts[idx]]
    else: 
        return mrts

def create_od_matrix(mrt: etree.Element, nodes: list) -> pd.DataFrame:
    def parse_node_names(*nodes): 
        out = []
        for n in nodes:
            out.append(n[:n.find(")") + 1])
        return out

    od_matrix = pd.DataFrame(0, index = nodes, columns = nodes)

    for rt in mrt.findall("RoundTrip"):
        mes = rt.xpath("Episodes/MoveEpisode")
        for me in mes:
            o, d = parse_node_names(me.get("from"), me.get("to"))
            od_matrix.loc[o, d] += 1
    return od_matrix

def get_basic_name(node):
    return node[:node.find(")") + 1]


def load_respondents(fp: Path, home_map: dict, n: int | None = None,
                     random_state: int | None = None) -> pd.DataFrame:

    logger.info("Loading respondents from %s (n=%s, random_state=%s)", fp, n, random_state)
    population = pd.read_csv(fp, sep="\t" if fp.suffix.lower() == ".tsv" else ",")
    logger.info("Loaded population: %d rows", len(population))
    population['home'] = population['idx'].astype(str).map(home_map)
    n_missing_home = population['home'].isna().sum()
    if n_missing_home:
        logger.warning("%d population rows have no matching home in home_map", n_missing_home)

    upbd = population.rename(columns={
        "idx": "UENR", 
        "age": "AGE",
        "income": "INKUP",
        "home": "BOST_S"
    })

    if n is not None:
        upbd = upbd.sample(n=min(n, len(upbd)), random_state=random_state).sort_values("UENR")
        logger.info("Sampled %d respondents", len(upbd))

    logger.info("Returning %d respondents", len(upbd))
    return upbd


def build_mddr(data_mrt) -> pd.DataFrame:
    logger.info("Building mddr from %d roundtrips", len(data_mrt.findall("RoundTrip")))
    rows = []
    for rt in data_mrt.findall("RoundTrip"):
        uenr = rt.get("index")
        eps = list(rt.findall('Episodes/'))

        for ii, me in enumerate(eps):
            if me.tag != 'MoveEpisode':
                continue

            dest = eps[ii+1] if ii < len(eps) - 1 else eps[0]
            # dest_activity = me.get("to")[me.get("to").find("[") + 1:-1]
            dest_activity = dest.get('labels').strip("[]").strip()
            d_b_s = get_basic_name(me.get("to"))
            d_a_s = get_basic_name(me.get("from"))
            t0, t1 = float(me.get("start")), float(me.get("end"))
            rows.append({
                "UENR": uenr, "D_A_S": d_a_s, "D_B_S": d_b_s,
                "D_A_KL": decimal_to_hhmm(t0), "D_B_KL": decimal_to_hhmm(t1),
                "D_RESTID": t1 - t0, "activity": dest_activity,
            })
    return pd.DataFrame(rows)


def build_home_map(data_mrt) -> dict:
    logger.info("Building home_map from %d roundtrips", len(data_mrt.findall("RoundTrip")))
    home_map = {}
    for rt in data_mrt.findall("RoundTrip"):
        ep0 = rt.findall("Episodes/StayEpisode")[0]
        home_map[rt.get("index")] = get_basic_name(ep0.get("loc"))
    return home_map


def write_survey(out_dir: Path, upbd: pd.DataFrame, mddr: pd.DataFrame):
    logger.info("Writing survey -> %s", out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    sub_mddr = mddr[mddr["UENR"].astype(str).isin(upbd["UENR"].astype(str))]
    upbd.to_csv(out_dir / "upbd.csv", index=False)
    sub_mddr.to_csv(out_dir / "mddr.csv", index=False)
    logger.info("Wrote survey (%d persons, %d trips) -> %s",
                len(upbd), len(sub_mddr), out_dir)


def write_filtered_states(data_mrt, selected_uenrs: set, output: Path):
    import gzip
    import copy
    from lxml import etree

    logger.info("Writing filtered states for %d selected respondents -> %s",
                len(selected_uenrs), output)
    root = etree.Element("MultiRoundTrips")
    mrt_out = etree.SubElement(root, "MultiRoundTrip", iteration=data_mrt.get("iteration"))

    sw = data_mrt.find("SamplingWeights")
    if sw is not None:
        mrt_out.append(copy.deepcopy(sw))

    for rt in data_mrt.findall("RoundTrip"):
        if rt.get("index") in selected_uenrs:
            mrt_out.append(copy.deepcopy(rt))

    out_path = output / "states.xml"
    # with gzip.open(out_path, 'wb') as f:
    with open(out_path, 'wb') as f:
        etree.ElementTree(root).write(f, encoding="utf-8", xml_declaration=True, pretty_print=True)
    logger.info("Wrote filtered states (%d respondents) -> %s", len(selected_uenrs), out_path)


def _parse_bracketed_list(text: str) -> list:
    # "[(1,4)[HOME], (1,3)[WORK]]" -> ["(1,4)[HOME]", "(1,3)[WORK]"]
    inner = text.strip()[1:-1].strip()
    return [p.strip() for p in inner.split(", ")] if inner else []


def write_warm_start(data_mrt, selected_uenrs: set, output: Path):
    """Write a global warm-start state for --warm-start-file.

    The runner slices this by position (getRoundTrip(partFrom + j)), so the round
    trips must be indexed 0..n-1 in the same order as the respondents in upbd.csv.
    """
    logger.info("Writing warm start state -> %s", output)
    states = []
    for rt in data_mrt.findall("RoundTrip"):
        if rt.get("index") not in selected_uenrs:
            continue
        states.append({
            "index": len(states),
            "nodes": _parse_bracketed_list(rt.find("Nodes").text),
            "dptBins": [int(d) for d in _parse_bracketed_list(rt.find("Departures").text)],
        })

    out_path = output / "finalState.json"
    with open(out_path, "w") as fid:
        json.dump(states, fid, indent=2)
    logger.info("Wrote warm start state (%d roundtrips) -> %s", len(states), out_path)

def main(input: Path, output: Path, states_xml_filename: str, population_filename: str):
    output.mkdir(parents=True, exist_ok=True)
    logger.info("Input dir: %s", input)
    logger.info("Output dir: %s", output)

    states_xml = input / "partitions_merged" / states_xml_filename

    logger.info("Loading MultiRoundTrip (idx=-1) from %s", states_xml.name)
    data_mrt = load_mrts(states_xml, idx=-1)[0]
    n_rt = len(data_mrt.findall("RoundTrip"))
    logger.info("Loaded MultiRoundTrip with %d RoundTrips", n_rt)

    network = pd.read_csv(input / "nodes.tsv", sep="\t")
    logger.info("Loaded network: %d nodes, %d unique basicNames",
                len(network), network.basicName.nunique())

    od_matrix = create_od_matrix(data_mrt, network.basicName.unique())
    od_matrix.index.name = ""
    od_matrix.to_csv(output / "od_matrix.tsv", sep=";")
    logger.info("Wrote OD matrix (%dx%d, total trips=%d) -> %s",
                od_matrix.shape[0], od_matrix.shape[1],
                int(od_matrix.values.sum()), output / "od_matrix.tsv")

    home_map = build_home_map(data_mrt)
    logger.info("Built home_map for %d roundtrips", len(home_map))

    mddr = build_mddr(data_mrt)
    logger.info("Built mddr (%d move episodes across %d roundtrips)",
                len(mddr), mddr['UENR'].nunique() if len(mddr) else 0)

    # Full survey
    upbd_full = load_respondents(input / "partitions_merged/" / population_filename, home_map)
    upbd_save = upbd_full

    # upbd_save = upbd_full.sample(n = 400, random_state = 1084)
    write_survey(output, upbd_save, mddr)
    write_filtered_states(data_mrt, set(upbd_save["UENR"]), output)
    write_warm_start(data_mrt, set(upbd_save["UENR"]), output)

    shutil.copyfile(input / "nodes.tsv", output / "nodes.tsv")
    shutil.copyfile(input / "edges.tsv", output / "edges.tsv")

    logger.info("Loading opportunity_weights from %s", input / "opportunity_weights.csv")
    oppo_weights = pd.read_csv(input / "opportunity_weights.csv",
                            dtype={"samscode": str, "ANDAMAL": str,
                                    "area_share": float, "area": int})

    oppo_weights.to_csv(output / "opportunity_weights.csv", index=False)
    logger.info("Wrote opportunity_weights (%d rows) -> %s",
                len(oppo_weights), output / "opportunity_weights.csv")


if __name__ == "__main__":
    main(
        input = Path("data/measurement_data"), 
        output = Path("data/survey"), 
        states_xml_filename = "final_states_merged.xml", 
        population_filename = "population_merged.tsv"
    )
   