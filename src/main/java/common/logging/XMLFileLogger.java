package common;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.Charset;

import se.vti.utils.misc.metropolishastings.MHAbstractStateProcessor;

public abstract class XMLFileLogger<R> extends MHAbstractStateProcessor<R> {

	private final File logFile;

	PrintWriter writer;

	private boolean flushEachLine = true;
	private String root;
	private String encoding;

	public String getFilePath() {
		return this.logFile.getAbsolutePath();
	}

	public XMLFileLogger(long samplingInterval, String logFileName, String root, String encoding) {
		super(0, samplingInterval);
		this.logFile = new File(logFileName);
		this.root = root;
		this.encoding = encoding;
	}
	
	public void setFlushEachLine(boolean flushEachLine) {
		this.flushEachLine = flushEachLine;
	}

	@Override
	public final void start() {
		super.start();
		File directory = this.logFile.getParentFile();
		if (directory != null && !directory.exists()) {
			directory.mkdirs();
		}
		if (this.logFile.exists()) {
			this.logFile.delete();
		}
		try {
			this.logFile.createNewFile();
			if (this.logFile.getName().endsWith(".gz")) {
				this.writer = new PrintWriter(new OutputStreamWriter(
					new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(this.logFile))), 
					Charset.forName(encoding)
				));
			} else {
				this.writer = new PrintWriter(this.logFile);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.writer.println("<?xml version=\"1.0\" encoding=\"" + this.encoding + "\"?>");
		this.writer.println("<" + this.root + ">");
	}

	@Override
	public final void end() {
		this.writer.println("</" + this.root + ">");
		this.writer.flush();
		this.writer.close();
	}

	@Override
	public final void processStateHook(R state, double lw) {
		this.writer.println(createDataLine(state, lw));
		if (this.flushEachLine) {
			this.writer.flush();
		}
	}

	public abstract String createDataLine(R state, double lw);
}
