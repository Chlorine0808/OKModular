package ruiseki.okmodular.structure;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;

/**
 * A tiered part that checks against every tier but hints and places the one picked by the
 * trigger's stack size: one for the lowest tier, n for the n-th, the highest past the end.
 */
public class TierByTriggerElement<T> implements IStructureElementChain<T> {

    private final IStructureElement<T> checker;
    private final IStructureElement<T>[] tiers;

    /**
     * @param checker        the element that decides whether a built block is valid
     * @param tiersAscending one placeable element per tier, lowest first, whose check has no side effects
     */
    @SuppressWarnings("unchecked")
    public TierByTriggerElement(IStructureElement<T> checker, List<IStructureElement<T>> tiersAscending) {
        if (tiersAscending.isEmpty()) {
            throw new IllegalArgumentException("a tiered part needs at least one tier");
        }
        this.checker = checker;
        this.tiers = tiersAscending.toArray(new IStructureElement[0]);
    }

    private IStructureElement<T> pick(ItemStack trigger) {
        int count = trigger == null ? 1 : trigger.stackSize;
        return tiers[Math.min(Math.max(count, 1), tiers.length) - 1];
    }

    @Override
    public IStructureElement<T>[] fallbacks() {
        return tiers;
    }

    @Override
    public boolean check(T t, World world, int x, int y, int z) {
        return checker.check(t, world, x, y, z);
    }

    @Override
    public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
        return checker.couldBeValid(t, world, x, y, z, trigger);
    }

    @Override
    public List<String> getDescription(T context) {
        return checker.getDescription(context);
    }

    @Override
    public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
        return pick(trigger).spawnHint(t, world, x, y, z, trigger);
    }

    @Override
    public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
        return pick(trigger).placeBlock(t, world, x, y, z, trigger);
    }

    @Override
    public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return pick(trigger).getBlocksToPlace(t, world, x, y, z, trigger, env);
    }

    /**
     * A block of any tier already standing is kept, as the old chain did. The tier elements have no
     * side effects in check, unlike the checker, which collects ports.
     */
    private boolean anyTierStands(T t, World world, int x, int y, int z) {
        for (IStructureElement<T> tier : tiers) {
            if (tier.check(t, world, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger, IItemSource s,
        EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
        if (anyTierStands(t, world, x, y, z)) {
            return PlaceResult.SKIP;
        }
        return pick(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
    }

    @Override
    public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        if (anyTierStands(t, world, x, y, z)) {
            return PlaceResult.SKIP;
        }
        return pick(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, env);
    }
}
