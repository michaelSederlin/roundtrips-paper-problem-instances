import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import gzip

def main(states_xml: Path, save_dir: Path, save: bool = True, show: bool = False):

    ####################### Step 1: Count bins in final MultiRoundTrip #######################
    xml_path = str(states_xml)
    if xml_path.endswith(".gz"):
        with gzip.open(xml_path, "rt") as f:
            tree = ET.parse(f)
    else:
        tree = ET.parse(xml_path)
    root = tree.getroot()

    multi_round_trips = root.findall(".//MultiRoundTrip")
    last_trip = multi_round_trips[-1]

    bin_counts = {i: 0 for i in range(24)}

    for round_trip in last_trip.findall(".//RoundTrip"):
        state_elem = round_trip.find("State")
        if state_elem is not None and state_elem.text:
            match = re.search(r"bins\[(.*?)\]", state_elem.text)
            if match:
                bins = match.group(1).split(",")
                for b in bins:
                    b = b.strip()
                    if b.isdigit():
                        b = int(b)
                        if 0 <= b < 24:
                            bin_counts[b] += 1

    df_bins = pd.DataFrame([bin_counts])
    df_bins = df_bins[[i for i in range(24)]]

    ####################### Step 2: Plot time structure #######################
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.bar(df_bins.columns, df_bins.iloc[0])
    ax.set_xlabel("Bin")
    ax.set_ylabel("Frequency")
    ax.set_title("Departures per time bin")
    ax.grid(axis='y', linestyle='--', alpha=0.7)
    ax.set_xticks(range(24))
    plt.tight_layout()

    ####################### Step 3: Output #######################
    if save:
        with PdfPages(save_dir / "time_structure_histogram.pdf") as pdf:
            pdf.savefig(fig)

    if show:
        plt.show()

    plt.close(fig)
