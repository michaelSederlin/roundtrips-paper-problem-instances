/**
 * instances.vienna
 * 
 * Copyright (C) 2024 by Gunnar Flötteröd (VTI, LiU).
 * 
 * VTI = Swedish National Road and Transport Institute
 * LiU = Linköping University, Sweden
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either 
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>. See also COPYING and WARRANTY file.
 */
package disaggregate.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import se.vti.utils.misc.Tuple;
import se.vti.utils.misc.tabularfileparser.AbstractTabularFileHandlerWithHeaderLine;
import se.vti.utils.misc.tabularfileparser.TabularFileParser;

import static java.io.File.separator;


public class MatrixDataReader {

	public MatrixDataReader() {
	}

	public static Map<Tuple<String, String>, Double> read(String fileName) {
		return readWithSeparator(fileName, ",");
	}

    public static Map<Tuple<String, String>, Double> readWithSemicolonSeparator(Path fileName) {
        return readWithSemicolonSeparator(fileName.toString());
    }
	
	public static Map<Tuple<String, String>, Double> readWithSemicolonSeparator(String fileName) {
		return readWithSeparator(fileName, ";");
	}

	public static Map<Tuple<String, String>, Double> readWithTabSeparator(String fileName) {
		return readWithSeparator(fileName, "\t");
	}

	public static Map<Tuple<String, String>, Double> readWithSeparator(String fileName, String separator) {
		Map<Tuple<String, String>, Double> data = new LinkedHashMap<>();

		AbstractTabularFileHandlerWithHeaderLine handler = new AbstractTabularFileHandlerWithHeaderLine() {
			@Override
			public void startDataRow(final String[] row) {
				String rowLabel = row[0];
				for (Map.Entry<String, Integer> e : this.label2index.entrySet()) {
					if (e.getValue() > 0 && row[e.getValue()].length() > 0) {
						String columnLabel = e.getKey();
						double value = Double.parseDouble(row[e.getValue()]);
						data.put(new Tuple<>(rowLabel, columnLabel), value);
					}
				}
			}
		};

		TabularFileParser parser = new TabularFileParser();
		parser.setDelimiterTags(new String[] { separator });
		parser.setOmitEmptyColumns(false);
		try {
			final String parseTarget;
			final java.io.File tmp;
			if (fileName.endsWith(".gz")) {
				tmp = java.io.File.createTempFile("matrixdata", ".tmp");
				tmp.deleteOnExit();
				try (java.io.InputStream in = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(fileName))) {
					java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				parseTarget = tmp.getAbsolutePath();
			} else {
				tmp = null;
				parseTarget = fileName;
			}
			try {
				parser.parse(parseTarget, handler);
			} finally {
				if (tmp != null) {
					tmp.delete();
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return data;
	}

	public static void printData(Map<Tuple<String, String>, Double> data) {
		if (data == null || data.isEmpty()) {
			System.out.println("No data to print.");
			return;
		}

		System.out.println("Matrix content:");
		for (Map.Entry<Tuple<String, String>, Double> entry : data.entrySet()) {
			Tuple<String, String> key = entry.getKey();
			Double value = entry.getValue();
			System.out.println("From: " + key.getA() + " To: " + key.getB()+ " Value: " + value);            
		}
	}
}
