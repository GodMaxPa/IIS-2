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
        public float yaw, pitch, roll, hurt, rackY, rackZ, rackScale;
        public boolean kit;
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
        s.pitch += Mth.lerp(partial, v.terrainPitchO, v.terrainPitch);
        s.kit = v.hasKit();
        if (s.kit) {
            // где на этой машине лежит груз: крыша, кузов или палуба
            float[] r = rack(v.kind);
            s.rackY = r[0];
            s.rackZ = r[1];
            s.rackScale = r[2];
        }
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

    /** Высота, сдвиг вдоль корпуса и масштаб багажника на крыше. */
    private static float[] rack(ru.iis.equipment.vehicle.VehicleKind k) {
        return switch (k) {
            case STEAM_CAR -> new float[]{35, 6, 0.8F};
            case QUAD -> new float[]{16, -18, 0.55F};
            case MOTORBIKE -> new float[]{16, -19, 0.3F};
            case CAR -> new float[]{28.4F, -8, 1.0F};
            case ATV -> new float[]{46.5F, 10, 1.0F};
            case TRUCK -> new float[]{46, -16, 1.6F};
            case BOAT -> new float[]{16, -24, 0.7F};
            case PLANE -> new float[]{30, -30, 0.5F};
            case HELICOPTER -> new float[]{37, -12, 0.6F};
            case TANK -> new float[]{23, -32, 1.0F};
            case JET -> new float[]{26, -38, 0.5F};
        };
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
        if (s.kit) {
            stack.pushPose();
            stack.translate(0F, s.rackY, s.rackZ);
            stack.scale(s.rackScale, s.rackScale, s.rackScale);
            MeshModel.get("a_cargo_rack").submit(stack, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.pose);
            stack.popPose();
        }
        stack.popPose();
        super.submit(s, stack, out, camera);
    }
}
