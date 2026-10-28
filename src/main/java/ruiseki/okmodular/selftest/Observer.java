package ruiseki.okmodular.selftest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.error.ErrorReason;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.StructureInput;
import ruiseki.okmodular.api.recipe.io.StructureOutput;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.recipe.RecipeLoader;
import ruiseki.okmodular.common.tile.TEMachineController;
import ruiseki.okmodular.common.tile.fluid.AbstractFluidPortTE;
import ruiseki.okmodular.common.tile.item.AbstractItemIOPortTE;
import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.judge.MachineSample;
import ruiseki.okmodular.selftest.judge.Observation;
import ruiseki.okmodular.selftest.scenario.Region;
import ruiseki.okmodular.structure.StructureManager;

/** Reads the machine and the world around a site into the selftest's Observation. */
final class Observer {

    private Observer() {}

    static MachineSample sample(MachineRig rig, int tick) {
        TEMachineController c = rig.controller();
        ProcessAgent p = c.getProcessAgent();
        String failure = conditionFailure(c);
        if (failure != null) rig.lastConditionFailure = failure;
        String recipe = null;
        if (p.isRunning() || p.isWaitingForOutput()) {
            recipe = p.getCurrentRecipeName() != null ? p.getCurrentRecipeName() : "(unnamed)";
        }
        return new MachineSample(tick, c.isFormed(), recipe, p.getProgressPercent(), p.wasBlockedByCondition());
    }

    /** The region's blocks, relative to the site origin; marks the controller and structure IO anchors. */
    static BlockGrid snapshot(TestSites.Site site, Region region, MachineRig rig) {
        BlockGrid g = new BlockGrid(region.from, region.to);
        int[] lo = g.from(), hi = g.to();
        World w = site.world;
        for (int dx = lo[0]; dx <= hi[0]; dx++) {
            for (int dy = lo[1]; dy <= hi[1]; dy++) {
                for (int dz = lo[2]; dz <= hi[2]; dz++) {
                    int x = site.x + dx, y = site.y + dy, z = site.z + dz;
                    g.set(dx, dy, dz, MachineRig.idOf(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z)));
                }
            }
        }
        if (rig != null) {
            TEMachineController c = rig.controller();
            for (char symbol : anchorSymbols(c)) {
                for (ChunkCoordinates p : c.getSymbolPositions(symbol)) {
                    int dx = p.posX - site.x, dy = p.posY - site.y, dz = p.posZ - site.z;
                    if (g.contains(dx, dy, dz)) g.mark(dx, dy, dz, BlockGrid.Marker.ANCHOR);
                }
            }
            if (g.contains(0, 0, 0)) g.mark(0, 0, 0, BlockGrid.Marker.CONTROLLER);
        }
        return g;
    }

    static void recordNbt(MachineRig rig, String label, Observation obs) {
        obs.nbt.put(label, NbtFlat.flatten(TestSites.nbtOf(rig.controller())));
    }

    /** Fills everything the judge reads at the end of a run; rig is null for a scenario without a machine. */
    static void finish(MachineRig rig, TestSites.Site site, Observation obs) {
        if (rig != null) {
            TEMachineController c = rig.controller();
            countPorts(rig, false, obs.outputItems);
            countPorts(rig, true, obs.inputItems);
            obs.componentTiers.putAll(c.getComponentTiers());
            String error = c.getStructureAgent()
                .getLastValidationError();
            obs.validationError = error == null || error.isEmpty() ? null : error;
            String failure = conditionFailure(c);
            obs.conditionFailure = failure != null ? failure : rig.lastConditionFailure;
        }
        countDrops(site, obs.droppedItems);
        readErrorFiles(obs.files);
    }

    private static String conditionFailure(TEMachineController c) {
        String recipeLevel = c.getProcessAgent()
            .getConditionFailure();
        if (recipeLevel != null) return recipeLevel;
        // The machine's own conditions report through the process error instead.
        return c.getLastProcessErrorReason() == ErrorReason.CONDITION_NOT_MET ? c.getLastProcessErrorDetail() : null;
    }

    /** Symbols the machine's recipes name in structure inputs and outputs, i.e. its structure IO anchors. */
    private static Set<Character> anchorSymbols(TEMachineController c) {
        Set<Character> out = new LinkedHashSet<>();
        List<IModularRecipe> recipes = RecipeLoader.getInstance()
            .getRecipes(
                c.getRecipeGroups()
                    .toArray(new String[0]));
        for (IModularRecipe r : recipes) {
            for (IRecipeInput in : r.getInputs()) {
                if (in instanceof StructureInput) out.add(((StructureInput) in).getSymbol());
            }
            for (IRecipeOutput o : r.getOutputs()) {
                if (o instanceof StructureOutput) out.add(((StructureOutput) o).getSymbol());
            }
        }
        return out;
    }

    /** Items by id; fluids are counted in mB under "fluid:" plus the fluid name. */
    private static void countPorts(MachineRig rig, boolean input, Map<String, Integer> out) {
        for (AbstractItemIOPortTE port : rig.ports(input, IPortType.Type.ITEM, AbstractItemIOPortTE.class)) {
            for (int slot = 0; slot < port.getSizeInventory(); slot++) {
                ItemStack s = port.getStackInSlot(slot);
                if (s != null && s.stackSize > 0) out.merge(MachineRig.idOf(s), s.stackSize, Integer::sum);
            }
        }
        for (AbstractFluidPortTE port : rig.ports(input, IPortType.Type.FLUID, AbstractFluidPortTE.class)) {
            FluidStack f = port.getStoredFluid();
            if (f != null && f.amount > 0) out.merge(
                "fluid:" + f.getFluid()
                    .getName(),
                f.amount,
                Integer::sum);
        }
    }

    /** Dropped items are counted anywhere inside the site's cleared box, down to its floor. */
    static AxisAlignedBB dropBox(int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(
            x - TestSites.RADIUS,
            y - TestSites.CLEAR_BELOW,
            z - TestSites.RADIUS,
            x + TestSites.RADIUS + 1,
            y + TestSites.CLEAR_ABOVE + 1,
            z + TestSites.RADIUS + 1);
    }

    private static void countDrops(TestSites.Site site, Map<String, Integer> out) {
        for (Object o : site.world.getEntitiesWithinAABB(EntityItem.class, dropBox(site.x, site.y, site.z))) {
            ItemStack s = ((EntityItem) o).getEntityItem();
            if (s != null && s.stackSize > 0) out.merge(MachineRig.idOf(s), s.stackSize, Integer::sum);
        }
    }

    /** Every *error* file under config/okmodular, keyed by its path relative to that folder. */
    private static void readErrorFiles(Map<String, String> out) {
        File dir = StructureManager.getInstance()
            .getConfigDir();
        if (dir == null || !dir.isDirectory()) return;
        Path root = dir.toPath();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(
                    p -> p.getFileName()
                        .toString()
                        .contains("error"))
                .forEach(p -> {
                    try {
                        out.put(
                            root.relativize(p)
                                .toString()
                                .replace('\\', '/'),
                            new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        SelfTest.LOG.warn("selftest: could not read {}", p, e);
                    }
                });
        } catch (IOException e) {
            SelfTest.LOG.warn("selftest: could not list {}", dir, e);
        }
    }
}
