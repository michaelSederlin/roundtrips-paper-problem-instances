package renate.utils;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class XMLWriter implements AutoCloseable {

    private final Writer out;
    private boolean startTagOpen = false;
    private int indent = 0;
    private final String indentStr = "  ";

    public XMLWriter(Writer out) {
        this.out = out;
    }

    public void startDocument() throws IOException {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    public void startElement(String name) throws IOException {
        closeStartTagIfNeeded();
        writeIndent();
        out.write("<");
        out.write(name);
        startTagOpen = true;
        indent++;
    }

    public void attribute(String name, String value) throws IOException {
        if (!startTagOpen) {
            throw new IllegalStateException("No open start tag to write an attribute on.");
        }
        out.write(" ");
        out.write(name);
        out.write("=\"");
        escape(value, true);
        out.write("\"");
    }

    public void text(String text) throws IOException {
        closeStartTagIfNeeded();
        escape(text, false);
    }

    public void endElement(String name) throws IOException {
        indent--;
        if (startTagOpen) {
            // <tag .../>
            out.write("/>\n");
            startTagOpen = false;
        } else {
            writeIndent();
            out.write("</");
            out.write(name);
            out.write(">\n");
        }
    }

    private void writeIndent() throws IOException {
        out.write("\n");
        for (int i = 0; i < indent; i++) {
            out.write(indentStr);
        }
    }

    private void closeStartTagIfNeeded() throws IOException {
        if (startTagOpen) {
            out.write(">");
            startTagOpen = false;
        }
    }

    private void escape(String text, boolean isAttribute) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': out.write("&amp;"); break;
                case '<': out.write("&lt;"); break;
                case '>': out.write("&gt;"); break;
                case '"':
                    if (isAttribute) out.write("&quot;");
                    else out.write('"');
                    break;
                default:
                    out.write(c);
            }
        }
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
