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
 * - 変数形式は `WorldPropertyExpression` が chunk の heightMap を読んでいた
 *
 * **`can_see_void` は定数 false だった** — `getHeightValue()` は負にならない。
 *
 * **`can_see_sky` は定数ではなかった。** `canBlockSeeTheSky` は `y >= heightMap` で、
 * heightMap は `Block.getLightOpacity()` から作られる。当時は
 * **この mod のブロックが全部 lightOpacity 0 を返していた**ので機械が heightMap から見えず、
 * 地上の機械では 2 つの綴りが**たまたま同じ答え**になっていた。
 *
 * **「たまたま合っている」を消すのがこのテストの目的。** heightMap 側は
 * **訊かれた座標のブロック自身を数える**ので、`BlockLightOpacityTest` の修正で
 * コントローラが光を遮るようになった今は**どこでも false** になる。
 * 委譲していなければ、この時点で静かに壊れていた。
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

        // どちらも heightMap 由来で、訊かれた座標のブロック自身を勘定に入れる。
        // 呼び出しの形（先頭のドットと開き括弧）で探す。名前だけだと説明文まで拾ってしまう。
        assertFalse(source.contains(".canBlockSeeTheSky("), "canBlockSeeTheSky は heightMap 比較なので、訊かれた座標のブロック自身を数える");
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
