package ruiseki.okmodular.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ruiseki.okmodular.common.item.ItemStructureWand;
import ruiseki.okmodular.structure.StructureConstants;

/**
 * Renders the Structure Wand selection with a translucent cyan outline.
 * Registered explicitly from {@link ruiseki.okmodular.MachineryClient#registerEventHooks()}.
 */
@SideOnly(Side.CLIENT)
public class StructureWandRenderer {

    // Translucent cyan (RGB: 0, 200, 255)
    private static final float COLOR_R = 0.0f;
    private static final float COLOR_G = 0.78f;
    private static final float COLOR_B = 1.0f;
    private static final float COLOR_A_EDGE = 0.8f;
    private static final float COLOR_A_FACE = 0.2f;

    // Orange, used for the not-yet-confirmed pos1 -> cursor box
    private static final float PREVIEW_R = 1.0f;
    private static final float PREVIEW_G = 0.65f;
    private static final float PREVIEW_B = 0.0f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;

        if (player == null) return;

        // Ensure the player is holding the wand
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemStructureWand)) {
            return;
        }

        // Retrieve stored positions
        ChunkCoordinates pos1 = ItemStructureWand.getPos1FromStack(heldItem);
        ChunkCoordinates pos2 = ItemStructureWand.getPos2FromStack(heldItem);

        // Dimension check
        int dim = ItemStructureWand.getDimensionFromStack(heldItem);
        if (dim != player.worldObj.provider.dimensionId) {
            return;
        }

        // Only pos1 set: preview towards whatever the player is looking at, or just mark pos1
        if (pos1 != null && pos2 == null) {
            ChunkCoordinates lookTarget = getLookTarget(player);

            if (lookTarget != null) {
                renderBox(pos1, lookTarget, event.partialTicks, player, PREVIEW_R, PREVIEW_G, PREVIEW_B, 0.15f, 0.7f);
            } else {
                renderPoint(pos1, event.partialTicks, player, COLOR_R, COLOR_G, COLOR_B);
            }
        }

        // Both positions set: draw the confirmed selection
        if (pos1 != null && pos2 != null) {
            renderBox(pos1, pos2, event.partialTicks, player, COLOR_R, COLOR_G, COLOR_B, COLOR_A_FACE, COLOR_A_EDGE);
        }
    }

    /**
     * Get the block position the player is looking at.
     */
    private static ChunkCoordinates getLookTarget(EntityPlayer player) {
        double reachDistance = StructureConstants.WAND_PREVIEW_REACH;
        Vec3 startVec = player.getPosition(1.0F);
        Vec3 lookVec = player.getLookVec();
        Vec3 endVec = startVec
            .addVector(lookVec.xCoord * reachDistance, lookVec.yCoord * reachDistance, lookVec.zCoord * reachDistance);

        MovingObjectPosition mop = player.worldObj.rayTraceBlocks(startVec, endVec);

        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            return new ChunkCoordinates(mop.blockX, mop.blockY, mop.blockZ);
        }

        return null;
    }

    private static void renderPoint(ChunkCoordinates pos, float partialTicks, EntityPlayer player, float r, float g,
        float b) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        double minX = pos.posX - px;
        double minY = pos.posY - py;
        double minZ = pos.posZ - pz;
        double maxX = pos.posX + 1 - px;
        double maxY = pos.posY + 1 - py;
        double maxZ = pos.posZ + 1 - pz;

        OverlayShapes.beginOverlay(false);
        GL11.glLineWidth(3.0f);
        GL11.glColor4f(r, g, b, COLOR_A_EDGE);
        OverlayShapes.drawBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);
        OverlayShapes.endOverlay();
    }

    private static void renderBox(ChunkCoordinates pos1, ChunkCoordinates pos2, float partialTicks, EntityPlayer player,
        float r, float g, float b, float faceAlpha, float edgeAlpha) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Convert block coordinates to render coordinates (+1 to include block edges)
        double minX = Math.min(pos1.posX, pos2.posX) - px;
        double minY = Math.min(pos1.posY, pos2.posY) - py;
        double minZ = Math.min(pos1.posZ, pos2.posZ) - pz;
        double maxX = Math.max(pos1.posX, pos2.posX) + 1 - px;
        double maxY = Math.max(pos1.posY, pos2.posY) + 1 - py;
        double maxZ = Math.max(pos1.posZ, pos2.posZ) + 1 - pz;

        OverlayShapes.beginOverlay(false);

        GL11.glColor4f(r, g, b, faceAlpha);
        OverlayShapes.drawBoxFaces(minX, minY, minZ, maxX, maxY, maxZ);

        GL11.glLineWidth(2.0f);
        GL11.glColor4f(r, g, b, edgeAlpha);
        OverlayShapes.drawBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);

        OverlayShapes.endOverlay();
    }

}
