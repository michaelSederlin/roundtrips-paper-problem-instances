package common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SplitPopulationJoiner {
    final Path baseDir;
    final Path outputPath;
    final String populationFileName;
    String delimiter = ",";

    public SplitPopulationJoiner(Path baseDir, String populationFileName, String targetFileName) {
        this.baseDir = baseDir;
        this.populationFileName = populationFileName;

        outputPath = baseDir.resolve("partitions_merged").resolve(targetFileName);
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void join() {
        List<String> header = null;

        try {
            Files.createDirectories(outputPath.getParent());

            try (BufferedWriter out = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
                 Stream<Path> entries = Files.list(baseDir)) {

                List<Path> partitionDirs = entries
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return !name.isEmpty() && name.chars().allMatch(Character::isDigit);
                    })
                    .sorted()
                    .toList();

                for (Path partitionDir : partitionDirs) {
                    Path inPath = partitionDir.resolve(populationFileName);
                    if (!Files.exists(inPath)) {
                        System.out.println("Population file for partition " + partitionDir + " does not exist");
                        continue;
                    }

                    try (BufferedReader in = Files.newBufferedReader(inPath, StandardCharsets.UTF_8)) {
                        String headerLine = in.readLine();
                        if (headerLine == null) continue;
                        List<String> fields = parseLine(headerLine);

                        if (header == null) {
                            header = fields;
                            out.write(formatLine(header));
                            out.write("\n");
                        }

                        // map this file's columns onto the master header order (like DictWriter)
                        Map<String, Integer> colIndex = new HashMap<>();
                        for (int i = 0; i < fields.size(); i++) colIndex.put(fields.get(i), i);

                        String line;
                        while ((line = in.readLine()) != null) {
                            List<String> values = parseLine(line);
                            List<String> ordered = new ArrayList<>(header.size());
                            for (String col : header) {
                                Integer idx = colIndex.get(col);
                                ordered.add(idx != null && idx < values.size() ? values.get(idx) : "");
                            }
                            out.write(formatLine(ordered));
                            out.write("\n");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> parseLine(String line) {
        char delim = delimiter.charAt(0);
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delim) {
                result.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        result.add(field.toString());
        return result;
    }

    private String formatLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(quote(values.get(i)));
        }
        return sb.toString();
    }

    private String quote(String value) {
        boolean needs = value.contains(delimiter) || value.indexOf('"') >= 0
                     || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needs ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}