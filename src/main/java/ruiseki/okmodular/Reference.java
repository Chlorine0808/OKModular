package ruiseki.okmodular;

public class Reference {

    public static final String MOD_ID = Tags.MOD_ID;
    public static final String MOD_NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;
    public static final String DEPENDENCIES = "required-after:okcore;" + "required-after:gtnhlib@[0.11.19,);"
        + "required-after:structurelib@[1.4.39,);"
        + "required-after:modularui2@[2.3.75-1.7.10,);"
        + "after:Thaumcraft;"
        + "after:Mekanism;"
        + "after:appliedenergistics2;"
        + "after:thaumicenergistics;"
        + "after:Botania;"
        + "after:NotEnoughItems;"
        + "after:Waila;";

    public static final String PROXY_COMMON = Tags.MOD_GROUP + ".CommonProxy";
    public static final String PROXY_CLIENT = Tags.MOD_GROUP + ".ClientProxy";
    public static final String GUI_FACTORY = Tags.MOD_GROUP + ".config.GuiConfigFactory";

    public static final String PREFIX_MOD = MOD_ID + ":";
    public static final String PREFIX_GUI = PREFIX_MOD + "textures/gui/";
    public static final String PREFIX_BLOCK = PREFIX_MOD + "textures/blocks/";
    public static final String PREFIX_ITEM = PREFIX_MOD + "textures/items/";
    public static final String PREFIX_MODEL = PREFIX_MOD + "models/";
    public static final String CONFIG = "config.";
    public static final String TOOLTIP = "tooltip.";
}
