package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;

/**
 * 出力パターンをどのアンカーに書くかの選び方の検証。
 *
 * ============================================
 * なぜこれが要るのか
 * ============================================
 *
 * 入力側は自分で安全になる — **全セルが一致したときだけ**アンカーとして数えるので、
 * 壁に食い込む位置は勝手に外れる。
 *
 * **出力側にはその自浄作用が無い。** 書き込むだけなので、記号に該当するブロックが複数あると
 * 「最初に見つかったもの」に書く。`SpatialCrafter` のように床の記号が 25 個ある機械では、
 * 隅のアンカーが選ばれた瞬間にパターンが壁を突き抜け、**レシピを走らせている機械自身を
 * 上書きして壊す**。例外も出ないし、壊れるまで誰も気づかない。
 *
 * 選ぶのは作者ではなく実装なので、**実装の側が「機械を壊さない位置」を選ぶ責任を持つ**。
 * 形成判定が記録した位置は `getSymbolCell` が非 null を返すので、それを避けるだけでよい。
 *
 * ============================================
 * なぜ入力側には同じガードを掛けないのか
 * ============================================
 *
 * 入力の `consume` が構造体のブロックを消すのは、**作者がそのブロックをパターンに描いたとき
 * だけ**。壁のガラスを食べたければガラスと書く必要があり、それは事故ではなく意図。
 * 出力側の危険は「アンカーの選択が作者の手を離れている」ことに由来する。
 */
@DisplayName("構造 IO 出力のアンカー選択")
public class StructureOutputAnchorTest {

    @TempDir
    File dir;

    /** アンカーの 1 つ隣（A 方向に -1）へ 1 ブロック書くだけのパターン。 */
    private static final String PATTERN = "{ 'name': 'pair', 'anchor': 'X',"
        + " 'mappings': { 'O': 'minecraft:obsidian' }, 'layers': [ ['OX'] ] }";

    @BeforeEach
    public void setUp() {
        try {
            Files.write(
                new File(dir, "pair.json").toPath(),
                PATTERN.replace('\'', '"')
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StructurePatternLoader.getInstance()
            .loadFrom(dir);
    }

    private static StructureOutput output(int amount) {
        return new StructureOutput('D', "pair", amount, false);
    }

    @Test
    @DisplayName("機械のブロックに重なるアンカーは選ばない")
    public void test機械を壊すアンカーは飛ばす() {
        StubContext context = new StubContext();
        // 先に来るほうが「壊れる」アンカー。順番で通ってしまわないことを見る
        context.anchor(1, 0, 0);
        context.anchor(5, 0, 0);
        context.machineBlockAt(0, 0, 0); // アンカー(1,0,0) の書き込み先

        assertTrue(output(1).checkCapacity(context, 1, null), "安全なアンカーが 1 つあるのに書けないと判定している");
        assertFalse(output(2).checkCapacity(context, 2, null), "壊れるアンカーまで数えている。最初に見つかった位置へ書くと、レシピを走らせている機械自身が消える");
    }

    @Test
    @DisplayName("解決できるアンカーが無ければ書けない")
    public void testアンカーが無ければ断る() {
        assertFalse(output(1).checkCapacity(new StubContext(), 1, null), "記号が 1 つも無いのに開始を許すと、入力だけ消えて何も置かれない");
    }

    /**
     * ワールドを持たない最小の文脈。
     *
     * `index` を指定しなければ `getWorld()` は触られないので、`MockWorld` すら要らない
     * （TE を作れないという恒久制約に触れずに、アンカー選択だけを値で確かめられる）。
     *
     * セルと世界座標は 1:1 にしてある。`getSymbolCell` が非 null を返す位置 = 機械の一部。
     */
    private static final class StubContext implements IRecipeContext {

        private final List<ChunkCoordinates> anchors = new ArrayList<>();
        private final Map<String, int[]> tracked = new HashMap<>();

        void anchor(int x, int y, int z) {
            anchors.add(new ChunkCoordinates(x, y, z));
            machineBlockAt(x, y, z); // アンカー自身も構造体の一部
        }

        void machineBlockAt(int x, int y, int z) {
            tracked.put(x + "," + y + "," + z, new int[] { x, y, z });
        }

        @Override
        public List<ChunkCoordinates> getSymbolPositions(char symbol) {
            return symbol == 'D' ? anchors : null;
        }

        @Override
        public int[] getSymbolCell(int x, int y, int z) {
            int[] cell = tracked.get(x + "," + y + "," + z);
            return cell == null ? null : Arrays.copyOf(cell, 3);
        }

        @Override
        public int[] getCellPosition(int a, int b, int c) {
            return new int[] { a, b, c };
        }

        @Override
        public World getWorld() {
            return null;
        }

        @Override
        public ChunkCoordinates getControllerPos() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public IStructureEntry getCurrentStructure() {
            return null;
        }

        @Override
        public ForgeDirection getFacing() {
            return ForgeDirection.NORTH;
        }

        @Override
        public ConditionContext getConditionContext() {
            return null;
        }
    }
}
