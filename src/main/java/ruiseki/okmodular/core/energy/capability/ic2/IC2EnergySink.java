package ruiseki.okmodular.core.energy.capability.ic2;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import ic2.api.energy.tile.IEnergySink;
import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.core.energy.capability.EnergyIO;

public class IC2EnergySink implements EnergyIO {

    private final TileEntity tile;
    private final ForgeDirection side;

    public IC2EnergySink(TileEntity tile, ForgeDirection side) {
        this.tile = tile;
        this.side = side;
    }

    @Override
    public int insert(int amount, boolean simulate) {
        if (!(tile instanceof IEnergySink sink)) {
            return 0;
        }
        double euAmount = (double) amount / MachineryConfig.rftToEU;
        double leftover = euAmount;

        if (!simulate) {
            leftover = sink.injectEnergy(side, euAmount, 0);
        }

        return (int) Math.round(leftover * MachineryConfig.rftToEU);
    }

    @Override
    public boolean canConnect() {
        if (!(tile instanceof IEnergySink sink)) {
            return false;
        }
        return sink.acceptsEnergyFrom(tile, side);
    }

    @Override
    public int extract(int amount, boolean simulate) {
        return 0;
    }
}
