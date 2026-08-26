import csv
import gzip
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

from ddsutils.merge_population_csv import _delimiter
from ddsutils.plotting.helpers import ArgumentParser
from ddsutils.plotting.helpers.burn_in import read_burn_in_iteration


def _load_states_tree(xml_path):
    if str(xml_path).endswith(".gz"):
        with gzip.open(xml_path, "rt", encoding="utf-8") as f:
            return ET.parse(f)
    return ET.parse(xml_path)


def _find_states_xml(partition_dir):
    xml_path = partition_dir / "states.xml"
    if not xml_path.exists():
        xml_path = partition_dir / "states.xml.gz"
    return xml_path if xml_path.exists() else None


def _load_local_to_global_index(population_path, population_filename):
    delimiter = _delimiter(population_filename)
    with open(population_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=delimiter)
        return {i: int(row["idx"]) for i, row in enumerate(reader)}


def _partition_post_burn_in_count(xml_path, convergence_stats_fp):
    burn_in_iteration = read_burn_in_iteration(convergence_stats_fp)
    if burn_in_iteration is None:
        return None
    tree = _load_states_tree(xml_path)
    iterations = np.array(
        [int(mrt.get("iteration")) for mrt in tree.getroot().findall(".//MultiRoundTrip")]
    )
    return int((iterations >= burn_in_iteration).sum())


def _effective_window(partition_dirs, args):
    """Number of most-recent logged iterations to merge per partition. Uses the
    minimum post-burn-in iteration count across partitions (so every partition
    contributes equally to each merged relative position) when every partition's
    convergenceStats.tsv is usable, otherwise falls back to the fixed --window."""
    counts = []
    for partition_dir in partition_dirs:
        xml_path = _find_states_xml(partition_dir)
        if xml_path is None:
            counts.append(None)
            continue
        counts.append(_partition_post_burn_in_count(
            xml_path, partition_dir / args.convergence_stats_filename))

    if any(c is None for c in counts):
        print("WARNING: burn-in unusable for at least one partition; "
              f"falling back to fixed --window={args.window}.")
        return args.window

    return max(1, min(counts))


def main(args):
    base_dir = Path(args.results_dir)
    joined_root = ET.Element("MultiRoundTrips")

    partition_dirs = [p for p in sorted(base_dir.iterdir()) if p.is_dir() and p.name.isdigit()]

    window_size = _effective_window(partition_dirs, args)
    window_groups = {i: [] for i in range(1, window_size + 1)}

    latest_sampling_weights = None

    for partition_dir in partition_dirs:
        xml_path = _find_states_xml(partition_dir)
        if xml_path is None:
            print(f"States file for partition {partition_dir} does not exist")
            continue

        population_path = partition_dir / args.population_filename
        if not population_path.exists():
            print(f"Population file for partition {partition_dir} does not exist; skipping")
            continue
        local_to_global = _load_local_to_global_index(population_path, args.population_filename)

        tree = _load_states_tree(xml_path)
        root = tree.getroot()
        multi_round_trips = root.findall(".//MultiRoundTrip")
        if not multi_round_trips:
            continue

        last_n_trips = multi_round_trips[-window_size:]
        if last_n_trips:
            sw = last_n_trips[-1].find("SamplingWeights")
            if sw is not None:
                latest_sampling_weights = sw

        for i, trip in enumerate(last_n_trips):
            relative_pos = window_size - len(last_n_trips) + i + 1

            for round_trip in trip.findall(".//RoundTrip"):
                rt_copy = ET.fromstring(ET.tostring(round_trip))
                orig_index = int(rt_copy.get("index"))
                global_index = local_to_global.get(orig_index)
                if global_index is None:
                    print(f"No population entry for local index {orig_index} in "
                          f"partition {partition_dir}; skipping RoundTrip")
                    continue
                rt_copy.set("index", str(global_index))

                window_groups[relative_pos].append(rt_copy)

    for i in range(1, window_size + 1):
        if not window_groups[i]:
            continue

        mrt_element = ET.SubElement(joined_root, "MultiRoundTrip", iteration=str(i))

        if i == window_size and latest_sampling_weights is not None:
            mrt_element.append(ET.fromstring(ET.tostring(latest_sampling_weights)))

        for rt_element in window_groups[i]:
            mrt_element.append(rt_element)

    joined_xml_path = base_dir / "partitions_merged/final_states_merged.xml"
    joined_xml_path.parent.mkdir(exist_ok=True, parents=True)
    joined_tree = ET.ElementTree(joined_root)
    ET.indent(joined_tree, space="    ", level=0)
    joined_tree.write(joined_xml_path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("--population-filename", default="population.tsv")
    args = parser.parse()

    main(args)
