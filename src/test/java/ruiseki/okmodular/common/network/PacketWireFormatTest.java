package ruiseki.okmodular.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ruiseki.okcore.datastructure.BlockPos;
import ruiseki.okcore.network.CodecField;
import ruiseki.okcore.network.ExtendedBuffer;
import ruiseki.okcore.network.PacketCodec;

/**
 * パケットのワイヤ形式の実測。**P19（ログ膨張）の再現テスト。**
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * ワールドに参加すると同じ例外が繰り返しログに出る（1 セッション 698 回、
 * ログが 1.2MB → 5.8MB）。
 *
 * java.lang.IndexOutOfBoundsException: readerIndex(9) + length(4) exceeds writerIndex(12)
 * at ruiseki.okcore.network.PacketCodec$17.decode ← List 型の codec
 *
 * 当初は OKCore のレシピ同期パケットが原因と結論していたが**誤り**だった
 * （そのクラスは encode/decode を override しており、この経路を通らない）。
 *
 * `@CodecField` の List はコード全体で `PacketStructureTint.positions` **ただ 1 つ**。
 * そしてこのテストが示すとおり、**別のパケットのバイト列が
 * `PacketStructureTint` として読まれている** = discriminator のずれ。
 *
 * ============================================
 * なぜバイト数を「リテラルで」書くか
 * ============================================
 *
 * 期待値を `wireSize()` から計算すると何も検証しないテストになる。
 * **ワイヤ形式は永続化と同じ契約**で、フィールドを 1 本足すだけで全部ずれる。
 * だから手で数えた値を書き写す。数え方は各テストのコメントに残す。
 *
 * ============================================
 * このテストが縛らないこと
 * ============================================
 *
 * **discriminator の採番そのものは縛れない。** `PacketHandler.register` は
 * FML の `NetworkRegistry` を要求するのでゲーム外では動かない。ここで確定できるのは
 * 「12 バイトのパケットが実在し、それを `PacketStructureTint` として読むと
 * 観測された例外文になる」ところまで。
 *
 * ============================================
 */
@DisplayName("パケットのワイヤ形式")
public class PacketWireFormatTest {

    // ========== ヘルパ ==========

    /** encode した結果のバイト数。discriminator は含まない（FML が別に 1 バイト付ける）。 */
    private static int wireSize(PacketCodec packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.encode(new ExtendedBuffer(buf));
        return buf.readableBytes();
    }

    private static ByteBuf encode(PacketCodec packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.encode(new ExtendedBuffer(buf));
        return buf;
    }

