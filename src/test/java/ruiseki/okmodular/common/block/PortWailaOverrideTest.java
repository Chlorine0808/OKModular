package ruiseki.okmodular.common.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ポートブロックが `getWailaInfo` を override するとき `super` を呼んでいることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `AbstractPortBlock.getWailaInfo` は**全ポート共通の行**を出す（塗った色、面ごとの IO）。
 * 具象ブロックが `super` を呼ばずに override すると、**共通の行だけが黙って消える**。
 * 例外も警告も出ない。「WAILA に何も出ない」のではなく「一部だけ出ない」ので気づきにくい。
 *
 * 実際に落ちていた。A1 でポートの色の行を親に足したが、**6 クラスが `super` を
 * 呼んでいなかった**ため、エネルギー / マナ / ME のポートでは色が出なかった
 * （fluid / gas / item は呼んでいたので出ていた）。**実機で指摘されるまで分からなかった。**
 *
 * `AbstractPortBlock.onBlockPlacedBy` が `super` を呼ばないせいでドロップアイテムの
 * NBT が読み戻されていなかったのと**同じ失敗**。この形は繰り返し出るので、
 * 気をつけるのではなくテストで縛る。
 *
 * ============================================
 * なぜソースを読むのか
 * ============================================
 *
 * `getWailaInfo` は WAILA の型と TileEntity を要求するのでゲーム外では呼べない。
 * だが**「super を呼んでいるか」はソースを見れば分かる**。実行できないことを
 * 理由にテストを諦めるより、読める形で読む。
 *
 * 期待値は**実在するファイルからの生きた列挙**なので、新しいポートブロックを
 * 足して `super` を忘れたら落ちる。
 *
 * ============================================
 */
@DisplayName("ポートブロックの getWailaInfo")
public class PortWailaOverrideTest {

    private static final File BLOCK_DIR = new File("src/main/java/ruiseki/okmodular/common/block");

    @Test
    @DisplayName("override するなら super.getWailaInfo を呼んでいる")
    void overrides_call_super() {
        List<String> offenders = new ArrayList<>();

        for (File file : sourceFiles()) {
            String body = read(file);
            if (!body.contains("extends AbstractPortBlock")) continue;
            if (!body.contains("public void getWailaInfo")) continue;
            if (!body.contains("super.getWailaInfo")) {
                offenders.add(
                    file.getName()
                        .replace(".java", ""));
            }
        }

        assertTrue(offenders.isEmpty(), "super.getWailaInfo を呼んでいない: " + offenders);
    }

    @Test
    @DisplayName("共通の行を出す親が実際に存在する")
    void the_base_class_still_adds_the_shared_lines() {
        // 親が共通の行を出さなくなったら、このテストが守っている契約自体が消える。
        String base = read(new File(BLOCK_DIR, "AbstractPortBlock.java"));
        assertTrue(base.contains("public void getWailaInfo"), "AbstractPortBlock が getWailaInfo を持たない");
        assertTrue(base.contains("getPortColor"), "AbstractPortBlock の getWailaInfo が色を出していない");
    }

    @Test
    @DisplayName("列挙の仕組みが生きている")
    void the_enumeration_finds_something() {
        // 0 件を緑と読み違えないための番犬。パス変更やリファクタで
        // ディレクトリが空振りしたら、上のテストは何も検証せずに通ってしまう。
        Set<String> found = new TreeSet<>();
        for (File file : sourceFiles()) {
            String body = read(file);
            if (body.contains("extends AbstractPortBlock") && body.contains("public void getWailaInfo")) {
                found.add(
                    file.getName()
                        .replace(".java", ""));
            }
        }
        assertFalse(found.isEmpty(), "getWailaInfo を override するポートブロックが 1 つも見つからない");
    }

    private static File[] sourceFiles() {
        File[] files = BLOCK_DIR.listFiles((dir, name) -> name.endsWith(".java"));
        assertNotNull(files, "block パッケージが読めない: " + BLOCK_DIR.getAbsolutePath());
        return files;
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
