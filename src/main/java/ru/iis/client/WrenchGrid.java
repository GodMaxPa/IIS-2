package ru.iis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.joml.Vector3f;
import ru.iis.lv.block.TransformerBlock;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.machine.block.Wrench;
import ru.iis.multi.block.HatchBlock;

/**
 * GT_Client.drawGrid: с ключом в руке на грани машины/трубы под прицелом рисуется сетка 3×3
 * (линии на 0.25 и 0.75) — по ней видно, куда повернётся лицевая сторона.
 */
public final class WrenchGrid {
    private WrenchGrid() {}

    public static void onExtract(ExtractBlockOutlineRenderStateEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !Wrench.is(mc.player.getMainHandItem())) return;
        Block b = e.getBlockState().getBlock();
        if (!(b instanceof MachineBlock || b instanceof TransformerBlock || b instanceof HatchBlock || b instanceof PipeBlock)) return;
        BlockPos pos = e.getBlockPos().immutable();
        Direction face = e.getHitResult().getDirection();
        e.addCustomRenderer((state, buffers, pose, translucent, level) -> {
            if (translucent) return false;
            var cam = level.cameraRenderState.pos;
            float width = mc.gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
            VertexConsumer vc = buffers.getBuffer(RenderTypes.lines());
            draw(pose, vc, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, face, ARGB.black(128), width);
            buffers.endLastBatch();
            return false;
        });
    }

    private static void draw(PoseStack ps, VertexConsumer vc, double ox, double oy, double oz, Direction face, int color, float width) {
        double e = 0.002;
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 + e : -e;
        for (double t : new double[]{0.25, 0.75}) {
            line(ps, vc, ox, oy, oz, face.getAxis(), plane, t, 0, t, 1, color, width);
            line(ps, vc, ox, oy, oz, face.getAxis(), plane, 0, t, 1, t, color, width);
        }
    }

    /** Отрезок на плоскости грани: (a1,b1)-(a2,b2) в координатах грани. */
    private static void line(PoseStack ps, VertexConsumer vc, double ox, double oy, double oz, Direction.Axis axis, double plane,
                             double a1, double b1, double a2, double b2, int color, float width) {
        double[] p1 = point(axis, plane, a1, b1), p2 = point(axis, plane, a2, b2);
        Vector3f n = new Vector3f((float) (p2[0] - p1[0]), (float) (p2[1] - p1[1]), (float) (p2[2] - p1[2])).normalize();
        var last = ps.last();
        vc.addVertex(last, (float) (ox + p1[0]), (float) (oy + p1[1]), (float) (oz + p1[2])).setColor(color).setNormal(last, n).setLineWidth(width);
        vc.addVertex(last, (float) (ox + p2[0]), (float) (oy + p2[1]), (float) (oz + p2[2])).setColor(color).setNormal(last, n).setLineWidth(width);
    }

    private static double[] point(Direction.Axis axis, double plane, double a, double b) {
        return switch (axis) {
            case Y -> new double[]{a, plane, b};
            case Z -> new double[]{a, b, plane};
            default -> new double[]{plane, b, a};
        };
    }
}
