from plotting.merge_states_xml import main as merge_states_main 
from plotting.argument_parser import ArgumentParser


class Arguments:
    

merge_states_main(args)

import importlib
import ddsutils.plotting
# importlib.reload(ddsutils.plotting.plot_population_reproduction)
# importlib.reload(ddsutils.plotting.plot_od_and_transitions)

import seaborn as sns 
sns.reset_defaults()

from renate import get_environment_root
from pathlib import Path 
import matplotlib.pyplot as plt 
import seaborn as sns 

from ddsutils.merge_population_csv import main as population_merge_main 
from ddsutils.merge_states_xml import main as states_merge_main 
from ddsutils.plotting.plot_education_diagnostics import main as education_diagnostics_main


from ddsutils.plotting.plot_population_reproduction import (
    save_show,
    main as population_reproduction_main
)

from ddsutils.plotting.plot_od_and_transitions import main as plot_od_main
from ddsutils.plotting.plot_individual_reproduction import (
    ground_truth_agent_stats, 
    estimation_agent_stats
)

from plot_age_bin_activity_durations import main as age_bins_main

dds_root = get_environment_root() 

class Args:
    measurement_states_xml = dds_root / "amal/Results_Paper/2026 08 07/amal_measurement/out/ProductionRun_1000/partitions_merged/_final_states_merged.xml"
    validation_states_xml = dds_root / "amal/Results_Paper/2026 08 07/amal_validation/out/ProductionRun_1000/partitions_merged/_final_states_merged.xml"
    data_dir = dds_root / "renate/data/scenarios/amal/results_paper/2026 08 07/full"
    time_bin_size_h = 1
    horizon = 24
    convergence_stats_filename = 'convergenceStats.tsv'
    target_filename = 'population_merged.tsv'
    population_filename = 'population.tsv'
    window = 1
    save = True
    show = False
    output_suffix = '.pdf'
    msg_interval = 10000

    def __init__(self, scenario):
        self.results_dir = Path(scenario)
        self.save_dir = self.results_dir / 'new_plots'
        if not self.save_dir.exists():
            self.save_dir.mkdir()

# scenario = 'test'
scenarios = [
    # 'narrow-n-100'
    # 'n-responses-500',
    # 'n-responses-100',
    # 'n-responses-20',
    # 'base',
    # 'B2-schedov3',
    # 'B1-schedov2'
    # 'new'
    'narrow-gaussian-gender'
    ]

for scenario in scenarios:
    args = Args(Path(scenario))
    # population_merge_main(args)
    # states_merge_main(args)

    population_reproduction_main(args)
    # plot_od_main(args)
    education_diagnostics_main(args)
    age_bins_main(args)
    