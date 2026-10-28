package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.selftest.judge.Observation;
import ruiseki.okmodular.selftest.scenario.InputSpec;
import ruiseki.okmodular.selftest.scenario.Region;
import ruiseki.okmodular.selftest.scenario.Step;

/** Carries out one scenario Step on the live world; positions are relative to the site origin. */
final class WorldActions {

    /** Long enough that the weather never changes by itself during a run. */
    private static final int WEATHER_HOLD_TICKS = 1_000_000;

    private WorldActions() {}

    static void apply(Step step, TestSites.Site site, MachineRig rig, Observation obs) {
        JsonObject a = step.args;
        switch (step.action) {
            case "setBlock": {
                int[] p = triple(a, "pos");
                setBlock(site, p[0], p[1], p[2], str(a, "block"));
                break;
            }
            case "fill": {
                int[] from = triple(a, "from"), to = triple(a, "to");
                BlockId id = BlockId.parse(str(a, "block"));
                Block block = MachineRig.block(id.name);
                for (int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
                    for (int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
                        for (int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
                            site.world.setBlock(site.x + x, site.y + y, site.z + z, block, id.meta, 3);
                        }
                    }
                }
                break;
            }
            case "weather":
                weather(site.world, a.has("value") ? str(a, "value") : str(a, "weather"));
                break;
            case "biome":
                biome(site, triple(a, "from"), triple(a, "to"), str(a, "biome"));
                break;
            case "insert":
                machine(rig, step).insert(inputs(a));
                break;
            case "clearOutputs":
                machine(rig, step).clearOutputs();
                break;
            case "fillOutputs":
                machine(rig, step).fillOutputs(str(a, "item"));
                break;
            case "unloadReload":
                site.owner.unloadReload(site, obs);
                if (rig != null) rig.reattach();
                break;
            case "recordNbt":
                Observer.recordNbt(machine(rig, step), str(a, "label"), obs);
                break;
            case "snapshot": {
                String name = str(a, "name");
                Region r = a.has("from") ? new Region(triple(a, "from"), triple(a, "to"))
                    : site.scenario.snapshots.get(name);
                if (r == null) throw new IllegalArgumentException("snapshot \"" + name + "\" has no region");
                obs.snapshots.put(name, Observer.snapshot(site, r, rig));
                break;
            }
            default:
                throw new IllegalArgumentException("unknown action " + step.action);
        }
    }

    /** Sets raining and thundering, their timers, and the strengths the world reads, all at once. */
    static void weather(WorldServer w, String kind) {
        if (!kind.equals("clear") && !kind.equals("rain") && !kind.equals("thunder")) {
            throw new IllegalArgumentException("weather must be clear, rain or thunder, not \"" + kind + "\"");
        }
        boolean rain = !kind.equals("clear"), thunder = kind.equals("thunder");
        WorldInfo info = w.getWorldInfo();
        info.setRaining(rain);
        info.setThundering(thunder);
        info.setRainTime(WEATHER_HOLD_TICKS);
        info.setThunderTime(WEATHER_HOLD_TICKS);
        // The flags alone only steer the strengths by 0.01 per tick; isRaining() reads the strengths.
        w.prevRainingStrength = w.rainingStrength = rain ? 1f : 0f;
        w.prevThunderingStrength = w.thunderingStrength = thunder ? 1f : 0f;
    }

    private static void setBlock(TestSites.Site site, int dx, int dy, int dz, String blockId) {
        BlockId id = BlockId.parse(blockId);
        site.world.setBlock(site.x + dx, site.y + dy, site.z + dz, MachineRig.block(id.name), id.meta, 3);
    }

    /** Rewrites the chunks' biome arrays over the columns from..to (y is ignored). */
    private static void biome(TestSites.Site site, int[] from, int[] to, String name) {
        BiomeGenBase biome = biomeNamed(name);
        for (int dx = Math.min(from[0], to[0]); dx <= Math.max(from[0], to[0]); dx++) {
            for (int dz = Math.min(from[2], to[2]); dz <= Math.max(from[2], to[2]); dz++) {
                int x = site.x + dx, z = site.z + dz;
                Chunk chunk = site.world.getChunkFromBlockCoords(x, z);
                byte[] biomes = chunk.getBiomeArray();
                biomes[(z & 15) << 4 | (x & 15)] = (byte) biome.biomeID;
                chunk.setBiomeArray(biomes);
                chunk.setChunkModified();
            }
        }
    }

    /** By id, or by name ignoring case and spaces ("desert", "ExtremeHills"). */
    private static BiomeGenBase biomeNamed(String name) {
        String want = name.replace(" ", "")
            .toLowerCase();
        for (BiomeGenBase b : BiomeGenBase.getBiomeGenArray()) {
            if (b == null) continue;
            if (Integer.toString(b.biomeID)
                .equals(name)
                || b.biomeName.replace(" ", "")
                    .toLowerCase()
                    .equals(want)) {
                return b;
            }
        }
        throw new IllegalArgumentException("unknown biome \"" + name + "\"");
    }

    private static List<InputSpec> inputs(JsonObject a) {
        JsonElement el = a.get("inputs");
        if (el == null || !el.isJsonArray()) throw new IllegalArgumentException("insert needs an \"inputs\" array");
        List<InputSpec> out = new ArrayList<>();
        for (JsonElement e : (JsonArray) el) {
            JsonObject i = e.getAsJsonObject();
            int amount = i.has("amount") ? i.get("amount")
                .getAsInt() : 1;
            if (i.has("energy")) out.add(
                new InputSpec(
                    InputSpec.Kind.ENERGY,
                    null,
                    i.get("energy")
                        .getAsInt()));
            else if (i.has("item")) out.add(new InputSpec(InputSpec.Kind.ITEM, str(i, "item"), amount));
            else if (i.has("fluid")) out.add(new InputSpec(InputSpec.Kind.FLUID, str(i, "fluid"), amount));
            else throw new IllegalArgumentException("an input needs item, fluid or energy");
        }
        return out;
    }

    private static MachineRig machine(MachineRig rig, Step step) {
        if (rig == null) throw new IllegalStateException("step " + step.action + " needs a machine");
        return rig;
    }

    private static String str(JsonObject a, String key) {
        JsonElement e = a.get(key);
        if (e == null || e.isJsonNull()) throw new IllegalArgumentException("missing argument \"" + key + "\"");
        return e.getAsString();
    }

    private static int[] triple(JsonObject a, String key) {
        JsonElement e = a.get(key);
        if (e == null || !e.isJsonArray()
            || e.getAsJsonArray()
                .size() != 3)
            throw new IllegalArgumentException("\"" + key + "\" needs 3 numbers");
        JsonArray arr = e.getAsJsonArray();
        return new int[] { arr.get(0)
            .getAsInt(),
            arr.get(1)
                .getAsInt(),
            arr.get(2)
                .getAsInt() };
    }
}
