package ruiseki.okmodular.common.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * EnumMaterial の meta 表の凍結（release_freeze F-3）。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `meta` は `ItemMaterialPart` のダメージ値、つまり
 * **全ワールドの全アイテムスタックが持っている数値**。番号が 1 つずれると、
 * プレイヤーのチェストの中身が無言で別の素材に化ける。例外もログも出ない。
 *
 * 設計そのものは正しい — meta は ordinal ではなく**明示フィールド**なので、
 * 表の途中に定数を挿しても既存の番号は動かない（歯抜けはそのための空き）。
 * 危ないのは設計ではなく**運用**で、ロードマップの第 1 項が「中間素材の追加」である以上、
 * **この表を触る作業は確実に来る**。「詰め直したくなる」誘惑もそのとき来る。
 *
 * ============================================
 * なぜ実行時列挙ではなく「書き写した表」なのか
 * ============================================
 *
 * `values()` を回して検証する形にすると、**定数が消えたことを検出できない**。
 * 列挙はその時点で存在するものを返すだけなので、消えれば検証対象からも消えて緑になる。
 * よってここには 90 件を**リテラルとして書き写す**。表が手書きであること自体が仕様。
 *
 * **追加は許す。変更と削除だけを禁止する。**
 *
 * ============================================
 * name / oreName も凍結する理由
 * ============================================
 *
 * `meta` ほど破滅的ではないが、どちらも外に出ている:
 * `name` はテクスチャ名と翻訳キー、`oreName` は鉱石辞書のエントリ名。
 * 変えるとテクスチャが落ち、他 mod のレシピが引けなくなる。
 *
 * **綴りの揺れも「揺れたまま」凍結している** — 直すのは破壊的変更なので、
 * 直したくなったらこのテストを意図的に落としてから直すこと:
 *
 * <pre>
 * ALUMINUM        → name "aluminium"        （定数は米綴り、name は英綴り）
 * CHROMECHLORIDE  → name "chromiumchloride" （定数は Chrome、name は Chromium）
 * HIHIIRIOKANE    → name "hihiirokane"      （定数に I が 1 つ多い）
 * </pre>
 */
@DisplayName("EnumMaterial の meta 表")
public class EnumMaterialMetaFreezeTest {

