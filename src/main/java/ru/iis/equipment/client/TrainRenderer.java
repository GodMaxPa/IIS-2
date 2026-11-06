package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.cart.Locomotive;
import ru.iis.rail.cart.TunnelBore;

/** Подвижной состав (D128): своя модель вместо вагонетки с блоком. Положение на пути считает ванильный код, перед локомотива смотрит по его курсу. */
public class TrainRenderer<T extends AbstractMinecart> extends AbstractMinecartRenderer<T, TrainRenderer.State> {
    public static final class State extends MinecartRenderState {
        public final MeshModel.Pose pose = new MeshModel.Pose();
        public float headX, headZ;
        public boolean directional;
    }

    private final MeshModel model;

    public TrainRenderer(EntityRendererProvider.Context context, String mesh) {
        super(context, ModelLayers.MINECART);
        model = MeshModel.get(mesh);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T cart, State s, float partial) {
        super.extractRenderState(cart, s, partial);
        // путь вдоль оси: сумма координат растёт на обоих направлениях одинаково
        s.pose.distance = (float) (s.x + s.z);
        s.pose.spin = (cart.tickCount + partial) * 0.35F;
        if (cart instanceof Locomotive loco) {
            s.directional = true;
            s.headX = loco.syncedHeadingX();
            s.headZ = loco.syncedHeadingZ();
            if (!loco.isRunning() && !loco.hasFuel()) s.pose.spin = 0;
        } else if (cart instanceof TunnelBore bore) {
            s.directional = true;
            s.headX = bore.facing().getStepX();
            s.headZ = bore.facing().getStepZ();
            s.pose.spin = bore.isDrilling() ? s.pose.spin * 3 : 0;
        } else {
            s.directional = false;
        }
    }

    @Override
    public void submit(State s, PoseStack stack, SubmitNodeCollector out, CameraRenderState camera) {
        stack.pushPose();
        float rotation = s.yRot, xRot = s.xRot, modelYaw;
        if (s.isNewRender) {
            stack.mulPose(Axis.YP.rotationDegrees(rotation));
            stack.mulPose(Axis.ZP.rotationDegrees(-xRot));
            modelYaw = rotation + 90F;
        } else {
            if (s.posOnRail != null && s.frontPos != null && s.backPos != null) {
                stack.translate(s.posOnRail.x - s.x, (s.frontPos.y + s.backPos.y) / 2.0 - s.y, s.posOnRail.z - s.z);
                Vec3 d = s.backPos.subtract(s.frontPos);
                if (d.length() != 0.0) {
                    d = d.normalize();
                    rotation = (float) (Math.atan2(d.z, d.x) * Mth.RAD_TO_DEG);
                    xRot = (float) (Math.atan(d.y) * 73.0);
                }
            }
            stack.mulPose(Axis.YP.rotationDegrees(180F - rotation));
            stack.mulPose(Axis.ZP.rotationDegrees(-xRot));
            modelYaw = 270F - rotation;
        }
        if (s.hurtTime > 0) stack.mulPose(Axis.XP.rotationDegrees(Mth.sin(s.hurtTime) * s.hurtTime * s.damageTime / 10F * s.hurtDir));
        stack.mulPose(Axis.YP.rotationDegrees(90F));
        if (s.directional) {
            // перед модели совпадёт с курсом: иначе разворот на 180
            double sin = Math.sin(Math.toRadians(modelYaw)), cos = Math.cos(Math.toRadians(modelYaw));
            if (sin * s.headX + cos * s.headZ < 0) stack.mulPose(Axis.YP.rotationDegrees(180F));
        }
        stack.scale(1 / 16F, 1 / 16F, 1 / 16F);
        model.submit(stack, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.pose);
        stack.popPose();
    }
}
