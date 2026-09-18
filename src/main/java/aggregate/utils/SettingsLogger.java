package aggregate.utils;

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

	private String filePath;
	private LocalDateTime startTime;


	// --------------- Constructor --------------- 

	public SettingsLogger(String filePath, LocalDateTime startTime) {
		this.filePath = filePath;
		this.startTime = startTime;

	}


	// --------------- Functionality --------------- 

	public void writeSettings(String className, int numberOfRT, int numberOfIterations, LinkedHashMap<String, Double> target2sigma, 
			int timeBinCnt, double flipProb, String inputPath, double samplingWeight, boolean gauss, 
			double realPopulationSize, double timeBinSize_h) throws FileNotFoundException {
		PrintStream out = new PrintStream(new FileOutputStream(filePath));
		out.println("Class: " + className);
		out.println("inputPath: " + inputPath);
		out.println("numberOfRT: " + numberOfRT);
		out.println("realPopulationSize: " + (int) realPopulationSize);
		out.println("numberOfIterations: " + numberOfIterations);	
		out.println("samplingWeight: " + samplingWeight);
		out.println("target2sigma: " + target2sigma);
		out.println("Gauss: " + gauss);
//		out.println("avgNumberOfStops: " + avgNumberOfStops);
		out.println("timeBinCnt: " + timeBinCnt);
		out.println("timeBinSize_h: " + timeBinSize_h);
		out.println("flipProb: " + flipProb);
	}
	
	public void writeSettings(
			String className, 
			long seed, 
			int  nTimeBins,  
			double timeBinSize_h,
			double realPopulationSize, 
			int nRoundTrips,
			boolean gauss,
			boolean convergenceCriterion, 
			double tolerance,
			long msgInterval,
			long lastIteration,
			LinkedHashMap<String, Double> target2sigma,
			String prior) throws FileNotFoundException {
		PrintStream out = new PrintStream(new FileOutputStream(filePath));
		out.println("Class: " + className);
		out.println("Seed: " + seed);
		out.println();
		out.println("nTimeBins: " + nTimeBins);
		out.println("timeBinSize_h: " + timeBinSize_h);
		out.println("realPopulationSize: " + (int) realPopulationSize);
		out.println("nRoundTrips: " + nRoundTrips);
		out.println();
		out.println("convergenceCriterion: " + convergenceCriterion);
		out.println("convergenceCriterion tolerance: " + tolerance);
		out.println("last iteration: " + lastIteration);	
		out.println("msg interval: " + msgInterval);
		out.println();
		out.println("prior: " + prior);
		out.println("Gauss: " + gauss);
		out.println("target2sigma: " + target2sigma);
	}
	

	
	

	public void writeRuntimeInfo(long numberOfIterations) throws FileNotFoundException {
		LocalDateTime endTime = LocalDateTime.now();
		double runtimeSec = Duration.between(this.startTime, endTime).toSeconds();
		double runtimeMin = runtimeSec / 60;
		double runtimeHour = runtimeMin / 60;
		double runtimeDay = runtimeHour / 24;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		PrintStream out = new PrintStream(new FileOutputStream(filePath, true));
		out.println();
		out.println("Start: " + this.startTime.format(formatter));
		out.println("End:   " + endTime.format(formatter));
		out.println(String.format(Locale.GERMAN, "Runtime [sec]: %.0f", runtimeSec));
		out.println(String.format(Locale.GERMAN, "Runtime [min]: %.0f", runtimeMin));
		out.println(String.format(Locale.GERMAN, "Runtime [h]:   %.1f", runtimeHour));
		out.println(String.format(Locale.GERMAN, "Runtime [d]:   %.2f", runtimeDay));
		out.println(String.format(Locale.GERMAN, "Time per iteration [s]: %.5f", runtimeSec / numberOfIterations));
		out.println();
		out.println( this.startTime.format(formatter) + "\t" +
				endTime.format(formatter) + "\t" +
				String.format(Locale.GERMAN, "%.1f", runtimeDay) + "\t" + 
				String.format(Locale.GERMAN, "%.1f", runtimeHour) + "\t" + 
				String.format(Locale.GERMAN, "%.0f", runtimeMin) + "\t" + 
				String.format(Locale.GERMAN, "%.0f", runtimeSec) + "\t" + 
				numberOfIterations/1_000_000 + "e6" + "\t" + 
				String.format(Locale.GERMAN, "%.5f", runtimeSec / numberOfIterations));
	}
}