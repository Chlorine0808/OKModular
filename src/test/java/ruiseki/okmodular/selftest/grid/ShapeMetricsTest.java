package ruiseki.okmodular.selftest.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShapeMetricsTest {

    private static final String B = "minecraft:stone";

    private static BlockGrid ball(int cx, int radius, int half) {
        BlockGrid g = new BlockGrid(new int[] { -half, -half, -half }, new int[] { half, half, half });
        for (int x = -half; x <= half; x++) for (int y = -half; y <= half; y++) for (int z = -half; z <= half; z++) {
            if ((x - cx) * (x - cx) + y * y + z * z <= radius * radius) g.set(x, y, z, B);
        }
        return g;
    }

    @Test
    @DisplayName("対称な球の重心は中心")
    void centroidOfSymmetricSphereIsCentre() {
        double[] c = ShapeMetrics.centroid(ball(0, 3, 5), B);
        for (double v : c) assertTrue(Math.abs(v) < 1e-9);
    }

    @Test
    @DisplayName("ずらした球の重心もずれる")
    void centroidOfShiftedBallIsShifted() {
        double[] c = ShapeMetrics.centroid(ball(2, 3, 6), B);
        assertEquals(2.0, c[0], 1e-9);
    }

    @Test
    @DisplayName("中実の立方体はまとまっている")
    void solidCubeIsFullyClustered() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 7, 7, 7 });
        for (int x = 2; x < 6; x++) for (int y = 2; y < 6; y++) for (int z = 2; z < 6; z++) g.set(x, y, z, B);
        assertTrue(ShapeMetrics.sameNeighbourRatio(g, B) > 0.6);
        assertEquals(64.0 / 512.0, ShapeMetrics.density(g, B), 1e-12);
    }

    @Test
    @DisplayName("市松模様はまとまらない")
    void checkerboardIsNotClustered() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 5, 5, 5 });
        for (int x = 0; x < 6; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 6; z++) {
            if ((x + y + z) % 2 == 0) g.set(x, y, z, B);
        }
        assertEquals(0.0, ShapeMetrics.sameNeighbourRatio(g, B), 0.0);
    }

    @Test
    @DisplayName("段階のある球は外側ほど薄い")
    void radialDensityFallsForAGradedBall() {
        BlockGrid g = ball(0, 6, 7);
        // thin the outer shell: keep only every third cell beyond radius 3
        for (int x = -7; x <= 7; x++) for (int y = -7; y <= 7; y++) for (int z = -7; z <= 7; z++) {
            if (x * x + y * y + z * z > 9 && (x + y + z) % 3 != 0) g.set(x, y, z, "minecraft:air");
        }
        double[] d = ShapeMetrics.radialDensity(g, B, new double[] { 0, 0, 0 }, 3, 7.0);
        assertTrue(d[0] > d[1]);
        assertTrue(d[1] > d[2]);
    }

    @Test
    @DisplayName("差分のマス数を数える")
    void differingCellsCountsChanges() {
        BlockGrid a = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 2, 2, 2 });
        BlockGrid b = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 2, 2, 2 });
        a.set(0, 0, 0, B);
        b.set(0, 0, 0, B);
        assertEquals(0, ShapeMetrics.differingCells(a, b));
        b.set(1, 1, 1, B);
        a.set(2, 2, 2, "minecraft:dirt");
        b.set(2, 2, 2, B);
        assertEquals(2, ShapeMetrics.differingCells(a, b));
    }

    @Test
    @DisplayName("x の縞は z と y 方向に揃う")
    void stripesAlongXAreUniformAlongZ() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 3, 3, 3 });
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
            if (x % 2 == 0) g.set(x, y, z, B);
        }
        assertTrue(ShapeMetrics.uniformAlong(g, B, 'z'));
        assertTrue(ShapeMetrics.uniformAlong(g, B, 'y'));
        assertFalse(ShapeMetrics.uniformAlong(g, B, 'x'));
    }
}
