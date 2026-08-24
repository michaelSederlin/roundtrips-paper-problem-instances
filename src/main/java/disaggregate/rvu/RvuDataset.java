package disaggregate.rvu;

import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;

public class RvuDataset {
    protected List<Map<String, String>> rows;

    public RvuDataset(List<Map<String, String>> rows) {
        this.rows = rows;
    }

    public int size() {
        return rows.size();
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }


    public static List<Map<String, String>> loadRowsFromCsv(String path) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();

        Reader in = path.endsWith(".gz")
            ? new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(new java.io.FileInputStream(path)))
            : new FileReader(path);
        try (Reader r = in;
            CSVParser parser = CSVFormat.RFC4180.builder()
                .setHeader()                // First line as header
                .setSkipHeaderRecord(true)  // Skip the header line in records
                .get()
                .parse(r)) {
                    for (CSVRecord rec : parser) {
                        rows.add(rec.toMap());
                    }
                }
        return rows;
    }

    @Override
    public String toString() {
        if (rows.isEmpty()) return "RvuDataset: (empty)";
        StringBuilder sb = new StringBuilder();
        sb.append("RvuDataset: ").append(rows.size()).append(" rows\n");
        // Print headers
        sb.append(rows.get(0).keySet()).append("\n");
        // Print first 3 rows
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            sb.append(rows.get(i)).append("\n");
        }
        if (rows.size() > 2) sb.append("...");
        return sb.toString();
    }

    public Map<String, String> getRow(int index) {
        assert index >= 0 && index < rows.size() : "Index out of bounds";
        return rows.get(index);
    }
}
