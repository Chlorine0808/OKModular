package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `can_see_sky` / `can_see_void` の 2 つの書き方が同じ判定を指していることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * docs は「この 2 つは括弧を省いて変数としても書ける」と言っている。
 * つまり `can_see_sky` と `can_see_sky()` は**同じ質問**でなければならない。
 * そうなっていなかった。
 *
 * - 関数形式 `can_see_sky()` は `VisionFunctionExpression` が
 * **コントローラの 1 つ上から**空に向かってブロックを走査する
 * - 変数形式 `can_see_sky` は `WorldPropertyExpression` が
 * `World.canBlockSeeTheSky(x, y, z)` を**コントローラ自身の座標で**呼んでいた
 *
 * `canBlockSeeTheSky` は chunk の heightMap 比較なので、**その座標のブロック自身を数える**。
 * コントローラは不透過キューブなので heightMap は常に「コントローラの y + 1」になり、
 * **どれだけ空が開けていても答えは必ず false**。`can_see_void` も同じで、
 * `getHeightValue()` は負にならないので必ず false。
 *
 * **変数形式は定数 0 だった。** ゲーム中では「条件が評価されていない」のと見分けがつかない
 * （`can_see_sky == 1` は絶対に始まらず、`can_see_sky == 0` は素通り）。
 * `726a0df` で変数として登録できるようにしたが、登録先の評価が壊れていたので
 * 「読めるようになっただけで効かない」状態が残っていた。
 *
 * ============================================
 * なぜソース走査なのか
 * ============================================
 *
 * 判定には `World` が要り、`MockWorld` はコンストラクタで NPE を出す
 * （`MachineConditionGateTest` に同じ事情が書いてある）。
 * 値で縛れないので、**答えを出している場所**を縛る。
 * `ConditionsRegistrationTest` と同じ手口で、
 * 「機能はあるのに繋がっていない」を静的に捕まえるためのもの。
 *
 * ============================================
 */
@DisplayName("can_see_sky / can_see_void の 2 つの書き方")
public class VisionVariableDelegationTest {

    private static final Path WORLD_PROPERTY = Paths
        .get("src/main/java/ruiseki/okmodular/api/recipe/expression/WorldPropertyExpression.java");

    private static final Path VISION_FUNCTION = Paths
        .get("src/main/java/ruiseki/okmodular/api/recipe/expression/VisionFunctionExpression.java");

    @Test
    @DisplayName("変数形式は関数形式に委譲している")
    public void test変数が関数に委譲している() {
        String source = read(WORLD_PROPERTY);

        assertTrue(
            source.contains("VisionFunctionExpression.SKY.evaluate(context)"),
            "can_see_sky が VisionFunctionExpression に委譲していない。" + "自前で答えると関数形式と食い違う");
        assertTrue(
            source.contains("VisionFunctionExpression.VOID.evaluate(context)"),
            "can_see_void が VisionFunctionExpression に委譲していない");
    }

    @Test
    @DisplayName("自分の座標を数える判定を使っていない")
    public void test自分のブロックを数える判定を使っていない() {
        String source = read(WORLD_PROPERTY);

        // どちらもコントローラ自身のブロックを勘定に入れるので、
        // 不透過キューブであるコントローラでは答えが定数になる。
        // 呼び出しの形（先頭のドットと開き括弧）で探す。名前だけだと説明文まで拾ってしまう。
        assertFalse(source.contains(".canBlockSeeTheSky("), "canBlockSeeTheSky はコントローラ自身のブロックを数えるので、機械では常に false になる");
        assertFalse(source.contains(".getHeightValue("), "getHeightValue は負にならないので、can_see_void が常に false になる");
    }

    @Test
    @DisplayName("委譲先の共有インスタンスが引数なしで作られている")
    public void test共有インスタンスが引数なし() {
        String source = read(VISION_FUNCTION);

        // 引数はブロック ID のフィルタ。変数形式にはそれを書く場所が無いので、空でなければならない。
        assertTrue(
            source.contains("Direction.SKY,") && source.contains("Collections.emptyList()"),
            "SKY / VOID の共有インスタンスが引数なしで作られていない。" + "変数形式にフィルタが混ざる");
    }

    @Test
    @DisplayName("2 つの書き方がどちらもパースできる")
    public void test両方の書き方がパースできる() {
        assertNotNull(ExpressionParser.parseExpression("can_see_sky"));
        assertNotNull(ExpressionParser.parseExpression("can_see_sky()"));
        assertNotNull(ExpressionParser.parseExpression("can_see_void"));
        assertNotNull(ExpressionParser.parseExpression("can_see_void()"));
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file.toAbsolutePath(), e);
        }
    }
}
