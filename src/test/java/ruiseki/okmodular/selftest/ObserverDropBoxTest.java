package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.AxisAlignedBB;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Dropped items were counted within 8 blocks of the origin. A drop pushed off the top of a
// machine falls to the site's floor 16 below and was missed (s3.6-port-full: three cobblestone
// lay at y -15.875, droppedCount 0). The site is cleared inside a stone shell, so everything
// inside that shell belongs to the scenario; the next site is SPACING blocks away.
class ObserverDropBoxTest {

    private static boolean inside(AxisAlignedBB b, double x, double y, double z) {
        return x >= b.minX && x < b.maxX && y >= b.minY && y < b.maxY && z >= b.minZ && z < b.maxZ;
    }

    @Test
    @DisplayName("落ちた品は区画の床まで数える")
    void countsDropsDownToTheSiteFloor() {
        AxisAlignedBB box = Observer.dropBox(100, 64, 200);
        assertTrue(inside(box, 100 - 8.2, 64 - 15.875, 200 + 0.5));
        assertTrue(inside(box, 100 + TestSites.RADIUS + 0.5, 64 + TestSites.CLEAR_ABOVE + 0.5, 200 - TestSites.RADIUS));
    }

    @Test
    @DisplayName("隣の区画には届かない")
    void staysOutOfTheNextSite() {
        AxisAlignedBB box = Observer.dropBox(100, 64, 200);
        assertFalse(inside(box, 100 + TestSites.SPACING - TestSites.RADIUS - 1, 64, 200));
    }
}