    /**
     * 凍結した表。列は {定数名, meta, name, oreName, forms}。
     * forms は空文字なら「全部の形状を作れる」の意（コンストラクタの vararg 省略と同じ）。
     */
    // spotless:off
    private static final String[][] FROZEN = {
        // --- 元素 ---
        { "NEUTRONIUM",       "0",    "neutronium",       "Neutronium",       "" },
        { "LITHIUM",          "3",    "lithium",          "Lithium",          "" },
        { "BERYLLIUM",        "4",    "beryllium",        "Beryllium",        "" },
        { "BORON",            "5",    "boron",            "Boron",            "" },
        { "SODIUM",           "11",   "sodium",           "Sodium",           "" },
        { "MAGNESIUM",        "12",   "magnesium",        "Magnesium",        "" },
        { "ALUMINUM",         "13",   "aluminium",        "Aluminium",        "" },
        { "SILICON",          "14",   "silicon",          "Silicon",          "" },
        { "PHOSPHORUS",       "15",   "phosphorus",       "Phosphorus",       "" },
        { "SULFUR",           "16",   "sulfur",           "Sulfur",           "" },
        { "TITANIUM",         "22",   "titanium",         "Titanium",         "" },
        { "VANADIUM",         "23",   "vanadium",         "Vanadium",         "" },
        { "CHROMIUM",         "24",   "chromium",         "Chromium",         "" },
        { "MANGANESE",        "25",   "manganese",        "Manganese",        "" },
        { "COBALT",           "27",   "cobalt",           "Cobalt",           "" },
        { "NICKEL",           "28",   "nickel",           "Nickel",           "" },
        { "COPPER",           "29",   "copper",           "Copper",           "" },
        { "ZINC",             "30",   "zinc",             "Zinc",             "" },
        { "GALLIUM",          "31",   "gallium",          "Gallium",          "" },
        { "GERMANIUM",        "32",   "germanium",        "Germanium",        "" },
        { "ARSENIC",          "33",   "arsenic",          "Arsenic",          "" },
        { "ZIRCONIUM",        "40",   "zirconium",        "Zirconium",        "" },
        { "NIOBIUM",          "41",   "niobium",          "Niobium",          "" },
        { "MOLYBDENUM",       "42",   "molybdenum",       "Molybdenum",       "" },
        { "RUTHENIUM",        "44",   "ruthenium",        "Ruthenium",        "" },
        { "RHODIUM",          "45",   "rhodium",          "Rhodium",          "" },
        { "PALLADIUM",        "46",   "palladium",        "Palladium",        "" },
        { "SILVER",           "47",   "silver",           "Silver",           "" },
        { "INDIUM",           "49",   "indium",           "Indium",           "" },
        { "TIN",              "50",   "tin",              "Tin",              "" },
        { "ANTIMONY",         "51",   "antimony",         "Antimony",         "" },
        { "EUROPIUM",         "63",   "europium",         "Europium",         "" },
        { "HOLMIUM",          "67",   "holmium",          "Holmium",          "" },
        { "TANTALUM",         "73",   "tantalum",         "Tantalum",         "" },
        { "TUNGSTEN",         "74",   "tungsten",         "Tungsten",         "" },
        { "RHENIUM",          "75",   "rhenium",          "Rhenium",          "" },
        { "OSMIUM",           "76",   "osmium",           "Osmium",           "" },
        { "IRIDIUM",          "77",   "iridium",          "Iridium",          "" },
        { "PLATINUM",         "78",   "platinum",         "Platinum",         "" },
        { "LEAD",             "82",   "lead",             "Lead",             "" },
        { "POLONIUM",         "84",   "polonium",         "Polonium",         "" },
        { "RADIUM",           "88",   "radium",           "Radium",           "" },
        { "THORIUM",          "90",   "thorium",          "Thorium",          "" },
        { "URANIUM",          "92",   "uranium",          "Uranium",          "" },

        // --- 合金・鉱物 ---
        { "OBSIDIAN",         "132",  "obsidian",         "Obsidian",         "" },
        { "BRONZE",           "140",  "bronze",           "Bronze",           "" },
        { "WROUGHTIRON",      "141",  "wroughtiron",      "WroughtIron",      "" },
        { "GRAPHITE",         "142",  "graphite",         "Graphite",         "" },
        { "STEEL",            "143",  "steel",            "Steel",            "" },
        { "CUPRONICKEL",      "144",  "cupronickel",      "Cupronickel",      "" },
        { "KANTHAL",          "145",  "kanthal",          "Kanthal",          "" },
        { "INGAP",            "146",  "ingap",            "InGaP",            "" },
        { "GALLIUMARSENIDE",  "147",  "galliumarsenide",  "GalliumArsenide",  "" },
        { "INVAR",            "148",  "invar",            "Invar",            "" },
        { "MAGNALIUM",        "149",  "magnalium",        "Magnalium",        "" },
        { "STAINLESSSTEEL",   "160",  "stainlesssteel",   "StainlessSteel",   "" },
        { "TUNGSTENSTEEL",    "161",  "tungstensteel",    "TungstenSteel",    "" },
        { "VANADIUMSTEEL",    "162",  "vanadiumsteel",    "VanadiumSteel",    "" },
        { "BLACKSTEEL",       "163",  "blacksteel",       "BlackSteel",       "" },
        { "BLUESTEEL",        "164",  "bluesteel",        "BlueSteel",        "" },
        { "REDSTEEL",         "165",  "redsteel",         "RedSteel",         "" },
        { "ROSEGOLD",         "170",  "rosegold",         "RoseGold",         "" },
        { "PLATINUMALLOY",    "171",  "platinumalloy",    "PlatinumAlloy",    "" },
        { "TITANIUMALLOY",    "172",  "titaniumalloy",    "TitaniumAlloy",    "" },
        { "CHROMECHLORIDE",   "200",  "chromiumchloride", "ChromiumChloride", "" },
        { "HSS_G",            "220",  "hss_g",            "HSS-G",            "" },
        { "HSS_S",            "221",  "hss_s",            "HSS-S",            "" },
        { "OSMIRIDIUM",       "250",  "osmiridium",       "Osmiridium",       "" },
        { "RHODIUMPALLADIUM", "251",  "rhodiumpalladium", "RhodiumPalladium", "" },
        { "THOURANIUM",       "252",  "thouranium",       "Thouranium",       "" },
        { "EXCITEDURANIUM",   "253",  "exciteduranium",   "ExcitedUranium",   "" },
        { "TOUGHALLOY",       "270",  "toughalloy",       "ToughAlloy",       "" },

        // --- MYTHIC ---
        { "ADAMANTIUM",       "1200", "adamantium",       "Adamantium",       "" },
        { "AURARIUM",         "1210", "aurarium",         "Aurarium",         "" },
        { "FREEZIUM",         "1220", "freezium",         "Freezium",         "" },
        { "ENDERALLOY",       "1230", "enderalloy",       "EnderAlloy",       "" },
        { "CHORUSALLOY",      "1231", "chorusalloy",      "ChorusAlloy",      "" },
        { "HIHIIRIOKANE",     "1240", "hihiirokane",      "Hihiirokane",      "" },
        { "OPTICALLIUM",      "1250", "opticallium",      "Opticallium",      "" },
        { "ORIHALKON",        "1260", "orihalkon",        "Orihalkon",        "" },
        { "PARADOX",          "1270", "paradox",          "Paradox",          "" },
        { "VOIDSTEEL",        "1280", "voidsteel",        "VoidSteel",        "" },
        { "VULCANIUM",        "1290", "vulcanium",        "Vulcanium",        "" },
        { "CHLONOX",          "1300", "chlonox",          "Chlonox",          "" },
        { "LEVIATHAN",        "1310", "leviathan",        "Leviathan",        "" },
        { "PERFECT",          "1500", "perfect",          "Perfect",          "" },

        // --- EGYPTIAN: インゴットしか作らない ---
        { "SOLARIUM",         "1600", "solarium",         "Solarium",         "ingot" },
        { "OSIRIUM",          "1601", "osirium",          "Osirium",          "ingot" },
        { "KHEPHRITE",        "1602", "khephrite",        "Khephrite",        "ingot" },
        { "ANUBIUM",          "1603", "anubium",          "Anubium",          "ingot" },
    };
    // spotless:on

