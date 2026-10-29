package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;

/**
 * A tiered part places the candidate picked by the blueprint's stack size (P23).
 *
 * One blueprint builds the lowest tier, n blueprints the n-th lowest, and anything past the last
 * candidate the highest, as StructureLib's ofBlocksTiered does. Checking a built structure is left
 * to the old chain, so every tier still forms the machine.
 */
class TierByTriggerElementTest {

    /** Remembers which candidate was asked to place or hint. */
    private static final class Candidate implements IStructureElement<Object> {

        private final String name;
        private final List<String> log;
        private final boolean standing;

        Candidate(String name, List<String> log) {
            this(name, log, false);
        }

        Candidate(String name, List<String> log, boolean standing) {
            this.name = name;
            this.log = log;
            this.standing = standing;
        }

        @Override
        public boolean check(Object t, World world, int x, int y, int z) {
            return standing;
        }

        @Override
        public PlaceResult survivalPlaceBlock(Object t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            log.add("survival " + name);
            return PlaceResult.REJECT;
        }

        @Override
        public boolean spawnHint(Object t, World world, int x, int y, int z, ItemStack trigger) {
            log.add("hint " + name);
            return true;
        }

        @Override
        public boolean placeBlock(Object t, World world, int x, int y, int z, ItemStack trigger) {
            log.add("place " + name);
            return true;
        }
    }

    private static final class FixedCheck implements IStructureElement<Object> {

        private final boolean result;

        FixedCheck(boolean result) {
            this.result = result;
        }

        @Override
        public boolean check(Object t, World world, int x, int y, int z) {
            return result;
        }

        @Override
        public boolean spawnHint(Object t, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        @Override
        public boolean placeBlock(Object t, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }
    }

    private static ItemStack blueprints(int count) {
        return new ItemStack((Item) null, count, 0);
    }

    private static TierByTriggerElement<Object> element(List<String> log, boolean checkResult) {
        return new TierByTriggerElement<>(
            new FixedCheck(checkResult),
            Arrays.asList(new Candidate("t1", log), new Candidate("t2", log), new Candidate("t3", log)));
    }

    @Test
    void oneBlueprintPlacesTheLowestTier() {
        List<String> log = new ArrayList<>();
        element(log, false).placeBlock(null, null, 0, 0, 0, blueprints(1));
        assertEquals(Arrays.asList("place t1"), log);
    }

    @Test
    void stackSizePicksTheNthTier() {
        List<String> log = new ArrayList<>();
        TierByTriggerElement<Object> element = element(log, false);
        element.placeBlock(null, null, 0, 0, 0, blueprints(2));
        element.spawnHint(null, null, 0, 0, 0, blueprints(3));
        assertEquals(Arrays.asList("place t2", "hint t3"), log);
    }

    @Test
    void moreBlueprintsThanTiersStayOnTheHighest() {
        List<String> log = new ArrayList<>();
        element(log, false).placeBlock(null, null, 0, 0, 0, blueprints(64));
        assertEquals(Arrays.asList("place t3"), log);
    }

    @Test
    void noTriggerPlacesTheLowestTier() {
        List<String> log = new ArrayList<>();
        element(log, false).placeBlock(null, null, 0, 0, 0, null);
        assertEquals(Arrays.asList("place t1"), log);
    }

    @Test
    void survivalBuildSkipsABlockOfAnotherTier() {
        List<String> log = new ArrayList<>();
        TierByTriggerElement<Object> element = new TierByTriggerElement<>(
            new FixedCheck(true),
            Arrays.asList(new Candidate("t1", log), new Candidate("t2", log, true)));
        assertEquals(PlaceResult.SKIP, element.survivalPlaceBlock(null, null, 0, 0, 0, blueprints(1), null));
        assertEquals(Arrays.asList(), log);
    }

    @Test
    void survivalBuildPlacesThePickedTierOnAnEmptySpot() {
        List<String> log = new ArrayList<>();
        element(log, false).survivalPlaceBlock(null, null, 0, 0, 0, blueprints(2), null);
        assertEquals(Arrays.asList("survival t2"), log);
    }

    @Test
    void checkIsLeftToTheWholeChain() {
        assertTrue(element(new ArrayList<>(), true).check(null, null, 0, 0, 0));
        assertFalse(element(new ArrayList<>(), false).check(null, null, 0, 0, 0));
    }
}
