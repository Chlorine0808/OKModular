package ruiseki.okmodular.common.tile;

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
 * シンボル位置と一緒にパターンセルを記録している配線の検証。
 *
 * ============================================
 * なぜソース走査なのか
 * ============================================
 *
 * `TEMachineController` は**ユニットテストで組めない**（`MockWorld` がコンストラクタで NPE。
 * `release_freeze.md` §5 の恒久制約）。計算そのものは `StructureCellLocatorTest` が
 * 値で縛っているので、ここで見るのは**その計算が呼ばれているか**だけ。
 *
 * この mod は「機能は書かれていたのに、それを使う側が無い」を 10 件出している。
 * `StructureCellLocator` を書いても `finalizeSymbolPosition` から呼ばなければ、
 * **セルは 1 つも記録されないまま構造 IO を実装することになる**。
 * `ConditionsRegistrationTest` / `VisionVariableDelegationTest` と同じ手口で静的に捕まえる。
 *
 * ============================================
 * なぜコントローラ側が計算するのか
 * ============================================
 *
 * セルの復元には**コントローラの世界座標・向き・コントローラオフセット**の 3 つが要る。
 * 呼び出し元の `CustomStructureRegistry.wrapTracking` は `IMachineController` しか持たず、
 * そこにはどれも生えていない。**インタフェースを広げるより、
 * 3 つとも既に持っている側で計算する**ほうが結合が増えない。
 *
 * ============================================
 * オフセットの出どころ
 * ============================================
 *
 * `structureCheck(piece, ox, oy, oz)` が唯一オフセットを受け取る場所で、
 * ここを通ってから走査が始まる。**走査中に読める場所へ写しておかないと、
 * `finalizeSymbolPosition` の時点で値が無い。**
 */
@DisplayName("パターンセルの記録")
public class SymbolCellRecordingTest {

    private static final Path CONTROLLER = Paths
        .get("src/main/java/ruiseki/okmodular/common/tile/TEMachineController.java");

    @Test
    @DisplayName("finalizeSymbolPosition がセルの計算を呼んでいる")
    public void testセルの計算が呼ばれている() {
        String source = read(CONTROLLER);
        String body = methodBody(source, "public void finalizeSymbolPosition(");

        assertTrue(
            body.contains("StructureCellLocator.locate("),
            "finalizeSymbolPosition が StructureCellLocator を呼んでいない。" + "位置だけ記録してセルを捨てると、構造 IO は「どのセルか」を復元できない");
    }

    @Test
    @DisplayName("structureCheck が走査前にコントローラオフセットを控えている")
    public void testオフセットを控えている() {
        String source = read(CONTROLLER);
        String body = methodBody(source, "protected boolean structureCheck(");

        assertTrue(
            body.contains("structureOriginOffset"),
            "structureCheck がコントローラオフセットを控えていない。" + "ここが唯一オフセットを受け取る場所で、走査中に読めるようにしておかないと"
                + "finalizeSymbolPosition の時点で値が無い");
    }

    @Test
    @DisplayName("位置を捨てるときセルも一緒に捨てる")
    public void test消すときも一緒() {
        String source = read(CONTROLLER);
        String body = methodBody(source, "public void clearSymbolPositions(");

        assertTrue(
            body.contains("symbolCells"),
            "clearSymbolPositions がセルを消していない。位置だけ作り直すと、" + "前回の形成で作られたセルが残って古い対応を返す");
    }

    @Test
    @DisplayName("記録したセルを外から引ける")
    public void testセルを引く口がある() {
        assertTrue(read(CONTROLLER).contains("public int[] getSymbolCell("), "記録したセルを読む手段が無い。書く側だけあって読む側が無い形になっている");
    }

    /**
     * メソッド本体を波括弧の対応で切り出す。
     * ファイル全体に対する `contains` だと**別のメソッドにある呼び出しを拾って**
     * 配線されていないのに緑になるので、範囲を絞る。
     */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "メソッドが見つからない: " + signature + " — 改名されたならこのテストの参照も直すこと");

        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, "メソッド本体の開始が見つからない: " + signature);

        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new AssertionError("メソッド本体の終端が見つからない: " + signature);
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file.toAbsolutePath(), e);
        }
    }
}
