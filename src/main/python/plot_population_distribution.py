from pathlib import Path
import json
import matplotlib.pyplot as plt
import numpy as np

from plotting.common_parameters import barplot_size


def main(json_file: Path, age_output_path: Path, income_output_path: Path):
    barplot_kwds = {'width': 0.55, 'alpha': 0.75, 'edgecolor': 'black'}
    fontsize = 12

    with open(json_file, "r") as f:
        config = json.load(f)

    age_groups = config["ageGroups"]
    age_dist = config["ageDistribution"]
    age_income_median = config["ageIncomeMedian"]

    cumulative = sorted([float(val.get('cutoff')) for val in age_dist])
    probabilities = np.diff([0] + cumulative)
    x = np.arange(len(age_groups))

    fig_age, ax_age = plt.subplots(figsize=barplot_size)

    ax_age.bar(
        x,
        probabilities,
        color="steelblue",
        **barplot_kwds
    )

    ax_age.set_xlabel("Age Groups", fontsize=fontsize)
    ax_age.set_ylabel("Share of Population", fontsize=fontsize)

    ax_age.set_xticks(x)
    ax_age.set_xticklabels(
        age_groups,
        rotation=45,
        ha="right",
        fontsize=fontsize,
    )

    ax_age.set_ylim(bottom=0)
    ax_age.grid(axis="y", linestyle="--", alpha=0.5)

    plt.tight_layout()

    fig_age.savefig(
        age_output_path,
        format="pdf",
        dpi=300,
    )

    plt.close(fig_age)

    income_by_max_age = {
        int(entry["maxAge"]): float(entry["incomeKsek"])
        for entry in age_income_median
    }

    median_income = [
        income_by_max_age[int(group.split("-")[1])]
        for group in age_groups
    ]

    fig_income, ax_income = plt.subplots(figsize=barplot_size)

    ax_income.bar(
        x,
        median_income,
        color="steelblue",
        **barplot_kwds
    )

    ax_income.set_xlabel("Age Groups", fontsize=fontsize)
    ax_income.set_ylabel(
        "Median Income [x1000 SEK]",
        fontsize=fontsize,
    )

    ax_income.set_xticks(x)
    ax_income.set_xticklabels(
        age_groups,
        rotation=45,
        ha="right",
        fontsize=fontsize,
    )

    ax_income.set_ylim(bottom=0)
    ax_income.grid(axis="y", linestyle="--", alpha=0.5)

    plt.tight_layout()

    fig_income.savefig(
        income_output_path,
        format="pdf",
        dpi=300,
    )

    plt.close(fig_income)


if __name__ == "__main__":
    from argparse import ArgumentParser
    parser = ArgumentParser() 
    parser.add_argument("--json-file", type = Path)
    parser.add_argument("--income-calibration-path", type = Path)
    parser.add_argument("--age-calibration-path", type = Path)
    args = parser.parse_args()
    main(args.json_file, args.age_output_path, args.income_output_path)
