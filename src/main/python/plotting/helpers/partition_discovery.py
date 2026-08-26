from pathlib import Path
from typing import Optional


def discover_partition_dirs(results_dir: Path) -> list[Path]:
    """Numeric subdirs of results_dir if any exist (multi-partition run), else
    [results_dir] itself if it directly holds a states.xml[.gz] (single-partition
    run, where ParallellAmalRunner sets partOutputDir == runOutputPath)."""
    numeric = sorted(p for p in results_dir.iterdir() if p.is_dir() and p.name.isdigit())
    if numeric:
        return numeric
    if states_xml_path(results_dir) is not None:
        return [results_dir]
    return []


def states_xml_path(partition_dir: Path) -> Optional[Path]:
    for name in ("states.xml", "states.xml.gz"):
        p = partition_dir / name
        if p.exists():
            return p
    return None
