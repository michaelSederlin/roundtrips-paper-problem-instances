package disaggregate.rvu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class UPBD extends RvuDataset {
    private Map<String, Map<String, Object>> personAttributes = new HashMap<>();

    public UPBD(List<Map<String, String>> rows) {
        super(rows);

        for (Map<String, String> row : rows) {
            String id = row.get("UENR").toString();
            if (!personAttributes.containsKey(id)) {
                personAttributes.put(id, new HashMap<>());
            }
            personAttributes.get(id).putAll(row);
        }
    }

    public List<String> getIds() {
        return this.rows.stream()
                .map(row -> row.get("UENR").toString())
                .collect(Collectors.toList());
    }

    public long getPersonIncome(String id) {
        checkIdExists(id);
        Object incObj = personAttributes.get(id).get("INKUP");
        try {
            long inc = (long) Double.parseDouble(incObj.toString());
            return inc >= 0 ? inc : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public int getPersonAge(String id) {
        checkIdExists(id);
        Object ageObj = personAttributes.get(id).get("AGE");
        try {
            int age = Integer.parseInt(ageObj.toString());
            return age > 0 ? age : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public String getPersonHome(String id) {
        // Need handling of missing income
        checkIdExists(id);
        String raw = personAttributes.get(id).get("BOST_S").toString();
        String cleaned = raw;
        if (raw.matches("\\d+\\.\\d+")) {
            cleaned = raw.replaceFirst("\\.?0+$", "");  
        }
        // Remove spaces from coordinates like "(1, 1)" -> "(1,1)"
        cleaned = cleaned.replaceAll("\\s+", "");
        return cleaned;
    }

    public double getPersonWeight(String id) {
        if (personAttributes.containsKey(id)) {
            return (double) personAttributes.get(id).get("VIKT");
        } else {
            return Double.NaN; // Use NaN to indicate missing value for double
        }
    }

    public int getPersonGender(String id) {
        checkIdExists(id);
        Object genderObj = personAttributes.get(id).get("SEX");
        try {
            int gender = Integer.parseInt(genderObj.toString());
            return gender; 
        } catch (Exception e) {
            return -1;
        }
    }

    private void checkIdExists(String id) {
        if (!personAttributes.containsKey(id)) {
            throw new IllegalAccessError("ID not found in UPBD");
        }
    }

    public void removePerson(String id) {
        this.personAttributes.remove(id);
        this.rows.removeIf(row -> id.equals(row.get("UENR")));
    }

    public List<String> samplePersonIdsWithoutReplacement(int n, Random r) {
//        Random r = new Random(seed);
        if (n > this.getIds().size()) {
            throw new IllegalArgumentException("Cannot sample more ids than available");
        }

        List<String> ids = new ArrayList<>(this.getIds());
        List<String> sampled = new ArrayList<>(n);

        for (int ii = 0; ii < n; ii++) {
            int idx = r.nextInt(ids.size());
            sampled.add(ids.get(idx));
            ids.remove(idx);
        }

        return sampled;
    }
}
