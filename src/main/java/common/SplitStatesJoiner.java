package common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SplitStatesJoiner {
    final Path baseDir;
    final String populationFilename;
    final String convergenceStatsFilename;
    final int window;

    public SplitStatesJoiner(Path baseDir, String populationFilename,
                             String convergenceStatsFilename, int window) {
        this.baseDir = baseDir;
        this.populationFilename = populationFilename;
        this.convergenceStatsFilename = convergenceStatsFilename;
        this.window = window;
    }

    public void join() {
        try {
            Document outDoc = newDocument();
            Element joinedRoot = outDoc.createElement("MultiRoundTrips");
            outDoc.appendChild(joinedRoot);

            List<Path> partitionDirs = partitionDirs();

            int windowSize = effectiveWindow(partitionDirs);
            Map<Integer, List<Element>> windowGroups = new LinkedHashMap<>();
            for (int i = 1; i <= windowSize; i++) windowGroups.put(i, new ArrayList<>());

            Element latestSamplingWeights = null;

            for (Path partitionDir : partitionDirs) {
                Path xmlPath = findStatesXml(partitionDir);
                if (xmlPath == null) {
                    System.out.println("States file for partition " + partitionDir + " does not exist");
                    continue;
                }

                Path populationPath = partitionDir.resolve(populationFilename);
                if (!Files.exists(populationPath)) {
                    System.out.println("Population file for partition " + partitionDir + " does not exist; skipping");
                    continue;
                }
                Map<Integer, Integer> localToGlobal = loadLocalToGlobalIndex(populationPath);

                Document tree = loadStatesTree(xmlPath);
                List<Element> multiRoundTrips = elementsByTag(tree.getDocumentElement(), "MultiRoundTrip");
                if (multiRoundTrips.isEmpty()) continue;

                List<Element> lastNTrips = lastN(multiRoundTrips, windowSize);
                if (!lastNTrips.isEmpty()) {
                    Element sw = firstChildElement(lastNTrips.get(lastNTrips.size() - 1), "SamplingWeights");
                    if (sw != null) latestSamplingWeights = sw;
                }

                for (int i = 0; i < lastNTrips.size(); i++) {
                    int relativePos = windowSize - lastNTrips.size() + i + 1;
                    Element trip = lastNTrips.get(i);
                    for (Element roundTrip : elementsByTag(trip, "RoundTrip")) {
                        Element rtCopy = (Element) outDoc.importNode(roundTrip, true);
                        int origIndex = Integer.parseInt(rtCopy.getAttribute("index").trim());
                        Integer globalIndex = localToGlobal.get(origIndex);
                        if (globalIndex == null) {
                            System.out.println("No population entry for local index " + origIndex
                                + " in partition " + partitionDir + "; skipping RoundTrip");
                            continue;
                        }
                        rtCopy.setAttribute("index", String.valueOf(globalIndex));
                        windowGroups.get(relativePos).add(rtCopy);
                    }
                }
            }

            for (int i = 1; i <= windowSize; i++) {
                List<Element> group = windowGroups.get(i);
                if (group.isEmpty()) continue;

                Element mrtElement = outDoc.createElement("MultiRoundTrip");
                mrtElement.setAttribute("iteration", String.valueOf(i));
                joinedRoot.appendChild(mrtElement);

                if (i == windowSize && latestSamplingWeights != null) {
                    mrtElement.appendChild(outDoc.importNode(latestSamplingWeights, true));
                }
                for (Element rt : group) {
                    mrtElement.appendChild(rt);
                }
            }

            Path joinedXmlPath = baseDir.resolve("partitions_merged/final_states_merged.xml");
            Files.createDirectories(joinedXmlPath.getParent());
            writeXml(outDoc, joinedXmlPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- states-merge helpers ----

    private List<Path> partitionDirs() throws IOException {
        try (var entries = Files.list(baseDir)) {
            return entries
                .filter(Files::isDirectory)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return !name.isEmpty() && name.chars().allMatch(Character::isDigit);
                })
                .sorted()
                .toList();
        }
    }

    private static Path findStatesXml(Path partitionDir) {
        Path xmlPath = partitionDir.resolve("states.xml");
        if (!Files.exists(xmlPath)) xmlPath = partitionDir.resolve("states.xml.gz");
        return Files.exists(xmlPath) ? xmlPath : null;
    }

    private Document loadStatesTree(Path xmlPath) throws IOException {
        try (InputStream raw = Files.newInputStream(xmlPath);
             InputStream in = xmlPath.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            return newBuilder().parse(in);
        } catch (org.xml.sax.SAXException e) {
            throw new IOException("Failed to parse " + xmlPath, e);
        }
    }

    /** {local sequential row index -> int(row["idx"])}, delimiter from the filename. */
    private Map<Integer, Integer> loadLocalToGlobalIndex(Path populationPath) throws IOException {
        char delim = populationPath.toString().toLowerCase().endsWith(".csv") ? ',' : '\t';
        Map<Integer, Integer> map = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(populationPath, StandardCharsets.UTF_8)) {
            String headerLine = r.readLine();
            if (headerLine == null) return map;
            List<String> header = parseLine(headerLine, delim);
            int idxCol = header.indexOf("idx");
            if (idxCol < 0) throw new IllegalStateException("No 'idx' column in " + populationPath);
            String line;
            int localIndex = 0;
            while ((line = r.readLine()) != null) {
                List<String> values = parseLine(line, delim);
                map.put(localIndex, Integer.parseInt(values.get(idxCol).trim()));
                localIndex++;
            }
        }
        return map;
    }

    /** Post-burn-in iteration count for one partition, or -1 if burn-in unusable. */
    private int partitionPostBurnInCount(Path xmlPath, Path convergenceStatsFp) throws IOException {
        Integer burnIn = BurnIn.readBurnInIteration(convergenceStatsFp);
        if (burnIn == null) return -1;
        Document tree = loadStatesTree(xmlPath);
        int count = 0;
        for (Element mrt : elementsByTag(tree.getDocumentElement(), "MultiRoundTrip")) {
            if (Integer.parseInt(mrt.getAttribute("iteration").trim()) >= burnIn) count++;
        }
        return count;
    }

    /** Most-recent logged iterations to merge per partition: min post-burn-in count
     *  across partitions when every convergenceStats.tsv is usable, else fixed --window. */
    private int effectiveWindow(List<Path> partitionDirs) throws IOException {
        List<Integer> counts = new ArrayList<>();
        for (Path partitionDir : partitionDirs) {
            Path xmlPath = findStatesXml(partitionDir);
            if (xmlPath == null) { counts.add(null); continue; }
            int c = partitionPostBurnInCount(xmlPath, partitionDir.resolve(convergenceStatsFilename));
            counts.add(c < 0 ? null : c);
        }
        if (counts.stream().anyMatch(c -> c == null)) {
            System.out.println("WARNING: burn-in unusable for at least one partition; "
                + "falling back to fixed --window=" + window + ".");
            return window;
        }
        int min = counts.stream().mapToInt(Integer::intValue).min().orElse(1);
        return Math.max(1, min);
    }

    // ---- generic helpers ----

    private static DocumentBuilder newBuilder() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            return f.newDocumentBuilder();
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Document newDocument() {
        return newBuilder().newDocument();
    }

    /** Descendant elements with the given tag, in document order (like ".//tag"). */
    private static List<Element> elementsByTag(Element root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        List<Element> list = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) list.add((Element) nl.item(i));
        return list;
    }

    /** First direct child element with the given tag (like ET.find("tag")). */
    private static Element firstChildElement(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(tag)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static <T> List<T> lastN(List<T> list, int n) {
        int size = list.size();
        return new ArrayList<>(list.subList(Math.max(0, size - n), size));
    }

    private static List<String> parseLine(String line, char delim) {
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

    private static void writeXml(Document doc, Path out) throws IOException {
        stripWhitespaceTextNodes(doc);
        try (OutputStream os = Files.newOutputStream(out)) {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            t.transform(new DOMSource(doc), new StreamResult(os));
        } catch (javax.xml.transform.TransformerException e) {
            throw new IOException("Failed to write " + out, e);
        }
    }

    private static void stripWhitespaceTextNodes(Document doc) {
        try {
            NodeList empty = (NodeList) XPathFactory.newInstance().newXPath()
                .evaluate("//text()[normalize-space(.)='']", doc, XPathConstants.NODESET);
            for (int i = 0; i < empty.getLength(); i++) {
                Node n = empty.item(i);
                n.getParentNode().removeChild(n);
            }
        } catch (javax.xml.xpath.XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
}