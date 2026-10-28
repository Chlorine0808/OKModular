package ruiseki.okmodular.selftest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
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

    private int next;

    Site allocate(Scenario s) {
        WorldServer world = world(s.dimension);
        int i = next++;
        // The origin sits in a chunk's middle, so a small machine never straddles a chunk border.
        Site site = new Site(this, s, world, BASE_X + i * SPACING + 8, s.y, BASE_Z + 8);
        force(site);
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
        t.setChunkListDepth(chunksPerSide() * chunksPerSide());
        for (int cx = (site.x - RADIUS) >> 4; cx <= (site.x + RADIUS) >> 4; cx++) {
            for (int cz = (site.z - RADIUS) >> 4; cz <= (site.z + RADIUS) >> 4; cz++) {
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
                // Forcing does not load, and provideChunk hands out an empty chunk that drops every
                // setBlock when loadChunkOnProvideRequest is off.
                site.world.theChunkProviderServer.loadChunk(cx, cz);
            }
        }
        site.ticket = t;
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
