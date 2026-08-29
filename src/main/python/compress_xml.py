# Just a little helper to compress xml files that are sometimes too large for github
# Author: Michael Sederlin

import shutil 
import gzip 
from pathlib import Path 
import sys 

d = Path(sys.argv[1])
for f in d.glob("**/*.xml"):
    with open(f, "rb") as f_in, gzip.open(f.with_suffix(f.suffix + ".gz"), "wb") as f_out:
        print(f"Compressing {f_in} to {f_out}"i)
        shutil.copyfileobj(f_in, f_out)
