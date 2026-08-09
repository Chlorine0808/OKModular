package ruiseki.okmodular.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 設定の値に**読む側が居る**ことの検証。
 *
 * ============================================
 * なぜこれを見るのか
 * ============================================
 *
 * 設定は「書いたら効く」ように見える。config ファイルに項目が出て、GUI にも出て、
 * 値を変えれば保存される。**読み手が居ないことだけが、どこにも現れない。**
 *
 * プレイヤーから見ると「設定したのに変わらない」で、mod のバグとして報告される。
 * 開発側から見ると項目は生きているので、再現しようとしても何も起きない。
 *
 * ============================================
 * 名前だけで数えると何が起きるか
 * ============================================
 *
 * 素朴に「フィールド名が他に出てくるか」で数えると、**同名の無関係な識別子を拾う**。
 * 実際 `WorldGenConfig.helium` を素の名前で探すと `EnumFluidMaterial` と
 * `EnumMaterial` と `ModFluidGases` が並び、読み手が 3 人居るように見える。
 * どれもガスの名前であって設定ではない。
 *
 * だから「`.名前` の形で参照されている」か「宣言したファイルの中で使われている」かで数える。
 * 前者は入れ子クラスのフィールド（`cfg.pocketSize`）も拾えるし、
 * 後者は `defaultTintColor` のように **getter 経由でしか読まれない**ものを拾える。
 *
 * ============================================
 * getter まで見る理由
 * ============================================
 *
 * フィールドに読み手が居ても、それが**誰も呼ばない getter だけ**なら結局死んでいる。
 * `defaultTintColor` は `getDefaultTintColorInt()` からしか読まれていないので、
 * その getter を呼ぶ側まで辿らないと「読まれている」と言い切れない。
 */
@DisplayName("設定に読む側が居る")
public class ConfigReachabilityTest {

    private static final Path CONFIG = JavaSource.MAIN.resolve("config");

    @Test
    @DisplayName("@Config を付けたフィールドに読み手が居る")
    public void test読まれない設定が無い() {
        List<String> unread = new ArrayList<>();
        List<String> fields = new ArrayList<>();

        for (Path file : JavaSource.javaFilesUnder(CONFIG)) {
            String source = JavaSource.stripComments(JavaSource.read(file));

            for (String field : configFields(source)) {
                fields.add(field);
                if (!isRead(field, file, source)) unread.add(
                    file.getFileName()
                        .toString()
                        .replace(".java", "") + "#"
                        + field);
            }
        }

        assertTrue(fields.size() >= 10, "@Config のフィールドが 10 個未満しか読めていない。走査が壊れている: " + fields);
        assertTrue(unread.isEmpty(), unread + " は config に出て GUI にも出るのに、値を読む側が居ない。" + "プレイヤーからは「設定しても変わらない」としか見えない");
    }

    @Test
    @DisplayName("設定の getter に呼ぶ側が居る")
    public void test呼ばれない_getter_が無い() {
        List<String> unused = new ArrayList<>();
        List<String> accessors = new ArrayList<>();

        for (Path file : JavaSource.javaFilesUnder(CONFIG)) {
            String owner = file.getFileName()
                .toString()
                .replace(".java", "");

            for (String method : publicStaticMethods(JavaSource.stripComments(JavaSource.read(file)))) {
                if (accessors.contains(method)) continue;
                accessors.add(method);
                if (callerCount(owner + "." + method + "(") == 0) unused.add(owner + "#" + method);
            }
        }

        assertTrue(accessors.size() >= 5, "設定の public static メソッドが 5 個未満。走査が壊れている: " + accessors);
        assertTrue(unused.isEmpty(), unused + " を誰も呼んでいない。その先で読んでいる設定ごと死ぬ");
    }

    @Test
    @DisplayName("文字列で名指ししたクラスが実在する")
    public void test名指しされたクラスが在る() {
        // GUI_FACTORY は @Mod(guiFactory = ...) に渡す**クラス名の文字列**。
        // 綴りを間違えても、クラスを動かしても、コンパイラは何も言わない。
        // 効果は「設定画面のボタンが出ない」だけなので、気づくのはずっと後になる。
        String reference = JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve("Reference.java")));
        Matcher named = Pattern.compile("GUI_FACTORY\\s*=\\s*[^;]*\"\\.?([\\w.]+)\"")
            .matcher(reference);

        assertTrue(named.find(), "Reference に GUI_FACTORY が無い。@Mod(guiFactory = ...) の指す先が追えない");

        String tail = named.group(1)
            .replace('.', '/');
        assertTrue(
            Files.exists(JavaSource.MAIN.resolve(tail + ".java")),
            "GUI_FACTORY が指す " + named.group(1) + " というクラスが無い。設定画面のボタンが出ないだけなので、実機でも気づきにくい");
    }

    // ============================================================

    /** `@Config.` が付いた宣言のフィールド名。 */
    private static List<String> configFields(String source) {
        Matcher declarations = Pattern
            .compile(
                "@Config\\.[A-Za-z]+[^;{]*?(?:public|protected)\\s+(?:static\\s+)?(?:final\\s+)?"
                    + "[\\w.<>\\[\\]]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*[=;]",
                Pattern.DOTALL)
            .matcher(source);

        List<String> found = new ArrayList<>();
        while (declarations.find()) {
            if (!found.contains(declarations.group(1))) found.add(declarations.group(1));
        }
        return found;
    }

    /** `.名前` で参照されているか、宣言したファイルの中で使われているか。 */
    private static boolean isRead(String field, Path declaredIn, String declaringSource) {
        if (callerCount("." + field) > 0) return true;

        // 自分のファイルの中は無修飾で書かれる。宣言そのものを引いて数える。
        int mentions = count(declaringSource, Pattern.compile("\\b" + Pattern.quote(field) + "\\b"));
        int declarations = count(
            declaringSource,
            Pattern.compile(
                "(?:public|protected)\\s+(?:static\\s+)?(?:final\\s+)?[\\w.<>\\[\\]]+\\s+" + Pattern.quote(field)
                    + "\\s*[=;]"));
        return mentions - declarations > 0;
    }

    private static List<String> publicStaticMethods(String source) {
        Matcher declarations = Pattern
            .compile("(?m)^[ \\t]*public\\s+static\\s+[\\w.<>\\[\\],? ]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
            .matcher(source);

        List<String> found = new ArrayList<>();
        while (declarations.find()) {
            if (!found.contains(declarations.group(1))) found.add(declarations.group(1));
        }
        return found;
    }

    private static int callerCount(String literal) {
        int found = 0;
        for (Path file : JavaSource.allMainSources()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            int at = source.indexOf(literal);
            while (at >= 0) {
                found++;
                at = source.indexOf(literal, at + 1);
            }
        }
        return found;
    }

    private static int count(String source, Pattern pattern) {
        Matcher hits = pattern.matcher(source);
        int found = 0;
        while (hits.find()) found++;
        return found;
    }
}
