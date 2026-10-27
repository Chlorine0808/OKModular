package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * Every decorator has a line for NEI, and every key those lines use is in both lang files.
 * <p>
 * The keys are handed to NEI inside a DecoratorDescription rather than passed to a translate
 * call, so LangKeyCoverageTest does not see them. Without a description a decorator is invisible
 * in NEI while the recipe still runs it, which reads as "this recipe does nothing extra".
 */
@DisplayName("デコレータの説明が NEI に出る")
public class DecoratorDescriptionCoverageTest {

    private static final String[] LANGUAGES = { "en_US", "ja_JP" };
    private static final Pattern KEY = Pattern.compile("\"(okmodular\\.nei\\.decorator\\.[\\w.]+)\"");
    private static final String OVERRIDE = "public List<DecoratorDescription> describe()";

    @Test
    @DisplayName("すべてのデコレータが説明を持つ")
    public void testEveryDecoratorDescribesItself() {
        List<String> missing = new ArrayList<>();
        for (Path file : decorators()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            if (!source.contains(OVERRIDE) || !KEY.matcher(source)
                .find()) missing.add(JavaSource.label(file));
        }
        assertTrue(missing.isEmpty(), missing + " に NEI の説明が無い。describe() を実装し、lang に文言を足すこと");
    }

    @Test
    @DisplayName("説明のキーは両言語に存在する")
    public void testKeysAreTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path file : JavaSource.allMainSources()) {
            Matcher matcher = KEY.matcher(JavaSource.stripComments(JavaSource.read(file)));
            while (matcher.find()) keys.add(matcher.group(1));
        }
        assertFalse(keys.isEmpty(), "説明のキーが 1 つも見つからない。走査が壊れている");

        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<String, String> entries = load(language);
            for (String key : keys) {
                String value = entries.get(key);
                if (value == null || value.isEmpty()) problems.add(language + ": " + key);
            }
        }
        assertTrue(problems.isEmpty(), "訳の無い説明は NEI に生のキーで出る: " + problems);
    }

    @Test
    @DisplayName("走査がデコレータを取りこぼしていない")
    public void testScanFindsDecorators() {
        assertTrue(decorators().size() >= 8, "デコレータが " + decorators().size() + " 個しか見えていない。走査が壊れている");
    }

    /** Concrete subclasses of RecipeDecorator anywhere in the main sources. */
    private static List<Path> decorators() {
        List<Path> found = new ArrayList<>();
        for (Path file : JavaSource.allMainSources()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            if (source.contains("extends RecipeDecorator") && !source.contains("abstract class")) found.add(file);
        }
        return found;
    }

    private static Map<String, String> load(String language) throws IOException {
        Map<String, String> entries = new HashMap<>();
        Path path = Paths.get("src/main/resources/assets/okmodular/lang/" + language + ".lang");
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            int split = line.indexOf('=');
            if (line.startsWith("#") || split <= 0) continue;
            entries.put(
                line.substring(0, split)
                    .trim(),
                line.substring(split + 1));
        }
        return entries;
    }
}
