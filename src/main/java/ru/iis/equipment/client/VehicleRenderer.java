package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import ru.iis.equipment.vehicle.Driveable;

/** Машина: корпус по курсу, тангажу и крену, колёса и винты по пути и оборотам. */
public class VehicleRenderer extends EntityRenderer<Driveable, VehicleRenderer.State> {
    public static final class State extends EntityRenderState {
        public MeshModel model;
        public float yaw, pitch, roll, hurt;
        public final MeshModel.Pose pose = new MeshModel.Pose();
    }

    public VehicleRenderer(EntityRendererProvider.Context context, float shadow) {
        super(context);
        shadowRadius = shadow;
    }

    /** Крылья и винты шире корпуса: по коробке сущности машина исчезала бы раньше времени. */
    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(Driveable v) {
        return super.getBoundingBoxForCulling(v).inflate(v.kind.flies() ? 6 : 3);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Driveable v, State s, float partial) {
        super.extractRenderState(v, s, partial);
        s.model = MeshModel.get(v.kind.id);
        s.yaw = v.getYRot(partial);
        s.pitch = v.getXRot(partial);
        s.roll = Mth.lerp(partial, v.rollO, v.roll);
        s.hurt = v.getHurtTime() > 0 ? (v.getHurtTime() - partial) : 0;
        s.pose.distance = Mth.lerp(partial, v.distanceO, v.distance);
        s.pose.steer = -Mth.lerp(partial, v.steerAngleO, v.steerAngle);
        s.pose.spin = Mth.lerp(partial, v.propAngleO, v.propAngle);
        // башня смотрит туда же, куда водитель
        Entity driver = v.getControllingPassenger();
        if (driver != null) {
            s.pose.turretYaw = -Mth.wrapDegrees(driver.getYRot(partial) - s.yaw);
            s.pose.barrelPitch = Mth.clamp(driver.getXRot(partial), -22F, 8F);
        } else {
            s.pose.turretYaw = 0;
            s.pose.barrelPitch = 0;
        }
    }

    @Override
    public void submit(State s, PoseStack stack, SubmitNodeCollector out, CameraRenderState camera) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(-s.yaw));
        stack.mulPose(Axis.XP.rotationDegrees(s.pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(s.roll));
        if (s.hurt > 0) stack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(s.hurt) * s.hurt * 0.2F));
        stack.scale(1 / 16F, 1 / 16F, 1 / 16F);
        s.model.submit(stack, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.pose);
        stack.popPose();
        super.submit(s, stack, out, camera);
    }
}
