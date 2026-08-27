# roundtrips-paper-problem-instances

### Synthesis TODO

```java
// ----------------- Plotting Partitions  -----------------
gridWorld.writeGridWorldToJson(partitionOutputDir + "/grid_world.json");
String[] plottingArgs = new String[]{
        "--results-dir", partitionOutputDir.toString(),
        "--states-xml", partitionOutputDir.resolve("states.xml").toString(),
        "--save-dir", partitionOutputDir.resolve("plots").toString(),
        "--preferences-xml", preferencesPath.toString(),
        "--population-csv", partitionOutputDir.resolve("population.csv").toString(),
        "--window", "10",
        "--reference-activity", "HOME"
};

String[] plottingScripts = new String[]{       		
        "plot_activity_durations_histogram.py", 
        "plot_activity_locations_heatmap.py",
        "plot_activity_locations_usage.py", 
//				"plot_convergence_stats.py",
        "plot_log_weights.py",

//				"plot_network.py",
        "plot_nStop_distribution.py",
        "plot_population_grouping.py",
        "plot_preference_matching.py",
        "plot_time_structure_histogram.py"
};
```