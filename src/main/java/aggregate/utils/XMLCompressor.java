package aggregate.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

public class XMLCompressor {
    public static void compressXML(String inputPath, boolean dropOriginal) throws IOException{
        compressXML(inputPath, 5, dropOriginal);
    }
    public static void compressXML(java.nio.file.Path inputPath, int compressionLevel, boolean dropOriginal) throws IOException {
        compressXML(inputPath.toString(), compressionLevel, dropOriginal);
    }
    public static void compressXML(String inputPath, int compressionLevel, boolean dropOriginal) throws IOException {
        String outputPath = inputPath.endsWith(".gz") ? inputPath : inputPath + ".gz";
        compressXML(inputPath, outputPath, compressionLevel, dropOriginal);
    }

    public static void compressXML(String inputPath, String outputPath, int compressionLevel, boolean dropOriginal) throws IOException {
        assert compressionLevel >= 0 && compressionLevel <= 9 : "Invalid compression level";
        assert Paths.get(outputPath).getParent().toFile().exists() : "Output directory does not exist";

        try (
            FileInputStream fis = new FileInputStream(inputPath);
            FileOutputStream fos = new FileOutputStream(outputPath);
            GZIPOutputStream gzos = new GZIPOutputStream(fos) {{
                def.setLevel(compressionLevel);
            }}) 
        {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzos.write(buffer, 0, len);
            }
        }

        if (dropOriginal) {
            Files.deleteIfExists(Paths.get(inputPath));
        }
    }
}
