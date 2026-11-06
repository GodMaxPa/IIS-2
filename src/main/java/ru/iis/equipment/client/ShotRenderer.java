package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import ru.iis.equipment.ShotEntity;

/** Снаряд в полёте. */
public class ShotRenderer extends EntityRenderer<ShotEntity, ShotRenderer.State> {
    public static final class State extends EntityRenderState {
        public MeshModel model;
        public float yaw, pitch, spin;
    }

    private static final MeshModel.Pose REST = new MeshModel.Pose();

    public ShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShotEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        String mesh = e.tank() ? "proj_shell" : switch (e.weapon().kind) {
            case GRENADE -> "proj_grenade";
            case ROCKET -> "proj_rocket";
            default -> e.weapon().damage > 100 ? "proj_plasma_big" : "proj_plasma";
        };
        s.model = MeshModel.get(mesh);
        s.yaw = e.getYRot(partial);
        s.pitch = e.getXRot(partial);
        s.spin = (e.tickCount + partial) * 40F;
    }

    @Override
    public void submit(State s, PoseStack stack, SubmitNodeCollector out, CameraRenderState camera) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(s.yaw));
        stack.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(s.spin));
        stack.scale(1 / 16F, 1 / 16F, 1 / 16F);
        s.model.submit(stack, out, s.lightCoords, OverlayTexture.NO_OVERLAY, REST);
        stack.popPose();
        super.submit(s, stack, out, camera);
    }
}
