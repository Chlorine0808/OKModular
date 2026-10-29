package ruiseki.okmodular.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** The errors.txt beside a folder of JSON files; a load without errors removes the old one. */
public final class ErrorsFile {

    public static final String NAME = "errors.txt";

    private ErrorsFile() {}

    public static void write(File dir, String title, List<String> errors) {
        File file = new File(dir, NAME);
        if (errors.isEmpty()) {
            if (file.exists() && !file.delete()) Logger.warn("Could not remove {}", file.getAbsolutePath());
            return;
        }
        try (PrintWriter w = new PrintWriter(
            new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            w.println("=== " + title + " ===");
            w.println("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            w.println("Error count: " + errors.size());
            w.println();
            for (int i = 0; i < errors.size(); i++) {
                w.println((i + 1) + ". " + errors.get(i));
                w.println();
            }
        } catch (IOException e) {
            Logger.error("Failed to write {}", file.getAbsolutePath(), e);
        }
    }
}