    /** コンストラクタが受け付ける形状。forms の検証はこの 3 つで回す。 */
    private static final List<String> ALL_FORMS = Arrays.asList("ingot", "plate", "dust");

    private static String[][] frozenRows() {
        return FROZEN;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frozenRows")
    @DisplayName("凍結した定数が同じ meta / name / oreName を持つ")
    public void test凍結した行が変わっていない(String constant, String meta, String name, String oreName, String forms) {
        EnumMaterial material = find(constant);

        assertEquals(
            Integer.parseInt(meta),
            material.getMeta(),
            () -> constant + " の meta が変わっている。meta は ItemMaterialPart のダメージ値なので、"
                + "既存ワールドの "
                + constant
                + " が無言で別の素材になる");
        assertEquals(name, material.getName(), () -> constant + " の name が変わっている。テクスチャ名と翻訳キーがこれを使っている");
        assertEquals(
            oreName,
            material.getOreName(),
            () -> constant + " の oreName が変わっている。鉱石辞書のエントリ名が変わり、他 mod のレシピが引けなくなる");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frozenRows")
    @DisplayName("凍結した定数が同じ形状だけを作る")
    public void test作れる形状が変わっていない(String constant, String meta, String name, String oreName, String forms) {
        EnumMaterial material = find(constant);
        Set<String> expected = forms.isEmpty() ? null : new HashSet<>(Arrays.asList(forms.split(",")));

        for (String form : ALL_FORMS) {
            boolean shouldSupport = expected == null || expected.contains(form);
            assertEquals(
                shouldSupport,
                material.supportsForm(form),
                () -> constant + " が " + form + " を作れるかどうかが変わっている。" + "形状を増やすと新しいアイテムが生えるので、追加なら意図的にこの表を直すこと");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frozenRows")
    @DisplayName("byMetadata が凍結した定数を引き当てる")
    public void test番号から引き当てられる(String constant, String meta, String name, String oreName, String forms) {
        assertSame(
            find(constant),
            EnumMaterial.byMetadata(Integer.parseInt(meta)),
            () -> "meta " + meta + " が " + constant + " 以外を返す。番号を別の素材に付け替えている");
    }

    @Test
    @DisplayName("meta が重複していない")
    public void test番号が重複していない() {
        Map<Integer, EnumMaterial> seen = new HashMap<>();

        for (EnumMaterial material : EnumMaterial.values()) {
            EnumMaterial previous = seen.put(material.getMeta(), material);
            assertEquals(
                null,
                previous,
                () -> "meta " + material.getMeta()
                    + " が "
                    + previous
                    + " と "
                    + material
                    + " で重複している。"
                    + "byMetadata は先に見つけたほうを返すので、後ろの素材は永久に引けない");
        }
    }

    @Test
    @DisplayName("name が重複していない")
    public void test名前が重複していない() {
        Map<String, EnumMaterial> seen = new HashMap<>();

        for (EnumMaterial material : EnumMaterial.values()) {
            EnumMaterial previous = seen.put(material.getName(), material);
            assertEquals(
                null,
                previous,
                () -> "name '" + material
                    .getName() + "' が " + previous + " と " + material + " で重複している。" + "テクスチャと翻訳キーが衝突する");
        }
    }

    @Test
    @DisplayName("素材は減っていない")
    public void test素材が減っていない() {
        assertTrue(
            EnumMaterial.values().length >= FROZEN.length,
            "EnumMaterial の定数が凍結した " + FROZEN.length + " 件より少ない。" + "追加は自由だが、削除は既存ワールドのアイテムを行き場のない meta にする");
    }

    private static EnumMaterial find(String constant) {
        EnumMaterial material = null;
        for (EnumMaterial candidate : EnumMaterial.values()) {
            if (candidate.name()
                .equals(constant)) {
                material = candidate;
                break;
            }
        }

        assertNotNull(
            material,
            () -> "定数 " + constant + " が消えている。既存ワールドのアイテムはこの meta を指したまま残るので、" + "消すなら byMetadata が何を返すかを決めてからにすること");
        return material;
    }
}
