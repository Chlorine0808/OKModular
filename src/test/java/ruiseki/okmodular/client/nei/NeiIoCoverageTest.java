package ruiseki.okmodular.client.nei;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * レシピの入出力型に、**NEI で描く側が居る**ことの検証。
 *
 * ============================================
 * 描く側が濾している
 * ============================================
 *
 * `LayoutPartFactory` は入出力の型ごとに描き方を登録する手書きの一覧。
 * 型を足しても**登録を忘れれば NEI に何も出ない**。レシピは正しく動くので、
 * 「NEI に出ない」は不具合ではなく仕様に見える。
 *
 * この mod はこの形を**同じファイルで 2 回**踏んでいる（`_SSOT` の「表示する側が濾している」）。
 * 1 回目を直したとき 2 回目が残った。手書きの一覧は、消すか、
 * **一覧から漏れたことが分かる仕組みを付ける**かのどちらかしかない。
 *
 * ============================================
 * 今わかっていること
 * ============================================
 *
 * `structure` の入出力が**描かれていない**。
 * 構造そのものを入力に取るレシピは NEI 上で「材料の無いレシピ」に見え、
 * 何を建てればいいのかが表示から分からない。
 *
 * 実装を待っているだけなので消すわけにはいかない。**名指しで残す**。
 */
@DisplayName("NEI が描く入出力型")
public class NeiIoCoverageTest {

    private static final Path IO = JavaSource.MAIN.resolve("api/recipe/io");
    private static final Path FACTORY = JavaSource.MAIN.resolve("client/nei/modular/layout/LayoutPartFactory.java");

    /**
     * まだ NEI に描き方が無い型。**空にするのが目標**。
     *
     * 構造 IO は「機械の中に何を建てるか」を入出力として扱う仕組みで、
     * アイテムやタンクのように 1 枠で描ける形ではない。プレビューの口
     * （`NEIConfig.registerStructurePreviews`）とどう繋ぐかが決まっていない。
     * 決まるまでは、**描かれていないことがここに書いてある**状態を保つ。
     */
    private static final List<String> NOT_DRAWN = Arrays.asList("StructureInput", "StructureOutput");

    @Test
    @DisplayName("入出力型はすべて描かれるか、描かれないと書いてある")
    public void test描き方の無い型が黙って増えない() {
        Set<String> drawn = registeredInFactory();
        List<String> invisible = new ArrayList<>();

        for (String type : ioTypes()) {
            if (NOT_DRAWN.contains(type)) continue;
            if (!drawn.contains(type)) invisible.add(type);
        }

        assertTrue(
            invisible.isEmpty(),
            invisible + " が LayoutPartFactory に登録されていない。レシピは動くのに NEI には何も出ないので、" + "見た人には「そういう仕様」としか読めない");
    }

    @Test
    @DisplayName("描かれない一覧が古くなっていない")
    public void test未対応の一覧が正しい() {
        Set<String> types = ioTypes();
        Set<String> drawn = registeredInFactory();

        for (String type : NOT_DRAWN) {
            assertTrue(types.contains(type), type + " という入出力型はもう無い。NOT_DRAWN から消すこと");
            assertTrue(!drawn.contains(type), type + " は描かれるようになった。NOT_DRAWN から消すこと");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        Set<String> types = ioTypes();
        Set<String> drawn = registeredInFactory();

        assertTrue(types.size() >= 16, "入出力型が 16 個未満。io パッケージの走査が壊れている: " + types);
        assertTrue(drawn.size() >= 12, "NEI に登録された型が 12 個未満。LayoutPartFactory の走査が壊れている: " + drawn);
        assertTrue(drawn.contains("ItemInput"), "ItemInput すら拾えていない");
    }

    /** `io` パッケージにある具象の入出力型。 */
    private static Set<String> ioTypes() {
        Set<String> found = new TreeSet<>();

        for (Path file : JavaSource.javaFilesUnder(IO)) {
            String name = file.getFileName()
                .toString()
                .replace(".java", "");
            if (!name.endsWith("Input") && !name.endsWith("Output")) continue;
            if (name.startsWith("I") || name.startsWith("Abstract")) continue;
            found.add(name);
        }
        return found;
    }

    /** `LayoutPartFactory` が `register(X.class, ...)` している型。 */
    private static Set<String> registeredInFactory() {
        Set<String> found = new TreeSet<>();
        Matcher hits = Pattern.compile("register\\(\\s*([A-Za-z]+)\\.class")
            .matcher(JavaSource.stripComments(JavaSource.read(FACTORY)));

        while (hits.find()) found.add(hits.group(1));
        return found;
    }
}
