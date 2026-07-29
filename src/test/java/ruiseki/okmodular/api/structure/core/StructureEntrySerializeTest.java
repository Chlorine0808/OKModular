package ruiseki.okmodular.api.structure.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * `StructureEntry.serialize()` が書き出す値の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `serialize()` は**ワンドの構造エクスポート**（`StructureScanner` / `StructureJsonWriter`）が
 * 使う経路で、出た JSON はそのまま構造定義として読み込まれる。
 * ここが値を取り違えると、**書き出した JSON が元の構造と違う機械になる**。
 *
 * 実際に取り違えていた。`batchMax` のキーに `batchMin` の値を渡していた。
 *
 * ```java
 * serializeExpr(json, "batchMax", batchMin, batchMaxExpr, 1.0); // 第 3 引数が batchMin
 * ```
 *
 * `batchMax` が定数で `batchMin != 1.0` のときだけ現れるので、
 * **既定値のまま使っている限り一度も表面化しない**。
 * 4 つ並んだ同形の呼び出しの 1 つだけが違うという、目で追うと飛ばす形。
 *
 * ============================================
 * なぜ「値」を見るのか
 * ============================================
 *
 * キーの有無だけを見るテストではこの取り違えを検出できない
 * （`batchMax` というキー自体は出ていた）。**値が入れ替わる欠陥**なので、
 * min と max に**別の値**を入れて、それぞれが正しい側に出ることを見る必要がある。
 *
 * ============================================
 */
@DisplayName("構造エントリの書き出し")
public class StructureEntrySerializeTest {

    /** 既定値（1.0）と異なり、かつ互いに異なる値。取り違えが見えるようにする。 */
    private static final int BATCH_MIN = 2;
    private static final int BATCH_MAX = 5;

    private static JsonObject serializeWithBatchLimits(int min, int max) {
        return new StructureEntryBuilder().setName("test")
            .setBatchMin(min)
            .setBatchMax(max)
            .build()
            .serialize();
    }

    @Test
    @DisplayName("batchMin と batchMax がそれぞれ自分の値で出る")
    public void testバッチ上下限が入れ替わらない() {
        JsonObject json = serializeWithBatchLimits(BATCH_MIN, BATCH_MAX);

        assertTrue(json.has("batchMin"), "batchMin が出ていない");
        assertTrue(json.has("batchMax"), "batchMax が出ていない");

        assertEquals(
            BATCH_MIN,
            json.get("batchMin")
                .getAsInt(),
            "batchMin の値が違う");
        assertEquals(
            BATCH_MAX,
            json.get("batchMax")
                .getAsInt(),
            "batchMax に別の値が入っている（batchMin の値が書かれていた欠陥）");
    }

    @Test
    @DisplayName("速度とエネルギーの乗数も入れ替わらない")
    public void test乗数が入れ替わらない() {
        // batchMax と同じ形の呼び出しが 4 つ並んでいるので、隣も確かめる。
        JsonObject json = new StructureEntryBuilder().setName("test")
            .setSpeedMultiplier(2.5)
            .setEnergyMultiplier(0.5)
            .build()
            .serialize();

        assertEquals(
            2.5,
            json.get("speedMultiplier")
                .getAsDouble(),
            "speedMultiplier の値が違う");
        assertEquals(
            0.5,
            json.get("energyMultiplier")
                .getAsDouble(),
            "energyMultiplier の値が違う");
    }

    @Test
    @DisplayName("既定値のままなら書き出さない")
    public void test既定値は省略される() {
        // 既定値を書き出さないのが元の設計。**この欠陥が長く残った理由でもある** —
        // 既定のままだと batchMax の行がそもそも出ないので、取り違えが表に出ない。
        JsonObject json = new StructureEntryBuilder().setName("test")
            .build()
            .serialize();

        assertFalse(json.has("batchMin"), "既定値なのに batchMin が出ている");
        assertFalse(json.has("batchMax"), "既定値なのに batchMax が出ている");
        assertFalse(json.has("speedMultiplier"), "既定値なのに speedMultiplier が出ている");
        assertFalse(json.has("energyMultiplier"), "既定値なのに energyMultiplier が出ている");
    }

    @Test
    @DisplayName("min だけ既定値から動かしても max は出ない")
    public void test片方だけ動かしたとき() {
        // 欠陥の実際の現れ方。batchMin だけ設定すると、
        // 修正前は **batchMax として batchMin の値が出ていた**（batchMax は既定のまま）。
        JsonObject json = new StructureEntryBuilder().setName("test")
            .setBatchMin(3)
            .build()
            .serialize();

        assertEquals(
            3,
            json.get("batchMin")
                .getAsInt());
        assertFalse(json.has("batchMax"), "batchMax は既定値なので出てはいけない（batchMin の値が漏れていた）");
    }
}
