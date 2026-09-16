package aggregate.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import se.vti.utils.misc.Tuple;

public class CsvReader {

    // ------------------- Implementation -------------------
	
	public static double[] csvTo1dArray(String filePath, String separator) {
        List<Double> tempValues = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(separator);
                if (columns.length >= 2) {
                    tempValues.add(Double.parseDouble(columns[1]));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("File not found in csvTo1dArray(): " + e.getMessage());
            return new double[0];
        }

        return tempValues.stream().mapToDouble(Double::doubleValue).toArray();
    }
	
	

    public static double[][] csvTo2dArray(String filePath, String separator) {    
        double[][] resultArray = null;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String header = br.readLine();
            int rowCount = 0;
            int colCount = header.split(separator).length - 1;
            while (br.readLine() != null) rowCount++;
            resultArray = new double[rowCount][colCount];
            try (BufferedReader br2 = new BufferedReader(new FileReader(filePath))) {
                br2.readLine(); 
                int rowIndex = 0;
                String line;
                while ((line = br2.readLine()) != null) {
                    String[] values = line.split(separator);
                    for (int colIndex = 1; colIndex < values.length; colIndex++) {
                        resultArray[rowIndex][colIndex - 1] = Double.parseDouble(values[colIndex]);
                    }
                    rowIndex++;
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Datei: " + e.getMessage());
        }
        return resultArray;
    }

    
    
    public static Map<String, ArrayList<Tuple<Double, Double>>> coordsFromCsv(String filename, String separator) {
        Map<String, ArrayList<Tuple<Double, Double>>> randomCoords = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file");
            }
            String[] columnNames = headerLine.split(separator);
            for (String colName : columnNames) {
                randomCoords.put(colName, new ArrayList<>());
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] points = line.split(separator);
                int colCnt = -1;
                for (String point : points) {
                    colCnt++;
                    String coords = point.substring(7, point.length() - 1).trim();
                    String[] xy = coords.split(" ");
                    Double x = Double.parseDouble(xy[0]);
                    Double y = Double.parseDouble(xy[1]);
                    Tuple<Double, Double> tuple = new Tuple<>(x, y);
                    List<String> keyList = new ArrayList<>(randomCoords.keySet());
                    randomCoords.get(keyList.get(colCnt)).add(tuple);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return randomCoords;
    }

    
    
    // ------------------- Main method for testing -------------------

    public static void main(String[] args) {
        System.out.println("Started...");
        Map<String, ArrayList<Tuple<Double, Double>>> randomCooridinate = new LinkedHashMap<>();
        randomCooridinate = coordsFromCsv("./input/randomPoints.csv", "\t");
        for (Map.Entry<String, ArrayList<Tuple<Double, Double>>> entry : randomCooridinate.entrySet()) {
            String columnName = entry.getKey();
            ArrayList<Tuple<Double, Double>> coordinates = entry.getValue();
//          System.out.println("Column: " + columnName);
            for (Tuple<Double, Double> coord : coordinates) {
//              System.out.println("  (" + coord.getA() + ", " + coord.getB() + ")");
            }
        }   
        System.out.println("...done");
    }  
}
