package ruiseki.okmodular.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okcore.network.PacketBase;
import ruiseki.okmodular.MachineryCommon;

/**
 * パケットクラスと「チャンネルへの登録」の突き合わせ。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **登録されていないパケットを送っても何も起きない。** FML はクラスから
 * ワイヤ上の discriminator を引くので、登録の無いクラスには番号が無く、
 * 送信は呼び出し側ではなくチャンネルのパイプライン内側で失敗する。
 * **送る側と登録する側をつなぐものがコンパイル時に何も無い。**
 *
 * 実際に落ちていた。分離でレガシー TE 基盤（送信側）を OKModular に持ち込んだとき、
 * 親 mod の `CoreCommon` が持っていた登録が付いてこなかった。結果:
 *
 * - `PacketToggleSide` — **レンチの面 IO 切り替えがサーバに届いていなかった**
 * - `PacketEnergy` / `PacketProgress` / `PacketFluidTanks` / `PacketCraftingState`
 * — 同期パケットが分離以降ずっと沈黙していた
 *
 * ============================================
 * 除外を「理由つきで」書かせる形にした理由
 * ============================================
 *
 * 「全部登録しろ」にはできない。上の 4 つは**沈黙していたのに不具合として
 * 現れていない**（GUI は ModularUI の sync value で値を運ぶ）ので、
 * 登録すると機械 1 個ごとに周囲の全プレイヤー宛の通信が復活するだけになる。
 *
 * かわりに**判断を明示させる**。新しいパケットクラスを足したら、
 * 登録するか、理由を書いて除外するかのどちらかをするまでこのテストが落ちる。
 * 「うっかり登録し忘れる」が起こらない形。
 *
 * ============================================
 * なぜソースディレクトリを読むのか
 * ============================================
 *
 * クラスパス走査の依存を足したくない。そして**手書きの一覧では意味が無い**
 * ことがこの欠陥の教訓そのもの — 一覧を書き写す作業を忘れるのが失敗原因なので、
 * 期待値は**実在するファイルから生きた列挙**で作る必要がある。
 * 手書きなのは「除外の理由」だけ。
 *
 * ============================================
 */
@DisplayName("パケット登録の網羅")
public class PacketRegistrationCoverageTest {

    private static final File NETWORK_DIR = new File("src/main/java/ruiseki/okmodular/common/network");

    /**
     * 登録しないと決めたパケットと、その理由。
     * <p>
     * **理由が書けないなら登録する。** ここに足すのは「送っていないか、
     * 送っているが届かなくても正しい」と言い切れるときだけ。
     */
    private static final Map<String, String> INTENTIONALLY_UNREGISTERED;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        String syncReason = "分離以降届いていないが不具合として現れていない（GUI は ModularUI の sync value を使う）。"
            + "登録すると機械 1 個ごとに周囲の全プレイヤー宛の通信が復活するだけになるため触らない。"
            + "実機で本当に要るかを確かめてから判断する。";
        map.put("PacketEnergy", syncReason);
        map.put("PacketProgress", syncReason);
        map.put("PacketFluidTanks", syncReason);
        map.put("PacketCraftingState", syncReason);
        INTENTIONALLY_UNREGISTERED = Collections.unmodifiableMap(map);
    }

    // ========== 生きた列挙 ==========

    /** `common/network` に実在する PacketCodec 派生クラスの単純名。 */
    private static Set<String> declaredPackets() {
        File[] files = NETWORK_DIR.listFiles((dir, name) -> name.endsWith(".java"));
        assertNotNull(files, "network パッケージが読めない: " + NETWORK_DIR.getAbsolutePath());

        Set<String> names = new TreeSet<>();
        for (File file : files) {
            if (read(file).contains("extends PacketCodec")) {
                names.add(
                    file.getName()
                        .replace(".java", ""));
            }
        }
        assertFalse(names.isEmpty(), "PacketCodec 派生が 1 つも見つからない。列挙の仕組みが壊れている");
        return names;
    }

    private static Set<String> registeredPackets() {
        Set<String> names = new TreeSet<>();
        for (Class<? extends PacketBase> packet : MachineryCommon.PACKETS) {
            names.add(packet.getSimpleName());
        }
        return names;
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }

    // ========== 検証 ==========

    @Test
    @DisplayName("実在するパケットは全て、登録されているか理由つきで除外されている")
    void every_packet_is_registered_or_excluded() {
        Set<String> registered = registeredPackets();
        List<String> unaccounted = new ArrayList<>();

        for (String packet : declaredPackets()) {
            if (!registered.contains(packet) && !INTENTIONALLY_UNREGISTERED.containsKey(packet)) {
                unaccounted.add(packet);
            }
        }

        assertTrue(
            unaccounted.isEmpty(),
            "MachineryCommon.PACKETS に足すか、理由つきで INTENTIONALLY_UNREGISTERED に足すこと: " + unaccounted);
    }

    @Test
    @DisplayName("除外リストと登録リストが重なっていない")
    void exclusions_are_not_also_registered() {
        Set<String> registered = registeredPackets();
        for (String excluded : INTENTIONALLY_UNREGISTERED.keySet()) {
            assertFalse(registered.contains(excluded), excluded + " は登録済み。除外リストから消すこと");
        }
    }

    @Test
    @DisplayName("除外リストに実在しないクラスが残っていない")
    void exclusions_still_exist() {
        // クラスを消したのに除外だけ残ると、「判断済み」の見た目のまま中身が無くなる。
        Set<String> declared = declaredPackets();
        for (String excluded : INTENTIONALLY_UNREGISTERED.keySet()) {
            assertTrue(declared.contains(excluded), excluded + " は実在しない。除外リストから消すこと");
        }
    }

    @Test
    @DisplayName("除外には必ず理由が書かれている")
    void exclusions_have_a_reason() {
        for (Map.Entry<String, String> entry : INTENTIONALLY_UNREGISTERED.entrySet()) {
            String reason = entry.getValue();
            assertNotNull(reason, entry.getKey() + " に理由が無い");
            assertTrue(reason.length() >= 20, entry.getKey() + " の理由が短すぎる: " + reason);
        }
    }

    @Test
    @DisplayName("登録リストに重複が無い")
    void registrations_are_unique() {
        // 同じクラスを 2 回登録すると discriminator が 2 つ振られる。
        assertEquals(
            MachineryCommon.PACKETS.size(),
            registeredPackets().size(),
            "同じパケットが 2 回登録されている: " + MachineryCommon.PACKETS);
    }
}
