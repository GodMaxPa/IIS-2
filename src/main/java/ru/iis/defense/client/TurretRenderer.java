package ru.iis.defense.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.defense.TurretBlockEntity;
import ru.iis.equipment.client.MeshModel;

/** Голова турели (D140): меш turret_&lt;вид&gt;, башня по курсу, ствол по тангажу; поворот плавно догоняет сервер. */
public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity, TurretRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        String mesh = "turret_disposable";
        final MeshModel.Pose pose = new MeshModel.Pose();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TurretBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        be.shownYaw += Mth.wrapDegrees(be.yaw - be.shownYaw) * 0.25F;
        be.shownPitch += (be.pitch - be.shownPitch) * 0.25F;
        state.mesh = "turret_" + be.kind().id;
        state.pose.turretYaw = -be.shownYaw;
        state.pose.barrelPitch = be.shownPitch;
    }

    @Override
    public void submit(State state, PoseStack stack, SubmitNodeCollector out, CameraRenderState camera) {
        stack.pushPose();
        stack.translate(0.5F, 0.55F, 0.5F);
        stack.scale(1 / 16F, 1 / 16F, 1 / 16F);
        MeshModel.get(state.mesh).submit(stack, out, state.lightCoords, OverlayTexture.NO_OVERLAY, state.pose);
        stack.popPose();
    }
}
