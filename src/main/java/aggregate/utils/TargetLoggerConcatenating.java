package aggregate.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.utils.misc.metropolishastings.MHStateProcessor;

public class TargetLoggerConcatenating<L extends Node> implements MHStateProcessor<MultiRoundTrip<L>> {
    private final String fileName;
    private String fileFolder;
    private final TargetDeviationWeight<L> target;
    private final long interval;
    private long iteration;
    private boolean createDir = true;
    private File fileFolderFile;
    private boolean headerWritten = false;

    public TargetLoggerConcatenating(long interval, TargetDeviationWeight<L> target, String fileName, String fileFolder) {
        this.fileName = fileName;
        this.fileFolder = fileFolder;
        this.fileFolderFile = new File(fileFolder);
        this.interval = interval;
        this.target = target;
    }

    public TargetLoggerConcatenating(long interval, TargetDeviationWeight<L> target, String fileName) {
        this(interval, target, fileName, "");
    }

    public void setFileFolder(String fileFolder) {
        this.fileFolder = fileFolder;
        this.fileFolderFile = new File(fileFolder);
    }

    public void setCreateDirectory(boolean createDirectory) {
        this.createDir = createDirectory;
    }

    public void start() {
        this.iteration = 0L;
        File outputFile = new File(Paths.get(this.fileFolder, this.fileName).toString());
        outputFile.delete();

        if (this.createDir) {
            if (!this.fileFolderFile.exists()) {
                this.fileFolderFile.mkdirs();
            }
        }
    }

    public void processState(MultiRoundTrip<L> multiRoundTrip) {
        if (this.iteration % (long) this.interval == 0L) {
            try {
                String outputFile = Paths.get(this.fileFolder, this.fileName).toString();
                boolean append = true; 
                PrintWriter writer = new PrintWriter(new FileWriter(outputFile, append));

                double[] targets = this.target.computeTargetIfAbsent();
                double[] samples = this.target.computeSample(multiRoundTrip, this.target.getFilter());
                String[] labels = this.target.createLabels();

                if (!headerWritten) {
                    StringBuilder header = new StringBuilder();
                    for (String label : labels) {
                        header.append(label).append("_target")
                              .append("\t").append(label).append("_sample")
                              .append("\t");
                    }

                    if (header.length() > 0) header.setLength(header.length() - 1);
                    writer.println(header.toString());
                    headerWritten = true;
                }


                StringBuilder line = new StringBuilder();
                for (int i = 0; i < targets.length; ++i) {
                    line.append(targets[i]).append("\t").append(samples[i]).append("\t");
                }

                if (line.length() > 0) line.setLength(line.length() - 1);
                writer.println(line.toString());

                writer.flush();
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        ++this.iteration;
    }

    public void end() {
    }
}
