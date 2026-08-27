"""
Just a tiny helper to compress states.xml files that can get too large for github. 
"""

import sys
import gzip
import shutil
from pathlib import Path

directory = Path(sys.argv[1])

for xml_file in directory.glob("**/*.xml"):
    gz_file = xml_file.with_suffix(xml_file.suffix + ".gz")

    with xml_file.open("rb") as f_in, gzip.open(gz_file, "wb") as f_out:
        print(f"Compressing {xml_file} to {gz_file}")
        shutil.copyfileobj(f_in, f_out)
