package ruiseki.okmodular.common.item;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import lombok.Getter;

/**
 * Define the type of intermediate material.
 * This is a template of new materials.
 *
 * The optional {@code forms} vararg restricts which part types (e.g. "ingot",
 * "plate", "dust") this material produces. Omit it to support all forms.
 */
public enum EnumMaterial {

    // Element IDs follow atomic numbers; gaps are elements that are not added.
    NEUTRONIUM(0, "neutronium", "Neutronium"),
    LITHIUM(3, "lithium", "Lithium"),
    BERYLLIUM(4, "beryllium", "Beryllium"),
    BORON(5, "boron", "Boron"),
    SODIUM(11, "sodium", "Sodium"),
    MAGNESIUM(12, "magnesium", "Magnesium"),
    ALUMINUM(13, "aluminium", "Aluminium"),
    SILICON(14, "silicon", "Silicon"),
    PHOSPHORUS(15, "phosphorus", "Phosphorus"),
    SULFUR(16, "sulfur", "Sulfur"),
    TITANIUM(22, "titanium", "Titanium"),
    VANADIUM(23, "vanadium", "Vanadium"),
    CHROMIUM(24, "chromium", "Chromium"),
    MANGANESE(25, "manganese", "Manganese"),
    IRON(26, "iron", "Iron", "plate", "gear", "dust"),
    COBALT(27, "cobalt", "Cobalt"),
    NICKEL(28, "nickel", "Nickel"),
    COPPER(29, "copper", "Copper"),
    ZINC(30, "zinc", "Zinc"),
    GALLIUM(31, "gallium", "Gallium"),
    GERMANIUM(32, "germanium", "Germanium"),
    ARSENIC(33, "arsenic", "Arsenic"),
    ZIRCONIUM(40, "zirconium", "Zirconium"),
    NIOBIUM(41, "niobium", "Niobium"),
    MOLYBDENUM(42, "molybdenum", "Molybdenum"),
    RUTHENIUM(44, "ruthenium", "Ruthenium"),
    RHODIUM(45, "rhodium", "Rhodium"),
    PALLADIUM(46, "palladium", "Palladium"),
    SILVER(47, "silver", "Silver"),
    INDIUM(49, "indium", "Indium"),
    TIN(50, "tin", "Tin"),
    ANTIMONY(51, "antimony", "Antimony"),
    EUROPIUM(63, "europium", "Europium"),
    HOLMIUM(67, "holmium", "Holmium"),
    TANTALUM(73, "tantalum", "Tantalum"),
    TUNGSTEN(74, "tungsten", "Tungsten"),
    RHENIUM(75, "rhenium", "Rhenium"),
    OSMIUM(76, "osmium", "Osmium"),
    IRIDIUM(77, "iridium", "Iridium"),
    PLATINUM(78, "platinum", "Platinum"),
    GOLD(79, "gold", "Gold", "plate", "gear", "dust"),
    LEAD(82, "lead", "Lead"),
    POLONIUM(84, "polonium", "Polonium"),
    RADIUM(88, "radium", "Radium"),
    THORIUM(90, "thorium", "Thorium"),
    URANIUM(92, "uranium", "Uranium"),

    // Alloy
    OBSIDIAN(132, "obsidian", "Obsidian"),
    BRONZE(140, "bronze", "Bronze"),
    WROUGHTIRON(141, "wroughtiron", "WroughtIron"),
    GRAPHITE(142, "graphite", "Graphite"),
    STEEL(143, "steel", "Steel"),
    CUPRONICKEL(144, "cupronickel", "Cupronickel"),
    KANTHAL(145, "kanthal", "Kanthal"),
    INGAP(146, "ingap", "InGaP"),
    GALLIUMARSENIDE(147, "galliumarsenide", "GalliumArsenide"),
    INVAR(148, "invar", "Invar"),
    MAGNALIUM(149, "magnalium", "Magnalium"),
    STAINLESSSTEEL(160, "stainlesssteel", "StainlessSteel"),
    TUNGSTENSTEEL(161, "tungstensteel", "TungstenSteel"),
    VANADIUMSTEEL(162, "vanadiumsteel", "VanadiumSteel"),
    BLACKSTEEL(163, "blacksteel", "BlackSteel"),
    BLUESTEEL(164, "bluesteel", "BlueSteel"),
    REDSTEEL(165, "redsteel", "RedSteel"),
    ROSEGOLD(170, "rosegold", "RoseGold"),
    PLATINUMALLOY(171, "platinumalloy", "PlatinumAlloy"),
    TITANIUMALLOY(172, "titaniumalloy", "TitaniumAlloy"),
    CHROMECHLORIDE(200, "chromiumchloride", "ChromiumChloride"),
    HSS_G(220, "hss_g", "HSS-G"),
    HSS_S(221, "hss_s", "HSS-S"),
    OSMIRIDIUM(250, "osmiridium", "Osmiridium"),
    RHODIUMPALLADIUM(251, "rhodiumpalladium", "RhodiumPalladium"),
    THOURANIUM(252, "thouranium", "Thouranium"),
    EXCITEDURANIUM(253, "exciteduranium", "ExcitedUranium"),
    TOUGHALLOY(270, "toughalloy", "ToughAlloy"),

    // MYTHIC
    ADAMANTIUM(1200, "adamantium", "Adamantium"),
    AURARIUM(1210, "aurarium", "Aurarium"),
    FREEZIUM(1220, "freezium", "Freezium"),
    ENDERALLOY(1230, "enderalloy", "EnderAlloy"),
    CHORUSALLOY(1231, "chorusalloy", "ChorusAlloy"),
    HIHIIRIOKANE(1240, "hihiirokane", "Hihiirokane"),
    OPTICALLIUM(1250, "opticallium", "Opticallium"),
    ORIHALKON(1260, "orihalkon", "Orihalkon"),
    PARADOX(1270, "paradox", "Paradox"),
    VOIDSTEEL(1280, "voidsteel", "VoidSteel"),
    VULCANIUM(1290, "vulcanium", "Vulcanium"),
    CHLONOX(1300, "chlonox", "Chlonox"),
    LEVIATHAN(1310, "leviathan", "Leviathan"),
    PERFECT(1500, "perfect", "Perfect"),

    // EGYPTIAN
    SOLARIUM(1600, "solarium", "Solarium", "ingot"),
    OSIRIUM(1601, "osirium", "Osirium", "ingot"),
    KHEPHRITE(1602, "khephrite", "Khephrite", "ingot"),
    ANUBIUM(1603, "anubium", "Anubium", "ingot"),

    ;

    @Getter
    private final int meta;
    @Getter
    private final String name; // internal name, texture name, translation key
    @Getter
    private final String oreName; // suffix for ore dictionary
    private final Set<String> forms; // empty = all forms supported

    /**
     * @param forms part types this material supports ("ingot", "plate", "gear", "dust").
     *              Omit to support all forms.
     */
    EnumMaterial(int meta, String name, String oreName, String... forms) {
        this.meta = meta;
        this.name = name;
        this.oreName = oreName;
        this.forms = forms.length == 0 ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(Arrays.asList(forms)));
    }

    /** Returns true if this material produces items of the given part type. */
    public boolean supportsForm(String form) {
        return forms.isEmpty() || forms.contains(form);
    }

    public static EnumMaterial byMetadata(int meta) {
        for (EnumMaterial material : values()) {
            if (material.getMeta() == meta) {
                return material;
            }
        }
        return null;
    }
}
