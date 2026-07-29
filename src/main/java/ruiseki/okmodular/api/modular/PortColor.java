package ruiseki.okmodular.api.modular;

import ruiseki.okcore.enums.EnumDye;

/**
 * A colour a player can put on a port, used to divide one machine's ports into
 * independent groups.
 *
 * <h2>The declaration order is a numbering shared with three other things</h2>
 *
 * <pre>
 * vanilla wool metadata          0 = white ... 15 = black
 * AE2's AEColor.ordinal()        0 = White ... 15 = Black, 16 = Transparent
 * Forge's Block.recolourBlock    the colour argument arrives in that same numbering
 * </pre>
 *
 * Matching it is what makes spray compatibility nearly free: AE2's Color
 * Applicator falls back to {@code blk.recolourBlock(w, x, y, z, side,
 * newColor.ordinal())} when a tile does not implement its own colour interface,
 * so a port only has to read that argument as an index into this enum.
 *
 * <p>
 * {@link #NONE} sits at 16 so it lines up with {@code AEColor.Transparent},
 * which is how the Color Applicator clears a colour. Without that, a painted
 * port could never be unpainted with the same tool.
 *
 * <h2>OKCore's EnumDye runs the other way</h2>
 *
 * {@link EnumDye} is ordered by dye damage value - {@code BLACK = 0} through
 * {@code WHITE = 15} - which is the exact reverse, so the two ordinals sum to 15.
 * OKCore is not modified, so the conversion lives here. Getting it backwards
 * inverts every colour rather than failing, which is why it is pinned by a test.
 *
 * <h2>Adding or renaming</h2>
 *
 * Neither is available. The name is what a port's NBT holds and the position is
 * the shared numbering, so this enum is fixed at the sixteen dye colours plus
 * "unpainted".
 */
public enum PortColor {

    // spotless:off
    WHITE      (0xF0F0F0, EnumDye.WHITE),
    ORANGE     (0xEB8844, EnumDye.ORANGE),
    MAGENTA    (0xC354CD, EnumDye.MAGENTA),
    LIGHT_BLUE (0x6689D3, EnumDye.LIGHT_BLUE),
    YELLOW     (0xDECF2A, EnumDye.YELLOW),
    LIME       (0x41CD34, EnumDye.LIME),
    PINK       (0xD88198, EnumDye.PINK),
    GRAY       (0x434343, EnumDye.GRAY),
    LIGHT_GRAY (0xABABAB, EnumDye.LIGHT_GRAY),
    CYAN       (0x287697, EnumDye.CYAN),
    PURPLE     (0x7B2FBE, EnumDye.PURPLE),
    BLUE       (0x253192, EnumDye.BLUE),
    BROWN      (0x51301A, EnumDye.BROWN),
    GREEN      (0x3B511A, EnumDye.GREEN),
    RED        (0xB3312C, EnumDye.RED),
    BLACK      (0x1E1B1B, EnumDye.BLACK),

    /** Not painted. Numbered to match {@code AEColor.Transparent}. */
    NONE       (0xFFFFFF, null);
    // spotless:on

    /**
     * Cached so that reading a colour index does not clone the array. Ports are
     * grouped by colour every tick a machine looks for a recipe.
     */
    private static final PortColor[] VALUES = values();

    private final int rgb;
    private final EnumDye dye;

    PortColor(int rgb, EnumDye dye) {
        this.rgb = rgb;
        this.dye = dye;
    }

    /**
     * The colour as {@code 0xRRGGBB}, which is the form {@code colorMultiplier}
     * wants. {@link #NONE} answers white, so an unpainted port renders untinted.
     */
    public int getRgb() {
        return rgb;
    }

    /** Whether this is an actual colour rather than the absence of one. */
    public boolean isColored() {
        return this != NONE;
    }

    /**
     * What a painted port should render as, given what it would have rendered as
     * otherwise.
     *
     * A port's own colour wins over the machine's structure tint. That is the whole
     * point of painting one: the groups have to be tellable apart at a glance, which
     * they would not be if the machine's own colour scheme stayed on top. An unpainted
     * port is unchanged.
     *
     * @param fallback the tint with no colour on the port - structure tint, or the
     *                 configured default
     */
    public int tintOr(int fallback) {
        return isColored() ? rgb : fallback;
    }

    /**
     * The number vanilla wool metadata, {@code AEColor.ordinal()} and
     * {@code recolourBlock}'s colour argument all use for this colour.
     */
    public int toColorIndex() {
        return ordinal();
    }

    /**
     * The colour a wool metadata / {@code AEColor.ordinal()} / {@code
     * recolourBlock} number refers to.
     *
     * Anything outside the range answers {@link #NONE} rather than throwing. The
     * numbers come from other mods, and a port that quietly stays unpainted is a
     * better outcome than a crash inside someone else's item.
     */
    public static PortColor fromColorIndex(int index) {
        if (index < 0 || index >= VALUES.length) return NONE;
        return VALUES[index];
    }

    /**
     * The colour a stored name refers to, or {@link #NONE} for a name that matches
     * nothing.
     *
     * Names are what NBT holds, and NBT crosses builds and gets hand-edited. Falling
     * back beats {@code valueOf}, which throws - and something reading a port's colour
     * is usually deep inside loading a chunk.
     */
    public static PortColor fromName(String name) {
        if (name == null || name.isEmpty()) return NONE;
        for (PortColor color : VALUES) {
            if (color.name()
                .equals(name)) {
                return color;
            }
        }
        return NONE;
    }

    /** The dye that produces this colour, or null for {@link #NONE}. */
    public EnumDye toDye() {
        return dye;
    }

    /** The colour a dye produces. A null dye means {@link #NONE}. */
    public static PortColor fromDye(EnumDye dye) {
        if (dye == null) return NONE;
        for (PortColor color : VALUES) {
            if (color.dye == dye) return color;
        }
        return NONE;
    }

    public String getUnlocalizedName() {
        return "gui.port_color." + name();
    }
}