    /**
     * 便利コンストラクタは TileEntity インタフェースを要求するので、
     * 引数なしで作ってフィールドを直接入れる。
     */
    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("フィールド " + name + " に入れられない", e);
        }
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("フィールド " + name + " が読めない", e);
        }
    }

    private static PacketEnergy energy() {
        PacketEnergy packet = new PacketEnergy();
        set(packet, "pos", new BlockPos(10, 20, 30));
        set(packet, "storedEnergy", 500);
        return packet;
    }

    private static PacketProgress progress() {
        PacketProgress packet = new PacketProgress();
        set(packet, "pos", new BlockPos(10, 20, 30));
        set(packet, "progress", 0.5f);
        return packet;
    }

    private static PacketCraftingState craftingState() {
        PacketCraftingState packet = new PacketCraftingState();
        set(packet, "pos", new BlockPos(10, 20, 30));
        set(packet, "craftingState", 2);
        return packet;
    }

    // ========== 12 バイトのパケットが実在する ==========

    @Test
    @DisplayName("PacketEnergy は 12 バイト（BlockPos 8 + int 4）")
    void energy_is_12_bytes() {
        // BlockPos の codec は writeLong = 8 バイト（3 int ではない）。
        // フィールドは名前順に並ぶ: pos(8) → storedEnergy(4)。
        assertEquals(12, wireSize(energy()));
    }

    @Test
    @DisplayName("PacketProgress は 12 バイト（BlockPos 8 + float 4）")
    void progress_is_12_bytes() {
        // 名前順: pos(8) → progress(4)。
        assertEquals(12, wireSize(progress()));
    }

    @Test
    @DisplayName("PacketCraftingState は 12 バイト（int 4 + BlockPos 8）")
    void crafting_state_is_12_bytes() {
        // 名前順: craftingState(4) → pos(8)。並びは違うが合計は同じ。
        assertEquals(12, wireSize(craftingState()));
    }

    // ========== PacketStructureTint のワイヤ形式 ==========

    @Test
    @DisplayName("PacketStructureTint は空リストでも 13 バイト")
    void structure_tint_empty_is_13_bytes() {
        // 名前順: clear(1) + color(4) + dimensionId(4) = 9、
        // その後 List codec が length として writeInt(0) = 4。合計 13。
        //
        // **12 にはならない。** ここが「12 バイトの送り主は PacketStructureTint ではない」の根拠。
        assertEquals(13, wireSize(new PacketStructureTint(0, 0, Collections.emptyList())));
    }

    @Test
    @DisplayName("PacketStructureTint は 12 バイトを一度も作らない")
    void structure_tint_never_produces_12_bytes() {
        for (int count = 0; count <= 4; count++) {
            ChunkCoordinates[] positions = new ChunkCoordinates[count];
            for (int i = 0; i < count; i++) {
                positions[i] = new ChunkCoordinates(i, i, i);
            }
            int size = wireSize(new PacketStructureTint(1, 0xFF0000, Arrays.asList(positions)));
            assertNotEquals(12, size, "要素 " + count + " 個で 12 バイトになった");
        }
    }

    @Test
    @DisplayName("PacketStructureTint 自身の codec は正しく往復する")
    void structure_tint_round_trips() {
        // 壊れているのは codec ではなく「どのクラスが読むか」だと示すための対照。
        List<ChunkCoordinates> positions = Arrays
            .asList(new ChunkCoordinates(1, 2, 3), new ChunkCoordinates(-4, 5, -6));

        ByteBuf buf = encode(new PacketStructureTint(7, 0x00FF00, positions));

        PacketStructureTint decoded = new PacketStructureTint();
        decoded.decode(new ExtendedBuffer(buf));

        assertEquals(7, get(decoded, "dimensionId"));
        assertEquals(0x00FF00, get(decoded, "color"));
        assertEquals(false, get(decoded, "clear"));
        assertEquals(positions, get(decoded, "positions"));
    }

    // ========== P19: 素の codec の壊れ方（フレームワークの特性）==========

    /**
     * `@CodecField` の List を持つ、ガードの無いパケット。
     *
     * `PacketStructureTint` にガードを入れた後も**フレームワーク側の壊れ方は変わらない**ので、
     * それを固定するために置く。ここが変わったらガードの前提が崩れたということ。
     * フィールド名は `PacketStructureTint` と同じにしてある（名前順に並ぶため）。
     */
    public static class UnguardedListPacket extends PacketCodec {

        @CodecField
        private boolean clear;
        @CodecField
        private int color;
        @CodecField
        private int dimensionId;
        @CodecField
        private List<ChunkCoordinates> positions = Collections.emptyList();

        @Override
        public boolean isAsync() {
            return false;
        }

        @Override
        public void actionClient(World world, EntityPlayer player) {}

        @Override
        public void actionServer(World world, EntityPlayerMP player) {}
    }

    @Test
    @DisplayName("短いバイト列を素通しすると、OKCore の codec は例外を飲んで print する")
    void unguarded_short_buffer_swallows_and_prints() {
        // P19 の壊れ方そのもの。loopCodecFields が try/catch で例外を飲み
        // printStackTrace するだけなので、**落ちずにログだけが膨らむ**。
        ByteBuf wire = encode(energy());
        assertEquals(12, wire.readableBytes());

        String trace = captureStderr(() -> new UnguardedListPacket().decode(new ExtendedBuffer(wire)));

        assertTrue(trace.contains("IndexOutOfBoundsException"), "例外が出ていない: " + trace);
        assertTrue(trace.contains("readerIndex(9)") && trace.contains("writerIndex(12)"), "観測された例外文と一致しない: " + trace);
    }

    // ========== P19: PacketStructureTint 側のガード ==========

    @Test
    @DisplayName("PacketStructureTint は 12 バイトを静かに捨てる")
    void tint_drops_a_short_buffer_quietly() {
        ByteBuf wire = encode(energy());

        String trace = captureStderr(() -> new PacketStructureTint().decode(new ExtendedBuffer(wire)));

        assertFalse(trace.contains("IndexOutOfBoundsException"), "スタックトレースが出た。ガードが効いていない: " + trace);
    }

    @Test
    @DisplayName("捨てるときフィールドを書き換えない")
    void tint_leaves_fields_untouched_when_dropping() {
        // 半端に読んだ値を入れてしまうと、正規のパケットが来る前に
        // クライアントが誤った dimensionId や色を持つ。
        PacketStructureTint victim = new PacketStructureTint();
        victim.decode(new ExtendedBuffer(encode(energy())));

        assertEquals(0, get(victim, "dimensionId"));
        assertEquals(0, get(victim, "color"));
        assertEquals(false, get(victim, "clear"));
        assertEquals(Collections.emptyList(), get(victim, "positions"));
    }

    @Test
    @DisplayName("ガードの境界は 13 バイト（正規の最小長）")
    void guard_boundary_is_the_minimum_legitimate_size() {
        // 13 = clear(1) + color(4) + dimensionId(4) + リスト長(4)。
        // 12 以下は正規のパケットではありえないので捨ててよい。
        assertEquals(13, wireSize(new PacketStructureTint(0, 0, Collections.emptyList())));

        assertFalse(PacketStructureTint.isDecodable(12), "12 バイトを受け入れてしまう");
        assertTrue(PacketStructureTint.isDecodable(13), "13 バイトを捨ててしまう");
    }

    @Test
    @DisplayName("成立しないリスト長は受け入れない（巨大確保の防止）")
    void implausible_list_length_is_rejected() {
        // 長さが足りていても、リスト長が壊れていると codec は
        // Lists.newArrayListWithExpectedSize(その値) を呼ぶ。**ログ膨張より悪い壊れ方。**
        assertFalse(PacketStructureTint.isPlausibleListLength(-1, 100), "負の長さを受け入れてしまう");
        assertFalse(PacketStructureTint.isPlausibleListLength(0x0A141E00, 20), "巨大な長さを受け入れてしまう");

        assertTrue(PacketStructureTint.isPlausibleListLength(0, 13), "空リストを捨ててしまう");
        // 要素 2 個の正規のパケットが実際に何バイトになるかを測って渡す。
        int realSize = wireSize(
            new PacketStructureTint(1, 0, Arrays.asList(new ChunkCoordinates(1, 2, 3), new ChunkCoordinates(4, 5, 6))));
        assertTrue(PacketStructureTint.isPlausibleListLength(2, realSize), "正規のパケットを捨ててしまう");
    }

    @Test
    @DisplayName("長さは足りていてもリスト長が壊れていれば静かに捨てる")
    void garbage_list_length_is_dropped_quietly() {
        // scalar 9 バイト + 壊れたリスト長 + 余り。isDecodable は通ってしまう長さにする。
        ByteBuf wire = Unpooled.buffer();
        wire.writeZero(9);
        wire.writeInt(0x0A141E00);
        wire.writeZero(7);
        assertEquals(20, wire.readableBytes());

        String trace = captureStderr(() -> new PacketStructureTint().decode(new ExtendedBuffer(wire)));

        assertFalse(trace.contains("Exception"), "例外が出た。リスト長の検算が効いていない: " + trace);
    }

    private static String captureStderr(Runnable body) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, "UTF-8"));
            body.run();
        } catch (Exception e) {
            throw new AssertionError("decode は例外を飲むはずだった", e);
        } finally {
            System.setErr(originalErr);
        }
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }
}
