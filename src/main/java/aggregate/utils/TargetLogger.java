package aggregate.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Paths;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.utils.misc.metropolishastings.MHStateProcessor;

public class TargetLogger<L extends Node> implements MHStateProcessor<MultiRoundTrip<L>> {
	
	private final String fileName;
	private String fileFolder;
	private final TargetDeviationWeight<L> target;
	private final long interval;
	private long iteration;
	private boolean overwrite = true;
	private boolean createDir = true;

	private File fileFolderFile;

	public TargetLogger(long interval, TargetDeviationWeight<L> target, String fileName, String fileFolder) {
		this.fileName = fileName;
		this.fileFolder = fileFolder;
		this.fileFolderFile = new File(fileFolder);
		this.interval = interval;
		this.target = target;
	}

	public TargetLogger(long interval, TargetDeviationWeight<L> target, String fileName) {
		this(interval, target, fileName, "");
	}

	public void setFileFolder(String fileFolder) {
		this.fileFolder = fileFolder;
		this.fileFolderFile = new File(fileFolder);
	}

	public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}

	public void setCreateDirectory(boolean createDirectory) {
		if (createDirectory) {
			this.createDir = createDirectory;
		}
	}

	public void start() {
		this.iteration = 0L;
		(new File(this.fileName)).delete();
		
		if (this.createDir) {
			if (!this.fileFolderFile.exists()) {
				this.fileFolderFile.mkdirs();
			}
		}
	}

	public void processState(MultiRoundTrip<L> multiRoundTrip) {

		// Gunnar took this out. Filtering happens in this.target.computeSample.
		// multiRoundTrip = this.target.filter(multiRoundTrip);
		
		if (this.iteration % (long) this.interval == 0L) {
			try {
				String var10002 = this.overwrite ? "" : this.iteration + ".";
				String outputFile = Paths.get(this.fileFolder, var10002 + this.fileName).toString();
				PrintWriter writer = new PrintWriter(outputFile);
				double[] targets = this.target.computeTargetIfAbsent();
				double[] samples = this.target.computeSample(multiRoundTrip, this.target.getFilter());
				String[] labels = this.target.createLabels();
				writer.println("label\ttarget\tsample");

				for (int i = 0; i < targets.length; ++i) {
					writer.println(labels[i] + "\t" + targets[i] + "\t" + samples[i]);
				}

				writer.flush();
				writer.close();
			} catch (FileNotFoundException var7) {
				throw new RuntimeException(var7);
			}
		}

		++this.iteration;
	}

	public void end() {
	}
}
