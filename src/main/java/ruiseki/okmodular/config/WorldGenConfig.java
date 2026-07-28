package ruiseki.okmodular.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import ruiseki.okmodular.Reference;

@Config.Comment("World generation settings")
@Config.LangKey(Reference.CONFIG + "worldGenConfig")
@Config(
    modid = Reference.MOD_ID,
    category = "worldGen",
    configSubDirectory = Reference.CONFIG_DIR,
    filename = "worldgen")
public class WorldGenConfig {

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(WorldGenConfig.class);
    }

    @Config.Comment("Helium gas pocket generation settings")
    @Config.LangKey(Reference.CONFIG + "heliumGasGen")
    public static final GasPocketGenSettings helium = new GasPocketGenSettings(true, 20, 0.01f, 10, 60);

    @Config.Comment("Chlorine gas pocket generation settings")
    @Config.LangKey(Reference.CONFIG + "chlorineGasGen")
    public static final GasPocketGenSettings chlorine = new GasPocketGenSettings(true, 15, 0.01f, 5, 30);

    @Config.Comment("Fluorine gas pocket generation settings")
    @Config.LangKey(Reference.CONFIG + "fluorineGasGen")
    public static final GasPocketGenSettings fluorine = new GasPocketGenSettings(true, 10, 0.01f, 5, 20);

    public static class GasPocketGenSettings {

        @Config.Comment("Enable gas pocket generation")
        @Config.DefaultBoolean(true)
        public boolean enable;

        @Config.Comment("Size of each gas pocket (blocks)")
        @Config.DefaultInt(15)
        @Config.RangeInt(min = 0)
        public int pocketSize;

        @Config.Comment("Number of pockets per chunk")
        @Config.DefaultFloat(0.01f)
        @Config.RangeFloat(min = 0f)
        public float pocketsPerChunk;

        @Config.Comment("Minimum generation height")
        @Config.DefaultInt(5)
        @Config.RangeInt(min = 0)
        public int minHeight;

        @Config.Comment("Maximum generation height")
        @Config.DefaultInt(40)
        @Config.RangeInt(min = 0)
        public int maxHeight;

        public GasPocketGenSettings() {
            this(true, 15, 0.01f, 5, 40);
        }

        public GasPocketGenSettings(boolean enable, int pocketSize, float pocketsPerChunk, int minHeight,
            int maxHeight) {
            this.enable = enable;
            this.pocketSize = pocketSize;
            this.pocketsPerChunk = pocketsPerChunk;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
    }
}
