package ruiseki.okmodular.client.render;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IMachineController;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.common.item.ItemWrench;
import ruiseki.okmodular.common.tile.TEMachineController;
import ruiseki.okmodular.core.tileentity.ISidedIO;

/**
 * Draws the wrench's overlay: linked external ports, and the controller they belong to.
 * <p>
 * Registered by hand from {@code MachineryClient.registerEventHooks}, the same way
 * {@link StructureWandRenderer} is. It used to carry GTNHLib's
 * {@code @EventBusSubscriber} instead, which never fired: <b>nothing in the mod
 * references this class</b>, so it was never loaded, and a class that is never loaded is
 * never wired up. The overlay simply did not exist, with no error to say so.
 */
public class WrenchOverlayRenderer {

    /** The controller this wrench is already linked to. */
    private static final float LINKED_R = 0.2f, LINKED_G = 1.0f, LINKED_B = 0.45f;

    /** A controller the wrench could link to, being looked at right now. */
    private static final float TARGET_R = 1.0f, TARGET_G = 0.75f, TARGET_B = 0.1f;

    private static final float BOX_FACE_ALPHA = 0.16f;
    private static final float BOX_EDGE_ALPHA = 0.9f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;

        if (player == null) return;

        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemWrench)) return;

        // The linked controller and its ports draw wherever the player is looking, so
        // this comes before the cursor is consulted at all - the whole point of the
        // link overlay is to find a machine that is not in front of you.
        ChunkCoordinates linked = ItemWrench.getLinkedController(held, player.worldObj);
        if (linked != null) {
            TileEntity cte = player.worldObj.getTileEntity(linked.posX, linked.posY, linked.posZ);
            if (cte instanceof TEMachineController controller) {
                drawBlockHighlight(
                    linked.posX,
                    linked.posY,
                    linked.posZ,
                    event.partialTicks,
                    player,
                    LINKED_R,
                    LINKED_G,
                    LINKED_B);
                drawLinkedPorts(controller, event.partialTicks, player);
            }
        }

        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        int x = mc.objectMouseOver.blockX;
        int y = mc.objectMouseOver.blockY;
        int z = mc.objectMouseOver.blockZ;

        TileEntity te = player.worldObj.getTileEntity(x, y, z);

        // A controller is a wrench target too, but not a per-face one: the wrench links
        // to it rather than toggling its sides, so the nine-section grid below would
        // promise an interaction that does not exist. It gets the whole block outlined
        // instead. This used to be an outright exclusion, which is why holding a wrench
        // showed nothing at all on the one block the wrench needs you to find first.
        if (te instanceof IMachineController) {
            boolean isLinkedOne = linked != null && linked.posX == x && linked.posY == y && linked.posZ == z;
            if (!isLinkedOne) {
                drawBlockHighlight(x, y, z, event.partialTicks, player, TARGET_R, TARGET_G, TARGET_B);
            }
            return;
        }

        if (!(te instanceof ISidedIO)) return;

        ForgeDirection side = ForgeDirection.getOrientation(mc.objectMouseOver.sideHit);

        // Calculate hit vector relative to the block center
        double hitX = mc.objectMouseOver.hitVec.xCoord - x;
        double hitY = mc.objectMouseOver.hitVec.yCoord - y;
        double hitZ = mc.objectMouseOver.hitVec.zCoord - z;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Always draw static outlines
        drawFaceOutline(x, y, z, side);
        drawGridLines(x, y, z, side);

        // Draw dynamic highlight based on hover position
        drawHighlight(x, y, z, side, (float) hitX, (float) hitY, (float) hitZ);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glPopMatrix();
    }

    /**
     * Outlines a whole block, drawn through terrain.
     *
     * <p>
     * The depth test is off on purpose. The linked controller can be behind the wall
     * the player is standing at when they register a port on the far side of a machine,
     * and an outline that only shows when the block is already visible would be exactly
     * as useful as no outline at that moment.
     */
    private static void drawBlockHighlight(int x, int y, int z, float partialTicks, EntityPlayer player, float r,
        float g, float b) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Outset so the outline sits clear of the block's own faces rather than fighting
        // them, the same reason the face outlines above carry one.
        double o = 0.01;
        double minX = x - o - px;
        double minY = y - o - py;
        double minZ = z - o - pz;
        double maxX = x + 1 + o - px;
        double maxY = y + 1 + o - py;
        double maxZ = z + 1 + o - pz;

        OverlayShapes.beginOverlay(false);

        GL11.glColor4f(r, g, b, BOX_FACE_ALPHA);
        OverlayShapes.drawBoxFaces(minX, minY, minZ, maxX, maxY, maxZ);

        GL11.glLineWidth(3.0f);
        GL11.glColor4f(r, g, b, BOX_EDGE_ALPHA);
        OverlayShapes.drawBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);

        OverlayShapes.endOverlay();
    }

    private static void drawFaceOutline(int x, int y, int z, ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN) return;
        Tessellator t = Tessellator.instance;
        GL11.glLineWidth(2.5f);
        GL11.glColor4f(1.0f, 1.0f, 1f, 1f);

        t.startDrawing(GL11.GL_LINE_LOOP);
        float o = 0.005f;

        addVertex(t, x, y, z, side, 0, 0, o);
        addVertex(t, x, y, z, side, 1, 0, o);
        addVertex(t, x, y, z, side, 1, 1, o);
        addVertex(t, x, y, z, side, 0, 1, o);

        t.draw();
    }

    private static void drawGridLines(int x, int y, int z, ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN) return;
        Tessellator t = Tessellator.instance;
        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        float min = 0.20f;
        float max = 0.80f;
        float o = 0.005f;

        t.startDrawing(GL11.GL_LINES);

        // Horizontal lines (v is constant)
        addVertex(t, x, y, z, side, 0, min, o);
        addVertex(t, x, y, z, side, 1, min, o);
        addVertex(t, x, y, z, side, 0, max, o);
        addVertex(t, x, y, z, side, 1, max, o);

        // Vertical lines (u is constant)
        addVertex(t, x, y, z, side, min, 0, o);
        addVertex(t, x, y, z, side, min, 1, o);
        addVertex(t, x, y, z, side, max, 0, o);
        addVertex(t, x, y, z, side, max, 1, o);

        t.draw();
    }

    private static void drawHighlight(int x, int y, int z, ForgeDirection side, float hitX, float hitY, float hitZ) {
        if (side == ForgeDirection.UNKNOWN) return;
        final float BORDER = 0.20f;

        // Determine UV coordinates based on side
        float uHit = 0, vHit = 0;
        switch (side) {
            case UP:
                uHit = hitX;
                vHit = hitZ;
                break;
            case DOWN:
                uHit = hitX;
                vHit = hitZ;
                break; // DOWN usually mirrors depending on rotation, but here we treat flat
            case NORTH:
                uHit = hitX;
                vHit = hitY;
                break;
            case SOUTH:
                uHit = hitX;
                vHit = hitY;
                break;
            case WEST:
                uHit = hitZ;
                vHit = hitY;
                break;
            case EAST:
                uHit = hitZ;
                vHit = hitY;
                break;
            default:
                return;
        }

        int hSection = getSection(uHit, BORDER);
        int vSection = getSection(vHit, BORDER);

        // Highlight color
        boolean isCenter = hSection == 1 && vSection == 1;
        float r = isCenter ? 0.0f : 1.0f;
        float g = 1.0f;
        float b = 0.0f;
        float alpha = 0.3f;

        Tessellator t = Tessellator.instance;
        GL11.glColor4f(r, g, b, alpha);
        t.startDrawing(GL11.GL_QUADS);

        // Determine drawing bounds based on section
        float hMin = hSection == 0 ? 0f : (hSection == 1 ? BORDER : 1 - BORDER);
        float hMax = hSection == 0 ? BORDER : (hSection == 1 ? 1 - BORDER : 1f);
        float vMin = vSection == 0 ? 0f : (vSection == 1 ? BORDER : 1 - BORDER);
        float vMax = vSection == 0 ? BORDER : (vSection == 1 ? 1 - BORDER : 1f);

        float o = 0.005f;

        addVertex(t, x, y, z, side, hMin, vMin, o);
        addVertex(t, x, y, z, side, hMax, vMin, o);
        addVertex(t, x, y, z, side, hMax, vMax, o);
        addVertex(t, x, y, z, side, hMin, vMax, o);

        t.draw();
    }

    private static int getSection(float hit, float border) {
        return hit < border ? 0 : (hit > 1 - border ? 2 : 1);
    }

    private static void addVertex(Tessellator t, int x, int y, int z, ForgeDirection side, double u, double v,
        double o) {
        switch (side) {
            case UP -> t.addVertex(x + u, y + 1 + o, z + v);
            case DOWN -> t.addVertex(x + u, y - o, z + v);
            case NORTH -> t.addVertex(x + u, y + v, z - o);
            case SOUTH -> t.addVertex(x + u, y + v, z + 1 + o);
            case WEST -> t.addVertex(x - o, y + v, z + u);
            case EAST -> t.addVertex(x + 1 + o, y + v, z + u);
            default -> {
                // Do nothing
            }
        }
    }

    private static void drawLinkedPorts(TEMachineController controller, float partialTicks, EntityPlayer player) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> configs = controller.getExternalPortConfigs();
        if (configs == null || configs.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        double cX = controller.xCoord + 0.5;
        double cY = controller.yCoord + 0.5;
        double cZ = controller.zCoord + 0.5;

        Tessellator t = Tessellator.instance;
        GL11.glLineWidth(3.0f);

        for (Map.Entry<ChunkCoordinates, Map<IPortType.Type, EnumIO>> entry : configs.entrySet()) {
            ChunkCoordinates port = entry.getKey();
            double pX = port.posX + 0.5;
            double pY = port.posY + 0.5;
            double pZ = port.posZ + 0.5;

            EnumIO firstIo = EnumIO.NONE;
            for (EnumIO io : entry.getValue()
                .values()) {
                if (io != EnumIO.NONE) {
                    firstIo = io;
                    break;
                }
            }

            switch (firstIo) {
                case INPUT:
                    GL11.glColor4f(0.2f, 0.6f, 1.0f, 0.8f);
                    break;
                case OUTPUT:
                    GL11.glColor4f(1.0f, 0.6f, 0.2f, 0.8f);
                    break;
                case BOTH:
                    GL11.glColor4f(0.8f, 0.2f, 0.8f, 0.8f);
                    break;
                default:
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.5f);
                    break;
            }

            t.startDrawing(GL11.GL_LINES);
            t.addVertex(cX, cY, cZ);
            t.addVertex(pX, pY, pZ);
            t.draw();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        RenderManager rm = RenderManager.instance;
        for (Map.Entry<ChunkCoordinates, Map<IPortType.Type, EnumIO>> entry : configs.entrySet()) {
            ChunkCoordinates port = entry.getKey();

            int lineOffset = 0;
            IStructureEntry entryProps = controller.getStructureAgent()
                .getCustomProperties();
            Map<Character, EnumIO> fixedPorts = entryProps != null ? entryProps.getFixedExternalPorts() : null;

            for (Map.Entry<IPortType.Type, EnumIO> typeEntry : entry.getValue()
                .entrySet()) {

                boolean fixed = false;
                if (fixedPorts != null) {
                    // Find which symbol corresponds to this ChunkCoordinates
                    for (Map.Entry<Character, List<ChunkCoordinates>> symEntry : controller.getSymbolPositionsMap()
                        .entrySet()) {
                        if (symEntry.getValue()
                            .contains(port)) {
                            fixed = fixedPorts.containsKey(symEntry.getKey());
                            break;
                        }
                    }
                }

                // The kind and the direction both have translations already - the same
                // ones the wrench's own name and every port GUI use - so the label read
                // "[ ITEM : INPUT ]" for no reason other than never asking for them.
                String kind = LangHelpers.localize(
                    "gui.port_type." + typeEntry.getKey()
                        .name());
                String io = LangHelpers.localize(
                    typeEntry.getValue()
                        .getName());

                // Written as two calls rather than one with a ternary key, so that both
                // keys sit as literals where LangKeyCoverageTest can see them.
                String text = fixed ? LangHelpers.localize("overlay.okmodular.wrench_port_fixed", kind, io)
                    : LangHelpers.localize("overlay.okmodular.wrench_port", kind, io);

                double d0 = port.posX + 0.5 - px;
                // Move downward natively (y moves pos when scaled, note normal rendering)
                double d1 = port.posY + 0.75 - Math.min(0.7, (lineOffset * 0.3)) - py;
                double d2 = port.posZ + 0.5 - pz;

                GL11.glPushMatrix();
                GL11.glTranslated(d0, d1, d2);
                GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
                GL11.glScalef(-0.02666667F, -0.02666667F, 0.02666667F);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDepthMask(false);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                FontRenderer font = Minecraft.getMinecraft().fontRenderer;
                int width = font.getStringWidth(text) / 2;

                GL11.glDisable(GL11.GL_TEXTURE_2D);
                t.startDrawingQuads();
                t.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.4F);
                t.addVertex(-width - 1, -1, 0.0D);
                t.addVertex(-width - 1, 8, 0.0D);
                t.addVertex(width + 1, 8, 0.0D);
                t.addVertex(width + 1, -1, 0.0D);
                t.draw();
                GL11.glEnable(GL11.GL_TEXTURE_2D);

                int color = 0xFFFFFF;
                switch (typeEntry.getValue()) {
                    case INPUT:
                        color = 0x3399FF;
                        break;
                    case OUTPUT:
                        color = 0xFF9933;
                        break;
                    case BOTH:
                        color = 0xCC33CC;
                        break;
                    default:
                        break;
                }
                font.drawString(text, -width, 0, color);

                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glPopMatrix();

                lineOffset++;
            }
        }
    }

}
