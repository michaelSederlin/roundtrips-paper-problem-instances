package disaggregate.rvu;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import disaggregate.model.Person;

/**
 * Reads the survey CSV pair into a population: person background attributes from upbd,
 * trip legs from mddr, joined on the UENR person id.
 *
 * Persons are returned in upbd file order, with {@link Person#getIdx()} equal to the
 * position in the returned list -- callers index homes and round trips by position.
 * Persons without legs, or that fail {@link Person#validate()}, are skipped with a warning.
 */
public final class SurveyReader {
    static Logger logger = LogManager.getLogger(SurveyReader.class);

    private SurveyReader() {}

    public static List<Person> readPopulation(Path upbdCsv, Path mddrCsv) throws IOException {
        List<Map<String, String>> personRows = readCsv(upbdCsv);
        Map<String, List<Map<String, String>>> legsByPerson = readCsv(mddrCsv).stream()
                .collect(Collectors.groupingBy(row -> row.get("UENR")));
        logger.info("Read {} persons from {} and legs for {} persons from {}",
                personRows.size(), upbdCsv, legsByPerson.size(), mddrCsv);

        List<Person> population = new ArrayList<>();
        for (Map<String, String> row : personRows) {
            String id = row.get("UENR");

            List<Map<String, String>> legs = legsByPerson.get(id);
            if (legs == null || legs.isEmpty()) {
                logger.warn("Skipping person {}: no trip legs in {}", id, mddrCsv);
                continue;
            }

            Person person = new Person(population.size(), home(row), age(row), income(row), gender(row));
            person.setName(id);
            person.assignPersonTrip(new Trip(legs));

            try {
                person.validate();
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping person {} due to validation error: {}", id, e.getMessage());
                continue;
            }
            population.add(person);
        }

        logger.info("Loaded {} of {} persons.", population.size(), personRows.size());
        return population;
    }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();

        Reader in = path.toString().endsWith(".gz")
                ? new InputStreamReader(new GZIPInputStream(new FileInputStream(path.toFile())))
                : new FileReader(path.toFile());
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

    private static String home(Map<String, String> row) {
        String raw = row.get("BOST_S");
        // Zone ids written as decimals ("12.0") lose their trailing zeros.
        String cleaned = raw.matches("\\d+\\.\\d+") ? raw.replaceFirst("\\.?0+$", "") : raw;
        // Remove spaces from coordinates like "(1, 1)" -> "(1,1)"
        return cleaned.replaceAll("\\s+", "");
    }

    // Unparseable or out-of-range attributes become -1, which Person.validate() rejects,
    // so the person is skipped rather than entering the population with a bogus value.
    private static Integer age(Map<String, String> row) {
        try {
            int age = Integer.parseInt(row.get("AGE"));
            return age > 0 ? age : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static Long income(Map<String, String> row) {
        try {
            long income = (long) Double.parseDouble(row.get("INKUP"));
            return income >= 0 ? income : -1;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private static Integer gender(Map<String, String> row) {
        try {
            return Integer.parseInt(row.get("SEX"));
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
