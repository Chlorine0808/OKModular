package ruiseki.okmodular.common.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.block.material.Material;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.core.block.BlockOK;
import ruiseki.okmodular.core.item.ItemOK;

/**
 * Every block and item of this mod must show up in the creative inventory.
 *
 * Nothing set a tab after the split from OmoshiroiKamo, so none of them could be found there.
 * The base classes set it, so a new block or item gets it without remembering to.
 */
class CreativeTabTest {

    @Test
    void theTabIsNamedAfterTheMod() {
        assertNotNull(OKModularCreativeTab.TAB);
        assertEquals("okmodular", OKModularCreativeTab.TAB.getTabLabel());
    }

    @Test
    void aBlockIsPutInTheTab() {
        assertSame(OKModularCreativeTab.TAB, new BlockOK("test_block", Material.iron).getCreativeTabToDisplayOn());
    }

    @Test
    void anItemIsPutInTheTab() {
        assertSame(OKModularCreativeTab.TAB, new ItemOK("test_item").getCreativeTab());
    }
}
