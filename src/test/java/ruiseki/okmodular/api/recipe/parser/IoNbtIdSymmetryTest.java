package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.recipe.io.StructureInput;
import ruiseki.okmodular.api.recipe.io.StructureOutput;
import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 入出力を NBT に書いたとき、**読み戻せる名前で書いている**ことの検証。
 *
 * ============================================
 * 綴りが 2 つある所
 * ============================================
 *
 * 実行中の機械は `cachedOutputs` と毎 tick 入出力を NBT に載せる。
 * 読み戻すときは `nbt.getString("id")` を `InputNBTRegistry` / `OutputNBTRegistry` で引く。
 *
 * つまり **同じ名前が 2 箇所に手で書かれている**。片方だけ変えると
 * `read` は「知らない型」として null を返し、ログに 1 行出して終わる。
 * その入出力は**セーブして入り直した瞬間に消える**。
 * 機械は動き続けるので、消えたことは払い出しを数えるまで分からない。
 *
 * ============================================
 * なぜ実際に書かせて確かめないのか
 * ============================================
 *
 * レジストリの供給関数は `new ItemInput((ItemStack) null)` のような**空の殻**で、
 * 読み戻す前の器としてしか成立しない。それに `writeToNBT` させても本番の経路ではない。
 * 本物を作るには JSON のパースが要り、そこには `GameRegistry` が要る（D-2）。
 *
 * 名前の一致だけならソースで確かめられるので、そこまでを常設にする。
 */
@DisplayName("入出力の NBT 型名")
public class IoNbtIdSymmetryTest {

    private static final Path IO = JavaSource.MAIN.resolve("api/recipe/io");
    private static final Path PARSER = JavaSource.MAIN.resolve("api/recipe/parser");

    @Test
    @DisplayName("書き出す型名はすべて登録されている")
    public void test読み戻せない型名が無い() {
        Set<String> registered = registeredIds();
        List<String> orphans = new ArrayList<>();

        for (Map.Entry<String, String> written : writtenIds().entrySet()) {
            if (!registered.contains(written.getValue()))
                orphans.add(written.getKey() + " が \"" + written.getValue() + "\"");
        }

        assertTrue(
            orphans.isEmpty(),
            orphans + " を書き出しているが、その名前で登録されていない。読み戻しは null を返してログを 1 行出すだけなので、"
                + "セーブして入り直すとその入出力が消える。動き続けるので気づくのは払い出しを数えたとき");
    }

    @Test
    @DisplayName("登録された型名はすべて誰かが書き出す")
    public void test誰も書かない型名が無い() {
        Set<String> written = new TreeSet<>(writtenIds().values());
        List<String> unused = new ArrayList<>();

        for (String id : registeredIds()) {
            if (!written.contains(id)) unused.add(id);
        }

        assertTrue(unused.isEmpty(), unused + " を登録しているが、その名前で書き出す入出力型が無い。読む側だけがある状態");
    }

    @Test
    @DisplayName("定数で書いている型名も同じ名前を指している")
    public void test定数経由の型名が揃っている() {
        // StructureInput / StructureOutput だけは文字列ではなく TYPE 定数を書く。
        // 定数にすると綴り違いは起こらないが、**登録側が別の定数を使っていたら**同じことになる。
        Set<String> registered = registeredIds();

        assertTrue(
            registered.contains(StructureInput.TYPE),
            "StructureInput.TYPE (" + StructureInput.TYPE + ") が登録されていない");
        assertTrue(
            registered.contains(StructureOutput.TYPE),
            "StructureOutput.TYPE (" + StructureOutput.TYPE + ") が登録されていない");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        assertTrue(writtenIds().size() >= 16, "型名を書き出すクラスが 16 個未満。io パッケージの走査が壊れている: " + writtenIds());
        assertTrue(registeredIds().size() >= 9, "登録された型名が 9 個未満。レジストリの走査が壊れている: " + registeredIds());
    }

    /** クラス名 → そのクラスが `setString("id", ...)` で書く名前。 */
    private static Map<String, String> writtenIds() {
        Map<String, String> found = new LinkedHashMap<>();
        Pattern literal = Pattern.compile("setString\\(\\s*\"id\"\\s*,\\s*\"([a-zA-Z_0-9]+)\"");
        Pattern constant = Pattern.compile("setString\\(\\s*\"id\"\\s*,\\s*TYPE\\s*\\)");

        for (Path file : JavaSource.javaFilesUnder(IO)) {
            String name = file.getFileName()
                .toString()
                .replace(".java", "");
            String source = JavaSource.stripComments(JavaSource.read(file));

            Matcher hit = literal.matcher(source);
            if (hit.find()) {
                found.put(name, hit.group(1));
                continue;
            }
            if (constant.matcher(source)
                .find()) {
                found.put(name, constantValueOf(source));
            }
        }
        return found;
    }

    /** `public static final String TYPE = "x";` の x。 */
    private static String constantValueOf(String source) {
        Matcher hit = Pattern.compile("String\\s+TYPE\\s*=\\s*\"([a-zA-Z_0-9]+)\"")
            .matcher(source);
        return hit.find() ? hit.group(1) : "";
    }

    /** `InputNBTRegistry` / `OutputNBTRegistry` が登録している名前。 */
    private static Set<String> registeredIds() {
        Set<String> found = new TreeSet<>();
        Pattern literal = Pattern.compile("register\\(\\s*\"([a-zA-Z_0-9]+)\"");
        Pattern constant = Pattern.compile("register\\(\\s*([A-Za-z]+)\\.TYPE");

        for (Path file : JavaSource.javaFilesUnder(PARSER)) {
            if (!file.getFileName()
                .toString()
                .endsWith("NBTRegistry.java")) continue;

            String source = JavaSource.stripComments(JavaSource.read(file));
            Matcher hits = literal.matcher(source);
            while (hits.find()) found.add(hits.group(1));

            Matcher viaConstant = constant.matcher(source);
            while (viaConstant.find()) {
                found.add(constantValueOf(JavaSource.read(IO.resolve(viaConstant.group(1) + ".java"))));
            }
        }
        return found;
    }
}
