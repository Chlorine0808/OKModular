package ruiseki.okmodular.common.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import ruiseki.okcore.datastructure.BlockPos;
import ruiseki.okcore.network.CodecField;
import ruiseki.okcore.network.PacketCodec;
import ruiseki.okmodular.core.energy.IOKEnergyTile;

public class PacketEnergy extends PacketCodec {

    @CodecField
    private BlockPos pos;

    @CodecField
    private int storedEnergy;

    public PacketEnergy() {}

    public PacketEnergy(IOKEnergyTile tile) {
        storedEnergy = tile.getEnergyStored();
        pos = tile.getPos();
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public void actionClient(World world, EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (te instanceof IOKEnergyTile energyTile) {
            energyTile.setEnergyStored(storedEnergy);
        }
    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {

    }

}
