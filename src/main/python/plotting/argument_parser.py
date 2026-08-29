import argparse 
from pathlib import Path 


# class ArgumentParser: 
#     def __init__(self):
#         self.parser = argparse.ArgumentParser()
#         self.parser.add_argument("--results-dir", type=Path, required=True)
#         self.parser.add_argument("--show", action="store_true")
#         self.parser.add_argument("--save", action="store_true")
#         self.parser.add_argument("--data-dir", type = Path, default = "data/")
#         self.parser.add_argument("--save-dir", type = Path)
#         self.parser.add_argument("--split-figures", action="store_true")
#         self.parser.add_argument("--window", type = int, default = 1)
#         self.parser.add_argument("--rasterized", action = "store_true")

#     def add_argument(self, *args, **kwargs):
#         self.parser.add_argument(*args, **kwargs)
        
#     def __call__(self):
#         args = self.parser.parse_args()

#         if args.save_dir is None: 
#             args.save_dir = args.results_dir

#         return args




class ArgumentParser: 
    def __init__(self):
        self.parser = argparse.ArgumentParser()
        self.parser.add_argument("--results-dir", type=Path)
        self.parser.add_argument("--show", action="store_true")
        self.parser.add_argument("--save", action="store_true")
        self.parser.add_argument("--data-dir", type = Path, default = "data/")
        self.parser.add_argument("--preferences-xml", type = Path)
        self.parser.add_argument("--save-dir", type = Path)
        self.parser.add_argument("--window", default = 1, type = int)
        self.parser.add_argument("--time-bin-size-h", type = float, default = 1.0)
        self.parser.add_argument("--horizon", type = float, default = 24.0)
        self.parser.add_argument("--output-suffix", type=str, default='.pdf')

        preference_args = self.parser.add_argument_group()
        preference_args.add_argument("--states-xml", type = Path)
        preference_args.add_argument("--population-csv", type = Path)
        preference_args.add_argument("--respondents", type = Path)

        
        reproduction_args = self.parser.add_argument_group()
        reproduction_args.add_argument("--msg-interval", type = int, default = 5000)
        reproduction_args.add_argument("--measurement-states-xml", type = Path, default = None)
        reproduction_args.add_argument("--validation-states-xml", type = Path, default = None)
        reproduction_args.add_argument("--convergence-stats-filename", type=str, default="convergenceStats.tsv")
        

    def add_argument(self, *args, **kwargs):
        self.parser.add_argument(*args, **kwargs)
    
    def parse(self):
        args = self.parser.parse_args()
        if args.save_dir is None: 
            args.save_dir = args.results_dir
        else:
            args.save = True

        if args.save:
            args.save_dir.mkdir(exist_ok = True, parents = True)

        return args 
    
    def __call__(self):
        # Kept for legacy
        return self.parse()
