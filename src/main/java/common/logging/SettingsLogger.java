package common.logging;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class SettingsLogger {

	// --------------- Fields --------------- 

	private final String filePath;
	private final LocalDateTime startTime;


	// --------------- Constructor --------------- 

	public SettingsLogger(String filePath, LocalDateTime startTime) {
		this.filePath = filePath;
		this.startTime = startTime;

	}	
	
	
	public SettingsLogger(String filePath) {
		this.filePath = filePath;
		this.startTime = null;

	}


	// --------------- Implementation --------------- 

	public void writeSettings(String className, int numberOfZones, int numberOfRT, int numberOfIterations, List<String> targets, List<Double> mjus, 
			double meanRoundTripLength, double flipProb, String inputPath) throws FileNotFoundException {
		PrintStream out = new PrintStream(new FileOutputStream(filePath));
		out.println("Class: " + className);
		out.println("numberOfZones: " + numberOfZones);
		out.println("numberOfRT: " + numberOfRT);
		out.println("numberOfIterations: " + numberOfIterations);
		out.println("targets: " + targets);
		out.println("mjuList: " + mjus);
		out.println("meanRoundTripLength: " + meanRoundTripLength);
		out.println("flipProb: " + flipProb);
		out.println("inputPath: " + inputPath);
	}
	
	public void writeSettings(String className, int numberOfZones, int numberOfRT, int numberOfIterations, LinkedHashMap<String, Double> target2mju, 
			double meanRoundTripLength, double flipProb, String inputPath) throws FileNotFoundException {
		PrintStream out = new PrintStream(new FileOutputStream(filePath));
		out.println("Class: " + className);
		out.println("numberOfZones: " + numberOfZones);
		out.println("numberOfRT: " + numberOfRT);
		out.println("numberOfIterations: " + numberOfIterations);
		out.println("target2mju: " + target2mju);
		out.println("meanRoundTripLength: " + meanRoundTripLength);
		out.println("flipProb: " + flipProb);
		out.println("inputPath: " + inputPath);
	}
	
	


	public void writeSettings(int gridSize, int populationSize, int nIter, int numberOfWeights) throws FileNotFoundException {
		PrintStream out = new PrintStream(new FileOutputStream(filePath));
		out.println("gridSize: " + gridSize);
		out.println("populationSize: " + populationSize);
		out.println("nIter: " + nIter);
		out.println("numberOfWeights: " + numberOfWeights);
	}
	

	public void writeRuntimeInfo(int numberOfIterations) throws FileNotFoundException {
		LocalDateTime endTime = LocalDateTime.now();
		double runtimeSec = Duration.between(startTime, endTime).toSeconds();
		double runtimeMin = runtimeSec / 60;
		double runtimeHour = runtimeMin / 60;
		double runtimeDay = runtimeHour / 24;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		PrintStream out = new PrintStream(new FileOutputStream(filePath, true));
		out.println();
		out.println("Start: " + startTime.format(formatter));
		out.println("End:   " + endTime.format(formatter));
		out.println(String.format(Locale.GERMAN, "Runtime [sec]: %.0f", runtimeSec));
		out.println(String.format(Locale.GERMAN, "Runtime [min]: %.0f", runtimeMin));
		out.println(String.format(Locale.GERMAN, "Runtime [h]:   %.1f", runtimeHour));
		out.println(String.format(Locale.GERMAN, "Runtime [d]:   %.2f", runtimeDay));
		out.println(String.format(Locale.GERMAN, "Time per iteration [s]: %.5f", runtimeSec / numberOfIterations));
		out.println();
		out.println( startTime.format(formatter) + "\t" +
				endTime.format(formatter) + "\t" +
				String.format(Locale.GERMAN, "%.1f", runtimeDay) + "\t" + 
				String.format(Locale.GERMAN, "%.1f", runtimeHour) + "\t" + 
				String.format(Locale.GERMAN, "%.0f", runtimeMin) + "\t" + 
				String.format(Locale.GERMAN, "%.0f", runtimeSec) + "\t" + 
				numberOfIterations/1_000_000 + "e6" + "\t" + 
				String.format(Locale.GERMAN, "%.5f", runtimeSec / numberOfIterations));
	}
}







