package ruiseki.okmodular.selftest.judge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Why z = 4.5: the two-sided false-fail probability is about 7e-6 per check. A run has fewer than 100 statistical
 * checks, so a spurious red is below 1e-3 per night.
 */
class BinomialBoundsTest {

    @Test
    @DisplayName("n=100, p=0.5 は [28, 72]")
    void halfOfHundred() {
        assertArrayEquals(new int[] { 28, 72 }, BinomialBounds.of(100, 0.5, 4.5));
    }

    @Test
    @DisplayName("p が 0 か 1 なら幅は 0")
    void certainties() {
        assertArrayEquals(new int[] { 0, 0 }, BinomialBounds.of(50, 0.0, 4.5));
        assertArrayEquals(new int[] { 50, 50 }, BinomialBounds.of(50, 1.0, 4.5));
    }

    @Test
    @DisplayName("下端は 0 に切られる")
    void clampedToRange() {
        assertEquals(0, BinomialBounds.of(10, 0.05, 4.5)[0]);
        assertEquals(10, BinomialBounds.of(10, 0.95, 4.5)[1]);
    }
}
