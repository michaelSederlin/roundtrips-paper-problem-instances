# Author: Michael Sederlin

# Takes states.xml and generates a survey with travel plans from these individuals.
# input = root / "input_amal_estimation" / "output" / "2026 02 01_ServerRuns" / "004" / "20E6_5x5_1000" / "output"

# Needs documentation 

import json
from pathlib import Path
import shutil

from renate import get_environment_root
from renate.utils.helpers import decimal_to_hhmm
from renate.data.utils import load_mrts, create_od_matrix
import pandas as pd
from renate import setup_logging
import logging

setup_logging()
logger = logging.getLogger(__name__)

def get_basic_name(node):
    return node[:node.find(")") + 1]


def load_respondents(fp: Path, home_map: dict, n: int | None = None,
                     random_state: int | None = None, gender_mapping: None|dict = None) -> pd.DataFrame:

    logger.info("Loading respondents from %s (n=%s, random_state=%s)", fp, n, random_state)
    population = pd.read_csv(fp)
    logger.info("Loaded population: %d rows", len(population))
    population['home'] = population['idx'].astype(str).map(home_map)
    n_missing_home = population['home'].isna().sum()
    if n_missing_home:
        logger.warning("%d population rows have no matching home in home_map", n_missing_home)

    upbd = population.rename(columns={
        "idx": "UENR", 
        "gender": "SEX", 
        "age": "AGE",
        "income": "INKUP",
        "home": "BOST_S"
    })

    if n is not None:
        upbd = upbd.sample(n=min(n, len(upbd)), random_state=random_state).sort_values("UENR")
        logger.info("Sampled %d respondents", len(upbd))

    if gender_mapping is not None and not pd.api.types.is_numeric_dtype(upbd['SEX']):
        upbd['SEX'] = upbd['SEX'].map(gender_mapping)
        logger.info("Applied gender_mapping to SEX column")
    logger.info("Returning %d respondents", len(upbd))
    return upbd


def build_mddr(data_mrt) -> pd.DataFrame:
    logger.info("Building mddr from %d roundtrips", len(data_mrt.findall("RoundTrip")))
    rows = []
    for rt in data_mrt.findall("RoundTrip"):
        uenr = rt.get("index")
        for me in rt.findall("Episodes/MoveEpisode"):
            dest_activity = me.get("to")[me.get("to").find("[") + 1:-1]
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
    sub_mddr = mddr[mddr["UENR"].astype(int).isin(upbd["UENR"].astype(int))]
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
        if int(rt.get("index")) in selected_uenrs:
            mrt_out.append(copy.deepcopy(rt))

    out_path = output / "states.xml.gz"
    with gzip.open(out_path, 'wb') as f:
        etree.ElementTree(root).write(f, encoding="utf-8", xml_declaration=True, pretty_print=True)
    logger.info("Wrote filtered states (%d respondents) -> %s", len(selected_uenrs), out_path)
    
DEFAULT_INPUT = Path("amal/Results_Paper/2026 08 07/amal_measurement/out/ProductionRun_1000")
DEFAULT_OUTPUT = Path("renate/data/scenarios/amal/results_paper/2026 08 07/full")


def parse_args(argv=None):
    import argparse

    parser = argparse.ArgumentParser(
        description="Generate a survey (upbd/mddr) from simulated states.xml.")
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_INPUT,
                        help="Run directory containing partitions_merged/, nodes.tsv, edges.tsv "
                             "and opportunity_weights.csv (default: %(default)s)")
    parser.add_argument("-o", "--output", type=Path, default=DEFAULT_OUTPUT,
                        help="Directory to write the survey into (default: %(default)s)")
    parser.add_argument("-n", "--n-respondents", type=int, default=None,
                        help="If given, sample this many respondents instead of using all.")
    parser.add_argument("--random-state", type=int, default=1084,
                        help="Seed used when --n-respondents is given (default: %(default)s)")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    root = get_environment_root()
    input = args.input
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    logger.info("Input dir: %s", input)
    logger.info("Output dir: %s", output)

    # candidates = list(input.glob("states.xml"))
    # if not candidates:
    #     logger.error("No states file found in %s", input)
    #     raise FileNotFoundError(f"No states file found")
    # logger.info("Found %d states file(s); using %s", len(candidates), candidates[0].name)
    states_xml = input / "partitions_merged/_final_states_merged.xml"
    # states_xml = input / "0/states.xml.gz"

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
    logger.info("Loading gender_mapping from %s", root / 'renate/data/metadata/gender_mapping.json')
    with open(root / 'renate/data/metadata/gender_mapping.json', 'r') as fid:
        gender_mapping = json.load(fid)
    logger.info("Loaded gender_mapping with %d entries", len(gender_mapping))

    upbd_full = load_respondents(input / "partitions_merged/population_meas_merged.csv", home_map, gender_mapping = gender_mapping)
    upbd_save = upbd_full

    # upbd_save = upbd_full.sample(n = 400, random_state = 1084)
    write_survey(output, upbd_save, mddr)
    write_filtered_states(data_mrt, set(upbd_save["UENR"]), output)

    logger.info("Copying nodes")
    shutil.copyfile(input / "nodes.tsv", output / "nodes.tsv")

    logger.info("Copying edges")
    shutil.copyfile(input / "edges.tsv", output / "edges.tsv")

    # write_survey(output, upbd_full, mddr)
    # # Subsets
    # SUBSET_SIZES = [50, 100, 200]
    # SUBSET_SEED = 1084
    # for n in SUBSET_SIZES:
    #     upbd_sub = load_respondents(input / "partitions_merged/population.csv", home_map,
    #                                 n=n, random_state=SUBSET_SEED)
    #     write_survey(output / f"subset_n{n}_seed{SUBSET_SEED}", upbd_sub, mddr)

    logger.info("Loading opportunity_weights from %s", input / "opportunity_weights.csv")
    oppo_weights = pd.read_csv(input / "opportunity_weights.csv",
                            dtype={"samscode": str, "ANDAMAL": str,
                                    "area_share": float, "area": int})

    oppo_weights.to_csv(output / "opportunity_weights.csv", index=False)
    logger.info("Wrote opportunity_weights (%d rows) -> %s",
                len(oppo_weights), output / "opportunity_weights.csv")
