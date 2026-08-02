package ruiseki.okmodular.api.recipe.context;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ポート列から機械本体を拾う口が 1 つであることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `TEMachineController` は `IModularPort` でもあり `IRecipeContext` でもある。
 * だからポート列を渡された側は、列を走査すれば機械に手が届く。
 *
 * その走査が **10 箇所に private でコピーされていた**（デコレータ 6 つ + ブロック IO 4 つ）。
 * この mod が繰り返し踏んでいる形そのもので、
 * 「同じものに綴りが 2 つあって、別々に答える」がいつでも起こせる状態だった。
 * 今は 10 個とも同じ答えを返すが、それは**たまたま**であって、そう保つ仕組みは無かった。
 *
 * 探される側が探し方を持つ、が正しい置き場所。`IRecipeContext.findIn` に 1 本化した。
 *
 * ============================================
 * 何を縛るのか
 * ============================================
 *
 * 「コピーが消えた」だけでは足りない。**消したあとに誰も文脈を拾わなくなった**なら、
 * デコレータは黙って何もしなくなる（`context == null` で早期 return する形が多い）。
 * 消えたことと、代わりに 1 本化した口を呼んでいることの両方を見る。
 */
@DisplayName("ポート列から機械を拾う口")
public class RecipeContextLookupTest {

    private static final Path MAIN = Paths.get("src/main/java/ruiseki/okmodular");

    /** かつて private コピーを持っていた 10 クラス。 */
    private static final String[] FORMER_COPIES = { "api/recipe/decorator/BonusOutputDecorator.java",
        "api/recipe/decorator/BonusBlockOutputDecorator.java",
        "api/recipe/decorator/PerPositionProbabilityDecorator.java",
        "api/recipe/decorator/RandomBlockOutputDecorator.java", "api/recipe/decorator/WeightedRandomDecorator.java",
        "api/recipe/decorator/HarvestBlockDecorator.java", "api/recipe/io/BlockInput.java",
        "api/recipe/io/BlockOutput.java", "api/recipe/io/StructureInput.java", "api/recipe/io/StructureOutput.java" };

    @Test
    @DisplayName("private なコピーが 1 つも残っていない")
    public void testコピーが残っていない() {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .forEach(p -> {
                    if (read(p).contains("IRecipeContext findRecipeContext(")) {
                        offenders.add(
                            MAIN.relativize(p)
                                .toString());
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(
            offenders.isEmpty(),
            "findRecipeContext を自前で持っているクラスがある: " + offenders + "。IRecipeContext.findIn を呼ぶこと");
    }

    @Test
    @DisplayName("コピーを消したクラスが代わりに findIn を呼んでいる")
    public void test消したあとに呼ぶ側が居る() {
        List<String> silent = new ArrayList<>();

        for (String relative : FORMER_COPIES) {
            String source = read(MAIN.resolve(relative));
            if (!source.replaceAll("\\s+", "")
                .contains("IRecipeContext.findIn(")) {
                silent.add(relative);
            }
        }

        assertTrue(silent.isEmpty(), silent + " がどこからも文脈を拾っていない。デコレータは文脈が null だと黙って何もしないので、" + "コピーを消しただけだと機能が消える");
    }

    @Test
    @DisplayName("ポートが無ければ null を返す")
    public void test空のとき() {
        // 消したコピーは 10 個とも null で NPE を投げた（どれも素の拡張 for だった）。
        // 1 本化したので、一番弱い呼び出し側に合わせて null を許す。
        assertNull(IRecipeContext.findIn(null));
        assertNull(IRecipeContext.findIn(Collections.emptyList()));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + path, e);
        }
    }
}
