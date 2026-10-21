package ruiseki.okmodular.testsupport;

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

/**
 * main のソースを読むテストのための道具。
 *
 * ============================================
 * なぜソースを読むテストがあるのか
 * ============================================
 *
 * この mod が繰り返し踏んでいるのは「機能は書かれていたのに使う側が無い」型のバグで、
 * `_SSOT.md` に **11 件**記録がある。デコレータ 6 種が一度も動いていなかった件が最新で、
 * **1963 個のテストが全部緑のまま**通り抜けた。
 *
 * 値を検査するテストではこの型は捕まらない。呼ばれない関数の中身が正しいかを
 * いくら確かめても、呼ばれないことは分からないため。**「その計算に手が届くか」**は
 * 呼び出し関係の話なので、ソースそのものを見るしかない。
 *
 * ============================================
 * コメントを剥がす理由
 * ============================================
 *
 * 「`processOutputs` を呼んでいる場所」を数えるとき、
 * **それについて説明している javadoc が呼び出しとして数えられる**と、
 * 死んでいることを説明した文章そのものが「生きている」証拠になってしまう。
 * 実際 `ProcessAgent` にも `IModularRecipe` にも、死因を説明する文が入っている。
 *
 * 文字列リテラルの中の `//` で行が切れないよう、リテラルは剥がさずに残す。
 */
public final class JavaSource {

    /** main のソース根。この mod の分だけを見る（Minecraft 本体や依存は対象外）。 */
    public static final Path MAIN = Paths.get("src/main/java/ruiseki/okmodular");

    private JavaSource() {}

    public static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + path, e);
        }
    }

    /** 配下の `.java` を全部。順序を固定するので、失敗メッセージが実行ごとに変わらない。 */
    public static List<Path> javaFilesUnder(Path root) {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .forEach(found::add);
        } catch (IOException e) {
            throw new UncheckedIOException("辿れない: " + root, e);
        }
        Collections.sort(found);
        return found;
    }

    /** main 配下の `.java` を全部。 */
    public static List<Path> allMainSources() {
        return javaFilesUnder(MAIN);
    }

    /** 失敗メッセージ用。`api/recipe/decorator/BonusOutputDecorator.java` の形。 */
    public static String label(Path path) {
        return MAIN.relativize(path)
            .toString()
            .replace('\\', '/');
    }

    /**
     * 行コメントとブロックコメントを落とす。文字列・文字リテラルは中身ごと残す。
     * <p>
     * 行数を保つため改行は消さない。ブロックコメントは空白 1 個に潰すので、
     * `a/**&#47;b` が `ab` という別のトークンに化けることはない。
     */
    public static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();

        while (i < n) {
            char c = source.charAt(i);

            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i = Math.min(i + 2, n);
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n) {
                    char d = source.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\') {
                        if (i < n) {
                            out.append(source.charAt(i));
                            i++;
                        }
                    } else if (d == c) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 署名で始まるメソッドの本体を `{` から対応する `}` まで返す。
     * 見つからなければ例外。**署名を書き換えたのにテストが黙って通る**のを防ぐため、
     * 「無かったから何もしない」は選ばない。
     */
    public static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalStateException("メソッドが見つからない: " + signature);

        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalStateException("メソッドの終わりが見つからない: " + signature);
    }
}
