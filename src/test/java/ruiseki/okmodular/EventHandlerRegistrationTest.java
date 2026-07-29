package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `@SubscribeEvent` を持つクラスが実際にイベントバスに登録されていることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **登録されていないハンドラは、書かれているだけで一度も呼ばれない。**
 * 例外もログも出ず、「その機能が存在しない」という形で現れる。
 *
 * この形は分離作業から繰り返し出ている。台帳（`post_split_issues.md`）が
 * 「登録の差分は静的に取れる」として挙げている検出方法を、テストにしたもの。
 *
 * | 事例 | 症状 |
 * |---|---|
 * | `MemoryEventHandler` が未 subscribe | 監視が一度も動いていなかった |
 * | 範囲選択オーバーレイが未登録 | 描画されなかった |
 * | **`WrenchOverlayRenderer` が未登録** | **レンチのオーバーレイが存在しなかった**（実機で指摘されるまで気づかず） |
 *
 * 最後の 1 件は GTNHLib の `@EventBusSubscriber` に頼っていたが発火しなかった。
 * **どこからも参照されないクラスは読み込まれず、読み込まれないクラスは配線されない。**
 * 手で登録する形に寄せた（兄弟の `StructureWandRenderer` と同じ）。
 *
 * ============================================
 * 何を「登録されている」と見なすか
 * ============================================
 *
 * **`EVENT_BUS.register(` を呼んでいるファイルの中で `new クラス名(` されている**なら登録済みと見なす。
 * 空白を除去してから探すので、改行で分かれていても拾う。
 *
 * 最初は `EVENT_BUS.register(new クラス名(` を直接探す形にしたが、**`NEIConfig` を誤検出した** —
 * あれはローカル変数を経由して登録している。
 *
 * ```java
 * NEIConfig config = new NEIConfig();
 * MinecraftForge.EVENT_BUS.register(config);
 * ```
 *
 * **この判定の限界**: 生成と登録が別ファイルに分かれていると見逃す。現状そういう例は無い。
 * 見逃す方向の誤りなので、**誤検出で作業を止めるより安全**。
 *
 * ============================================
 */
@DisplayName("イベントハンドラの登録")
public class EventHandlerRegistrationTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 登録しないと決めたハンドラと、その理由。
     * <p>
     * **理由が書けないなら登録する。** 自動登録の仕組みに任せる場合もここに書く —
     * ただし `WrenchOverlayRenderer` の前例があるので、**その仕組みが実際に発火することを
     * 実機で確かめてから**にすること。
     */
    private static final Map<String, String> INTENTIONALLY_UNREGISTERED = Collections
        .unmodifiableMap(new LinkedHashMap<>());

    @Test
    @DisplayName("@SubscribeEvent を持つクラスは全て登録されている")
    void every_handler_is_registered() {
        List<String> registeringSources = new ArrayList<>();
        for (Path file : allSources()) {
            String body = stripWhitespace(read(file));
            if (body.contains("EVENT_BUS.register(")) registeringSources.add(body);
        }
        assertFalse(registeringSources.isEmpty(), "EVENT_BUS.register を呼ぶファイルが 1 つも無い。列挙が壊れている");

        List<String> unregistered = new ArrayList<>();
        for (Path file : handlerFiles()) {
            String className = simpleName(file);
            if (INTENTIONALLY_UNREGISTERED.containsKey(className)) continue;

            // 自分自身を登録する形（register(this)）か、
            // register を呼ぶファイルの中で new されているか。
            boolean registered = stripWhitespace(read(file)).contains("EVENT_BUS.register(");
            if (!registered) {
                for (String source : registeringSources) {
                    if (source.contains("new" + className + "(")) {
                        registered = true;
                        break;
                    }
                }
            }

            if (!registered) unregistered.add(className);
        }

        assertTrue(
            unregistered.isEmpty(),
            "@SubscribeEvent を持つがイベントバスに登録されていない: " + unregistered
                + " — proxy で登録するか、理由つきで INTENTIONALLY_UNREGISTERED に足すこと");
    }

    @Test
    @DisplayName("除外リストに実在しないクラスが残っていない")
    void exclusions_still_exist() {
        List<String> names = new ArrayList<>();
        for (Path file : handlerFiles()) {
            names.add(simpleName(file));
        }
        for (String excluded : INTENTIONALLY_UNREGISTERED.keySet()) {
            assertTrue(names.contains(excluded), excluded + " は @SubscribeEvent を持たない。除外リストから消すこと");
        }
    }

    @Test
    @DisplayName("列挙の仕組みが生きている")
    void the_enumeration_finds_something() {
        // 0 件を緑と読み違えないための番犬。
        assertFalse(handlerFiles().isEmpty(), "@SubscribeEvent を持つクラスが 1 つも見つからない。列挙が壊れている");
    }

    // ========== 列挙 ==========

    private static List<Path> handlerFiles() {
        List<Path> handlers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(path -> { if (read(path).contains("@SubscribeEvent")) handlers.add(path); });
        } catch (IOException e) {
            throw new UncheckedIOException("ソースツリーが読めない: " + SOURCE_ROOT.toAbsolutePath(), e);
        }
        return handlers;
    }

    private static List<Path> allSources() {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(sources::add);
        } catch (IOException e) {
            throw new UncheckedIOException("ソースツリーが読めない: " + SOURCE_ROOT.toAbsolutePath(), e);
        }
        return sources;
    }

    private static String simpleName(Path file) {
        return file.getFileName()
            .toString()
            .replace(".java", "");
    }

    private static String stripWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
