package disaggregate.rvu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Objects;

public class MDDR extends RvuDataset {
    public MDDR(List<Map<String, String>> rows) {
        super(rows);
    }

    @Override 
    public String toString() {
        return "MDDR: " + super.toString();
    }

    public Set<Object> getIds() {
        // TODO: Make type more strict
        return rows.stream().map(row -> row.get("UENR")).collect(Collectors.toSet());
    }

    public MDDR subset(Set<String> subsetIds) {
        rows.removeIf(row -> !subsetIds.contains(row.get("UENR")));
        return this;
    }

    // Unique trip purposes
    public Set<Object> getTripPurposes() {
        return rows.stream().map(row -> row.get("D_ARE_LONG")).collect(Collectors.toSet());
    }

    // Count of each trip purpose
    public Map<Object, Long> getTripPurposeCounts() {
        return rows.stream().collect(
            Collectors.groupingBy(row -> row.get("D_ARE_LONG"), Collectors.counting())
        );
    }

    // Unique modes (short or long)
    public Set<Object> getModes(boolean longMode) {
        String col = longMode ? "D_FORD_VERBOSE_LONG" : "D_FORD_VERBOSE_SHORT";
        return rows.stream().map(row -> row.get(col)).collect(Collectors.toSet());
    }

    // Mode counts (short or long)
    public Map<Object, Long> getModeCounts(boolean longMode) {
        String col = longMode ? "D_FORD_VERBOSE_LONG" : "D_FORD_VERBOSE_SHORT";
        return rows.stream().collect(
            Collectors.groupingBy(row -> row.get(col), Collectors.counting())
        );
    }

    // Trips for a given person id
    public List<Map<String, String>> getTripPurposeById(Object personId) {
        return rows.stream()
            .filter(row -> personId.equals(row.get("UENR")))
            .collect(Collectors.toList());
    }

        public Trip getTripById(Object id) {
            return new Trip(getRowsById(id));
        }

    public List<Map<String, String>> getRowsById(Object pId) {
        List<Map<String, String>> results = rows.stream()
            .filter(row -> pId.equals(row.get("UENR")))
            .collect(Collectors.toList());

        // Sort by D_A_KL which is "HH:MM"
        results.sort(Comparator.comparing(row -> row.get("D_A_KL").toString()));

        return results;
    }

    // Row(s) for person+trip
    public List<Map<String, String>> getTripPurposeByIdAndTrip(Object personId, String tripId) {
        // Assumes tripId = H_NUMMER + D_NUMMER as string (adjust as needed)
        return rows.stream()
            .filter(row -> personId.equals(row.get("UENR")) &&
                        String.valueOf(row.get("H_NUMMER")).equals("" + tripId.charAt(0)) &&
                        String.valueOf(row.get("D_NUMMER")).equals("" + tripId.charAt(1)))
            .collect(Collectors.toList());
    }

    // First legs: H_NUMMER == 1, D_NUMMER == 1
    public List<Map<String, String>> getFirstLegs() {
        return rows.stream()
            .filter(row -> "1".equals(String.valueOf(row.get("H_NUMMER"))) &&
                        "1".equals(String.valueOf(row.get("D_NUMMER"))))
            .collect(Collectors.toList());
    }

    public List<Map<String, String>> getLastLegs() {
        // Group by UENR, for each group pick row with max (H_NUMMER, D_NUMMER)
        return rows.stream()
            .collect(Collectors.groupingBy(row -> row.get("UENR")))
            .values().stream()
            .map(list -> list.stream()
                .max(Comparator
                    .comparing((Map<String, String> r) -> Integer.parseInt(r.get("H_NUMMER").toString()))
                    .thenComparing(r -> Integer.parseInt(r.get("D_NUMMER").toString())))
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public void removePerson(String id) {
            
    }

// In MDDR.java
public static MDDR fromAmalCSV(List<Map<String, String>> rows) {
    // Add synthetic H_NUMMER and D_NUMMER by grouping consecutive legs
    List<Map<String, String>> enrichedRows = new ArrayList<>();
    
    Map<String, List<Map<String, String>>> byPerson = rows.stream()
        .collect(Collectors.groupingBy(row -> row.get("UENR")));
    
    for (Map.Entry<String, List<Map<String, String>>> entry : byPerson.entrySet()) {
        String personId = entry.getKey();
        List<Map<String, String>> personLegs = entry.getValue();
        
        // Sort by departure time
        personLegs.sort(Comparator.comparing(row -> 
            Double.parseDouble(row.get("D_A_T"))));
        
        int legNum = 1;
        for (Map<String, String> leg : personLegs) {
            Map<String, String> enriched = new HashMap<>(leg);
            enriched.put("H_NUMMER", "1");  // All legs in one "main trip"
            enriched.put("D_NUMMER", String.valueOf(legNum++));
            enriched.put("D_A_KL", formatDecimalToTime(leg.get("D_A_T")));
            enriched.put("D_B_KL", formatDecimalToTime(leg.get("D_B_T")));
            // Normalize coordinates: remove spaces from "(1, 1)" -> "(1,1)"
            String dAre = leg.get("D_ARE");
            if (dAre != null) {
                dAre = dAre.replaceAll("\\s+", "");
            }
            enriched.put("D_ARE_LONG", dAre);  // Map D_ARE to D_ARE_LONG
            enriched.put("D_FORD_VERBOSE_SHORT", "UNKNOWN");
            enriched.put("D_FORD_VERBOSE_LONG", "Unknown");
            enrichedRows.add(enriched);
        }
    }
    return new MDDR(enrichedRows);
}

private static String formatDecimalToTime(String decimalHours) {
    double time = Double.parseDouble(decimalHours);
    if (time < 0) {
        time = 0.0;
    }

    if (time > 24) {
        time = 24.0;
    }
    int hours = (int) time;
    int minutes = (int) ((time - hours) * 60);
    return String.format("%02d%02d", hours, minutes); // 4-digit format: "1015" not "10:15"
}    
}
