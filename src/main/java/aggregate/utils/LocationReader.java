package aggregate.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


public class LocationReader {
	
	// ------------------- Fields -------------------
    private String[] locations;
    private String delimiter = ";";

    
	// ------------------- Construction -------------------
    public LocationReader(String filePath) {
        loadPostalCodes(filePath);
    }
    
    public LocationReader(String filePath, String delimiter) {
    	this.delimiter = delimiter;
        loadPostalCodes(filePath);
    }
    
    
    // ------------------- Implementation -------------------
    private void loadPostalCodes(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line != null) {
                locations = line.split(delimiter);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String[] getLocationNames() {
        return locations;
    }

    
	// ------------------- Main Method for Testing -------------------
    public static void main(String[] args) {
    	LocationReader loader = new LocationReader("input/zones_vienna_23.csv");
        String[] locations = loader.getLocationNames();

        if (locations != null) {
            for (String location : locations) {
                System.out.println(location);
            }
        }
    }
}

