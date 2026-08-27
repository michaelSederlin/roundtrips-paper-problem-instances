package common.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ActivityLocationSetsWriter {

	public static void write(List<String> nodeList, Path outputPath, int gridSize) {
		write(nodeList, outputPath.toString(),  gridSize);
	}
	
	
	public static void write(List<String> nodeList, String outputPath, int gridSize) {

	    String seperator = ";";
	    int[][] counts = new int[gridSize][gridSize];

	    for (String node : nodeList) {
	        String trimmed = node.replace("(", "").replace(")", "");
	        String[] parts = trimmed.split(",");
	        int row = Integer.parseInt(parts[0].trim());
	        int col = Integer.parseInt(parts[1].trim());
	        if (row >= 0 && row < gridSize && col >= 0 && col < gridSize) {
	            counts[row][col]++;
	        }
	    }

	    // Write file 
	    try (FileWriter writer = new FileWriter(outputPath)) { 
	    	// Write header 
	        writer.write(seperator);
	    	for (int row = 0; row < gridSize; row++) { 
	    		for (int col = 0; col < gridSize; col++) { 
	    			writer.write("(" + row + "," + col + ")"); 
	    			if (!(row == gridSize - 1 && col == gridSize - 1)) { 
	    				writer.write(seperator); 
	    			} 
	    		} 
	    	} 
	    	writer.write("\n");

	        // Write Row
	        writer.write("pop group");
	        writer.write(seperator);

	        for (int row = 0; row < gridSize; row++) {
	            for (int col = 0; col < gridSize; col++) {
	                writer.write(String.valueOf(counts[row][col]));
	                if (!(row == gridSize - 1 && col == gridSize - 1)) {
	                    writer.write(seperator);
	                }
	            }
	        }
	        writer.write("\n");

	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
}