package ru.iis.cover.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.cover.CoverHost;
import ru.iis.cover.CoverKind;

/** Рисует покрытия поверх граней блока (GT: текстура покрытия заменяет грань) и значки выходов машины (D101). */
public class CoverRenderer<T extends BlockEntity & CoverHost> implements BlockEntityRenderer<T, CoverRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final CoverKind[] kinds = new CoverKind[6];
        final int[] tiers = new int[6];
        final int[] light = new int[6];
        /** Грани выхода предметов и жидкостей машины (-1 — нет). */
        int itemOut = -1, fluidOut = -1;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            state.kinds[i] = be.covers().get(d);
            state.tiers[i] = be.covers().tier(d);
            state.light[i] = be.getLevel() != null ? LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos().relative(d)) : 15728880;
        }
        state.itemOut = state.fluidOut = -1;
        if (be instanceof ru.iis.lv.block.ElectricMachineBlockEntity m) {
            state.itemOut = m.outputSide().get3DDataValue();
            // GT_MetaTileEntity_BasicMachine.getTexture: OVERLAY_FLUID_OUT, если выход жидкостей не совпадает с выходом предметов
            if (m.fluidOutputSide() != m.outputSide()) state.fluidOut = m.fluidOutputSide().get3DDataValue();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            CoverKind k = state.kinds[i];
            String path = k != null ? "cover/" + ru.iis.cover.Covers.textureId(k, state.tiers[i]) : i == state.itemOut ? "overlay/pipe_out" : i == state.fluidOut ? "overlay/fluid_out" : null;
            if (path == null) continue;
            int light = state.light[i];
            Identifier tex = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/block/" + path + ".png");
            out.submitCustomGeometry(pose, RenderTypes.entityCutout(tex), (p, buf) -> face(p, buf, d, light));
        }
    }

    /** Квадрат чуть снаружи грани, развёрнутый наружу. */
    private static void face(PoseStack.Pose p, VertexConsumer buf, Direction d, int light) {
        float e = 0.002f;
        float[][] v = switch (d) {
            case DOWN -> new float[][]{{0, -e, 0, 0, 0}, {1, -e, 0, 1, 0}, {1, -e, 1, 1, 1}, {0, -e, 1, 0, 1}};
            case UP -> new float[][]{{0, 1 + e, 1, 0, 1}, {1, 1 + e, 1, 1, 1}, {1, 1 + e, 0, 1, 0}, {0, 1 + e, 0, 0, 0}};
            case NORTH -> new float[][]{{1, 1, -e, 0, 0}, {1, 0, -e, 0, 1}, {0, 0, -e, 1, 1}, {0, 1, -e, 1, 0}};
            case SOUTH -> new float[][]{{0, 1, 1 + e, 0, 0}, {0, 0, 1 + e, 0, 1}, {1, 0, 1 + e, 1, 1}, {1, 1, 1 + e, 1, 0}};
            case WEST -> new float[][]{{-e, 1, 0, 0, 0}, {-e, 0, 0, 0, 1}, {-e, 0, 1, 1, 1}, {-e, 1, 1, 1, 0}};
            case EAST -> new float[][]{{1 + e, 1, 1, 0, 0}, {1 + e, 0, 1, 0, 1}, {1 + e, 0, 0, 1, 1}, {1 + e, 1, 0, 1, 0}};
        };
        for (float[] q : v) {
            buf.addVertex(p, q[0], q[1], q[2]).setColor(0xFFFFFFFF).setUv(q[3], q[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(p, d.getStepX(), d.getStepY(), d.getStepZ());
        }
    }
}
