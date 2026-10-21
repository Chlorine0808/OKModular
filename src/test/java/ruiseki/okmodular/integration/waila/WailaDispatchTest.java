package ruiseki.okmodular.integration.waila;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * WAILA の口に**配る側が居る**ことの検証。
 *
 * ============================================
 * 何が起きていたか
 * ============================================
 *
 * 機械が独立した mod になったとき、`IWaila*InfoProvider` と `WailaUtils` は移ってきたが、
 * **それを WAILA に配る `BlockProvider` が親に残った**。
 * 15 個ほどのブロックが `getWailaInfo` を書いているのに、呼ぶ側がどこにも居なかった。
 *
 * ツールチップが**間違って出る**のではなく**出ない**ので、
 * 「この mod は WAILA に対応していないのだろう」と読めてしまうのが厄介なところ。
 *
 * OKCore も provider を持っているが、これは助けにならない。OKCore が登録するのは
 * `ruiseki.okcore.addon.waila.IWailaBlockInfoProvider` で、
 * ここのブロックが実装しているのは `ruiseki.okmodular.integration.waila` の同名の別型。
 * WAILA は**クラスで**照合するので一致しない。
 * 「同じものに綴りが 2 つあって、別々に答える」がパッケージ境界で起きた形。
 *
 * `PortWailaOverrideTest` はこの間ずっと緑だった。
 * **一度も呼ばれないコードの中で `super` が呼ばれていることを、正しく確かめていた。**
 *
 * ============================================
 * 何を縛るのか
 * ============================================
 *
 * インタフェースが宣言した口は、**全部 `BlockProvider` から呼ばれていること**。
 * 1 つ足して配り忘れると、その口だけが黙って死ぬ。今回死んだのは全部だったが、
 * 次に起きるのは 1 つだけのはず。
 */
@DisplayName("WAILA の口に配る側が居る")
public class WailaDispatchTest {

    private static final Path WAILA = JavaSource.MAIN.resolve("integration/waila");
    private static final Path PROVIDER = WAILA.resolve("BlockProvider.java");
    private static final Path MOD = JavaSource.MAIN.resolve("OKModular.java");

    /** 配る対象。ここに無いインタフェースを足しても、それは誰にも届かない。 */
    private static final List<String> DISPATCHED = Arrays
        .asList("IWailaBlockInfoProvider.java", "IWailaTileInfoProvider.java", "IWailaNBTProvider.java");

    @Test
    @DisplayName("インタフェースの口を BlockProvider が全部呼んでいる")
    public void test配られていない口が無い() {
        String provider = JavaSource.stripComments(JavaSource.read(PROVIDER));
        List<String> orphans = new ArrayList<>();
        List<String> hooks = new ArrayList<>();

        for (String name : DISPATCHED) {
            for (String hook : declaredMethods(WAILA.resolve(name))) {
                hooks.add(hook);
                if (!provider.contains("." + hook + "(")) orphans.add(name.replace(".java", "") + "#" + hook);
            }
        }

        // 署名の読み取りが空振りすると、このテストは何も確かめずに緑になる。
        assertTrue(hooks.size() >= 5, "インタフェースの口が 5 個未満しか読めていない。署名の走査が壊れている: " + hooks);
        assertTrue(orphans.isEmpty(), orphans + " を BlockProvider が呼んでいない。実装したブロックからは「書いたのに出ない」としか見えず、" + "例外もログも出ない");
    }

    @Test
    @DisplayName("BlockProvider が WAILA に登録されている")
    public void test登録する側が居る() {
        String mod = JavaSource.stripComments(JavaSource.read(MOD));

        assertTrue(
            mod.contains("BlockProvider.init()"),
            "OKModular が BlockProvider.init() を呼んでいない。provider を書いても WAILA に名乗り出なければ" + "一度も呼ばれない。分割のときに落ちたのはまさにこの一段");

        String provider = JavaSource.stripComments(JavaSource.read(PROVIDER));
        assertTrue(provider.contains("registerBodyProvider"), "BlockProvider が body を登録していない。getWailaInfo が届かない");
        assertTrue(provider.contains("registerNBTProvider"), "BlockProvider が NBT を登録していない。サーバ側の値が届かない");
    }

    @Test
    @DisplayName("登録するクラスがブロックの実際の親である")
    public void test登録の型が広い() {
        // WAILA はここで渡したクラスでしか照合しない。インタフェースを渡すと
        // 「OKCore が自分のインタフェースで登録していて一致しない」のと同じ穴に落ちる。
        String provider = JavaSource.stripComments(JavaSource.read(PROVIDER));

        assertTrue(provider.contains("BlockOK.class"), "ブロックを BlockOK.class で登録していない。ここのブロックは全部 BlockOK の子孫");
        assertTrue(provider.contains("TileEntityOK.class"), "TileEntity を TileEntityOK.class で登録していない");
    }

    @Test
    @DisplayName("配る先の無いインタフェースが残っていない")
    public void test宛先の無い口が無い() {
        // 実装するクラスも配る側も居ないインタフェースは、次に誰かが実装したときに
        // 「書いたのに出ない」を再生産する。
        List<String> stranded = new ArrayList<>();

        for (Path file : JavaSource.javaFilesUnder(WAILA)) {
            String name = file.getFileName()
                .toString();
            if (!name.startsWith("IWaila")) continue;
            if (DISPATCHED.contains(name)) continue;
            stranded.add(name);
        }

        assertTrue(stranded.isEmpty(), stranded + " は BlockProvider が配らないインタフェース。配るか、消すこと");
    }

    /** そのインタフェースが宣言しているメソッドの名前。 */
    private static List<String> declaredMethods(Path file) {
        Pattern signature = Pattern
            .compile("(?m)^[ \\t]*(?:default\\s+)?[\\w.<>\\[\\],? ]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^)]*\\)");
        List<String> found = new ArrayList<>();
        Matcher hits = signature.matcher(JavaSource.stripComments(JavaSource.read(file)));

        while (hits.find()) found.add(hits.group(1));
        return found;
    }
}
