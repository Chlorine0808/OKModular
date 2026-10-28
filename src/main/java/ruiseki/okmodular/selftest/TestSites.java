package ruiseki.okmodular.selftest;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.selftest.judge.Observation;
import ruiseki.okmodular.selftest.scenario.Scenario;

/** Hands each scenario its own force-loaded patch of world far from spawn, and takes it back afterwards. */
final class TestSites {

    static final int BASE_X = 4096, BASE_Z = 4096, SPACING = 96;
    /** Blocks force-loaded around a site's origin in each horizontal direction. */
    static final int RADIUS = 32;
    /** The cleared box reaches this far below and above the origin, inside a stone shell. */
    static final int CLEAR_BELOW = 16, CLEAR_ABOVE = 32;

    private int next;

    Site allocate(Scenario s) {
        WorldServer world = world(s.dimension);
        int i = next++;
        // The origin sits in a chunk's middle, so a small machine never straddles a chunk border.
        Site site = new Site(this, s, world, BASE_X + i * SPACING + 8, s.y, BASE_Z + 8);
        try {
            force(site);
            clear(site);
        } catch (RuntimeException e) {
            // The caller never sees this site, so nobody else would release its ticket.
            unforce(site);
            throw e;
        }
        return site;
    }

    void release(Site site) {
        unforce(site);
        // Without players nothing ever queues these chunks, so a finished site would tick forever.
        queueUnload(site);
        site.world.theChunkProviderServer.unloadQueuedChunks();
    }

    /**
     * Unloads the site's chunks and loads them back, recording the controller NBT on both sides
     * ("before-unload", "after-reload") and whether a different TileEntity came back.
     */
    void unloadReload(Site site, Observation obs) {
        WorldServer world = site.world;
        ChunkProviderServer provider = world.theChunkProviderServer;
        TileEntity before = world.getTileEntity(site.x, site.y, site.z);
        if (before != null) obs.nbt.put("before-unload", NbtFlat.flatten(nbtOf(before)));

        unforce(site);
        queueUnload(site);
        int cx = site.x >> 4, cz = site.z >> 4;
        // unloadQueuedChunks handles at most 100 chunks per call and the queue may hold others.
        for (int i = 0; i < 10 && provider.chunkExists(cx, cz); i++) provider.unloadQueuedChunks();
        boolean gone = !provider.chunkExists(cx, cz);

        force(site);
        TileEntity after = world.getTileEntity(site.x, site.y, site.z);
        if (after != null) obs.nbt.put("after-reload", NbtFlat.flatten(nbtOf(after)));
        obs.chunkReloaded = gone && before != null && after != null && after != before;
        if (!gone) SelfTest.LOG.warn("selftest: chunk {},{} did not unload", cx, cz);
    }

    static NBTTagCompound nbtOf(TileEntity te) {
        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);
        return nbt;
    }

    private static WorldServer world(int dimension) {
        WorldServer w = DimensionManager.getWorld(dimension);
        if (w == null) {
            DimensionManager.initDimension(dimension);
            w = DimensionManager.getWorld(dimension);
        }
        if (w == null) throw new IllegalStateException("dimension " + dimension + " could not be loaded");
        return w;
    }

    /** Without a forced chunk a world with no players stops updating TileEntities after 1200 ticks. */
    private static void force(Site site) {
        ForgeChunkManager.Ticket t = ForgeChunkManager
            .requestTicket(OKModular.instance, site.world, ForgeChunkManager.Type.NORMAL);
        if (t == null) throw new IllegalStateException("no forced-chunk ticket for the selftest");
        // Held on the site at once, so a throw below still lets the caller release it.
        site.ticket = t;
        t.setChunkListDepth(chunksPerSide() * chunksPerSide());
        for (int cx = (site.x - RADIUS) >> 4; cx <= (site.x + RADIUS) >> 4; cx++) {
            for (int cz = (site.z - RADIUS) >> 4; cz <= (site.z + RADIUS) >> 4; cz++) {
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
                // Forcing does not load, and provideChunk hands out an empty chunk that drops every
                // setBlock when loadChunkOnProvideRequest is off.
                site.world.theChunkProviderServer.loadChunk(cx, cz);
            }
        }
    }

    /**
     * Empties the box around the origin (±RADIUS across, CLEAR_BELOW down, CLEAR_ABOVE up) and walls it
     * in stone: floor and sides always, a roof only in a world without sky, so a sky check still sees
     * the sky. The shell keeps terrain liquids out. Writes go straight into the chunk sections and
     * light is redone once per chunk; tens of thousands of setBlock calls would each relight.
     */
    private static void clear(Site site) {
        WorldServer w = site.world;
        boolean roof = w.provider.hasNoSky;
        int x0 = site.x - RADIUS - 1, x1 = site.x + RADIUS + 1;
        int z0 = site.z - RADIUS - 1, z1 = site.z + RADIUS + 1;
        int y0 = Math.max(1, site.y - CLEAR_BELOW - 1), y1 = Math.min(255, site.y + CLEAR_ABOVE + 1);
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                Chunk chunk = w.theChunkProviderServer.loadChunk(cx, cz);
                for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                    for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                        for (int y = y0; y <= y1; y++) {
                            boolean side = x == x0 || x == x1 || z == z0 || z == z1;
                            boolean shell = y == y0 || (y == y1 ? roof : side);
                            put(w, chunk, x & 15, y, z & 15, shell ? Blocks.stone : Blocks.air);
                        }
                    }
                }
                chunk.generateSkylightMap();
                chunk.setChunkModified();
            }
        }
    }

    private static void put(WorldServer w, Chunk chunk, int x, int y, int z, Block block) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        ExtendedBlockStorage section = sections[y >> 4];
        if (section == null) {
            if (block == Blocks.air) return;
            section = sections[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
        }
        if (section.getBlockByExtId(x, y & 15, z)
            .hasTileEntity(section.getExtBlockMetadata(x, y & 15, z))) {
            chunk.removeTileEntity(x, y, z);
        }
        section.func_150818_a(x, y & 15, z, block);
        section.setExtBlockMetadata(x, y & 15, z, 0);
        // Light from removed lava or glowstone would otherwise linger in the empty box.
        section.setExtBlocklightValue(x, y & 15, z, 0);
    }

    private static int chunksPerSide() {
        return (2 * RADIUS) / 16 + 1;
    }

    private static void unforce(Site site) {
        if (site.ticket == null) return;
        ForgeChunkManager.releaseTicket(site.ticket);
        site.ticket = null;
    }

    private static void queueUnload(Site site) {
        ChunkProviderServer provider = site.world.theChunkProviderServer;
        for (int cx = (site.x - RADIUS) >> 4; cx <= (site.x + RADIUS) >> 4; cx++) {
            for (int cz = (site.z - RADIUS) >> 4; cz <= (site.z + RADIUS) >> 4; cz++) {
                provider.unloadChunksIfNotNearSpawn(cx, cz);
            }
        }
    }

    /** One scenario's patch of world; x, y, z is the origin every relative position is measured from. */
    static final class Site {

        final TestSites owner;
        final Scenario scenario;
        final WorldServer world;
        final int x, y, z;
        ForgeChunkManager.Ticket ticket;

        Site(TestSites owner, Scenario scenario, WorldServer world, int x, int y, int z) {
            this.owner = owner;
            this.scenario = scenario;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
