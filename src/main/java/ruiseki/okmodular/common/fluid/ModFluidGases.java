package ruiseki.okmodular.common.fluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import cpw.mods.fml.common.registry.GameRegistry;
import ruiseki.okcore.util.Logger;
import ruiseki.okcore.world.gen.SimpleMinableWorldGenerator;
import ruiseki.okcore.world.gen.WorldGenGasPocket;
import ruiseki.okcore.world.gen.WorldGenMinableExtended;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.common.block.BlockFluidBase;
import ruiseki.okmodular.common.block.BlockLiquidBase;
import ruiseki.okmodular.common.item.ItemBlockFluid;
import ruiseki.okmodular.config.WorldGenConfig;

/**
 * Registration class for all fluid materials.
 * All fluids use BlockLiquidBase (vanilla water-style flow).
 * Flow direction is determined by density: positive = down, negative = up.
 */
public class ModFluidGases {

    public static final Map<EnumFluidMaterial, Fluid> FLUIDS = new HashMap<>();
    public static final Map<EnumFluidMaterial, BlockFluidBase> BLOCKS = new HashMap<>();

    public static void preInit() {
        for (EnumFluidMaterial mat : EnumFluidMaterial.values()) {
            // 1. Fluid registration
            Fluid fluid = new FluidOK(mat).setDensity(mat.getDensity())
                .setTemperature(mat.getTemperature())
                .setGaseous(mat.isGaseous())
                .setViscosity(mat.getViscosity());

            if (!FluidRegistry.registerFluid(fluid)) {
                fluid = FluidRegistry.getFluid(mat.getName());
                Logger.info("Using existing fluid for " + mat.getName());
            }
            FLUIDS.put(mat, fluid);

            // 2. Block registration
            String blockName = (mat.isGaseous() ? "gas." : "liquid.") + mat.getName();
            BlockFluidBase block = (BlockFluidBase) new BlockLiquidBase(fluid, null).setMaterial(mat)
                .setQuantaPerBlock(mat.getQuantaPerBlock())
                .setDrag(mat.getDrag())
                .setBlockName(blockName);

            GameRegistry.registerBlock(block, ItemBlockFluid.class, block.getUnlocalizedName());
            BLOCKS.put(mat, block);

            // 3. Associate block with fluid (skip if another mod already claimed it)
            if (fluid.getBlock() == null) {
                fluid.setBlock(block);
                Logger.info("Associated " + mat.getName() + " with our block.");
            } else {
                Logger.info("Fluid " + mat.getName() + " already has a block from another mod.");
            }
        }

        registerWorldGen();
    }

    private static void registerWorldGen() {
        List<WorldGenMinableExtended> generators = new ArrayList<>();

        addGasPocket(generators, EnumFluidMaterial.HELIUM, WorldGenConfig.helium);
        addGasPocket(generators, EnumFluidMaterial.CHLORINE, WorldGenConfig.chlorine);
        addGasPocket(generators, EnumFluidMaterial.FLUORINE, WorldGenConfig.fluorine);

        if (!generators.isEmpty()) {
            GameRegistry.registerWorldGenerator(new SimpleMinableWorldGenerator(OKModular.instance, generators), 10);
        }
    }

    private static void addGasPocket(List<WorldGenMinableExtended> generators, EnumFluidMaterial mat,
        WorldGenConfig.GasPocketGenSettings cfg) {
        if (!cfg.enable) return;

        BlockFluidBase block = BLOCKS.get(mat);
        if (block == null) return;

        generators.add(new WorldGenGasPocket(block, cfg.pocketSize, cfg.pocketsPerChunk, cfg.minHeight, cfg.maxHeight));
    }
}
