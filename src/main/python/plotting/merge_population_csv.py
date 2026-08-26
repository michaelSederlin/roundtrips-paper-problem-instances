import csv
from pathlib import Path
from ddsutils.plotting.helpers import ArgumentParser

_DELIMITERS = {".csv": ",", ".tsv": "\t"}

def _delimiter(filename):
    return _DELIMITERS.get(Path(filename).suffix.lower(), "\t")

def main(args):
    base_dir = Path(args.results_dir).resolve()
    output_path = base_dir / "partitions_merged" / args.target_filename
    output_path.parent.mkdir(exist_ok=True)

    out_delim = _delimiter(args.target_filename)

    with open(output_path, "w", newline="", encoding="utf-8") as out_f:
        writer = None

        for partition_dir in sorted(base_dir.iterdir()):
            if not partition_dir.is_dir() or not partition_dir.name.isdigit():
                continue

            in_path = partition_dir / args.population_filename

            if not in_path.exists():
                print(f"Population file for partition {partition_dir} does not exist")
                continue

            in_delim = _delimiter(args.population_filename)

            with open(in_path, "r", encoding="utf-8") as in_f:
                reader = csv.DictReader(in_f, delimiter=in_delim)

                if writer is None:
                    writer = csv.DictWriter(out_f, fieldnames=reader.fieldnames, delimiter=out_delim)
                    writer.writeheader()

                for row in reader:
                    writer.writerow(row)


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("--population-filename", default="population.tsv")
    parser.add_argument("--target-filename", default="population_merged.tsv")
    args = parser.parse()
    main(args)