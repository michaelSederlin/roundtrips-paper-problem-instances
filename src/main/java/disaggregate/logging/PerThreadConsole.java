package disaggregate.logging;

import java.io.*;

public class PerThreadConsole {
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;
    private static final InheritableThreadLocal<PrintStream> OUT = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<PrintStream> ERR = new InheritableThreadLocal<>();

    static {
        System.setOut(new PrintStream(new DispatchingOutputStream(OUT, ORIGINAL_OUT), true));
        System.setErr(new PrintStream(new DispatchingOutputStream(ERR, ORIGINAL_ERR), true));
    }

    /** Call once early in main() to install the dispatchers. */
    public static void install() { /* triggers static init */ }

    /** Route this thread's stdout/stderr to the given file. Caller closes via {@link #clear()}. */
    public static void redirectTo(java.nio.file.Path file) throws IOException {
        java.nio.file.Files.createDirectories(file.getParent());
        PrintStream ps = new PrintStream(new FileOutputStream(file.toFile(), true), true);
        OUT.set(ps);
        ERR.set(ps);
    }

    public static void clear() {
        PrintStream ps = OUT.get();
        OUT.remove();
        ERR.remove();
        if (ps != null) ps.close();
    }

    private static class DispatchingOutputStream extends OutputStream {
        private final InheritableThreadLocal<PrintStream> tl;
        private final PrintStream fallback;
        DispatchingOutputStream(InheritableThreadLocal<PrintStream> tl, PrintStream fallback) {
            this.tl = tl; this.fallback = fallback;
        }
        private PrintStream target() {
            PrintStream ps = tl.get();
            return ps != null ? ps : fallback;
        }
        @Override public void write(int b) { target().write(b); }
        @Override public void write(byte[] b, int off, int len) { target().write(b, off, len); }
        @Override public void flush() { target().flush(); }
    }
}