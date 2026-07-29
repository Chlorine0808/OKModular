package ruiseki.okmodular.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ruiseki.okcore.datastructure.BlockPos;
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

    // ========== P19 の再現 ==========

    @Test
    @DisplayName("12 バイトを PacketStructureTint として読むと P19 の例外文が出る")
    void twelve_bytes_decoded_as_tint_reproduces_p19() {
        ByteBuf wire = encode(energy());
        assertEquals(12, wire.readableBytes());

        // PacketCodec.decode は loopCodecFields の try/catch で例外を飲み、
        // printStackTrace するだけ。**だから落ちずにログだけが膨らむ。**
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        String trace;
        try {
            System.setErr(new PrintStream(captured, true, "UTF-8"));
            new PacketStructureTint().decode(new ExtendedBuffer(wire));
        } catch (Exception e) {
            throw new AssertionError("decode は例外を飲むはずだった", e);
        } finally {
            System.setErr(originalErr);
        }
        trace = new String(captured.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(trace.contains("IndexOutOfBoundsException"), "例外が出ていない: " + trace);
        assertTrue(trace.contains("readerIndex(9)") && trace.contains("writerIndex(12)"), "観測された例外文と一致しない: " + trace);
    }
}
