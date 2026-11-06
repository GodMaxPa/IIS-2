package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.iis.IIS;

/** Объёмные части брони на игроке (D128): треуголка, разгрузка, наплечники, ранец с ядром. */
public class ArmorExtrasLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final MeshModel.Pose REST = new MeshModel.Pose();

    public ArmorExtrasLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static String set(ItemStack stack) {
        if (stack.isEmpty()) return "";
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(IIS.MODID)) return "";
        String p = id.getPath();
        for (String s : new String[]{"musketeer", "ballistic", "assault", "power_armor", "plasma_armor"}) if (p.startsWith(s + "_")) return s;
        return "";
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        String head = set(state.headEquipment), chest = set(state.chestEquipment);
        PlayerModel m = getParentModel();
        switch (head) {
            case "musketeer" -> part(pose, out, light, m, m.head, "a_tricorn", false);
            case "power_armor" -> part(pose, out, light, m, m.head, "a_power_head", false);
            case "plasma_armor" -> part(pose, out, light, m, m.head, "a_plasma_head", false);
            default -> {}
        }
        switch (chest) {
            case "musketeer" -> part(pose, out, light, m, m.body, "a_musketeer_body", false);
            case "ballistic" -> part(pose, out, light, m, m.body, "a_ballistic_body", false);
            case "assault" -> {
                part(pose, out, light, m, m.body, "a_assault_body", false);
                shoulders(pose, out, light, m, "a_assault_shoulder");
            }
            case "power_armor" -> {
                part(pose, out, light, m, m.body, "a_power_back", false);
                shoulders(pose, out, light, m, "a_power_shoulder");
            }
            case "plasma_armor" -> {
                part(pose, out, light, m, m.body, "a_plasma_back", false);
                shoulders(pose, out, light, m, "a_plasma_shoulder");
            }
            default -> {}
        }
    }

    private static void shoulders(PoseStack pose, SubmitNodeCollector out, int light, PlayerModel m, String mesh) {
        // на теле, а не на руке: иначе наплечник уезжал бы вперёд вместе с поднятой рукой
        side(pose, out, light, m, mesh, -6F);
        side(pose, out, light, m, mesh, 6F);
    }

    private static void side(PoseStack pose, SubmitNodeCollector out, int light, PlayerModel m, String mesh, float x) {
        pose.pushPose();
        m.root().translateAndRotate(pose);
        m.body.translateAndRotate(pose);
        pose.scale(-1 / 16F, -1 / 16F, 1 / 16F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
        pose.translate(x, -1.4F, 0F);
        MeshModel.get(mesh).submit(pose, out, light, OverlayTexture.NO_OVERLAY, REST);
        pose.popPose();
    }

    /** Модель на части тела: y вверх, z вперёд, как в tools/gen/misc_models.py. */
    private static void part(PoseStack pose, SubmitNodeCollector out, int light, PlayerModel m, ModelPart part, String mesh, boolean arm) {
        pose.pushPose();
        m.root().translateAndRotate(pose);
        part.translateAndRotate(pose);
        // части модели игрока смотрят по -Z, вверх — это -Y; масштаб пикселя 1/16
        pose.scale(-1 / 16F, -1 / 16F, 1 / 16F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
        MeshModel.get(mesh).submit(pose, out, light, OverlayTexture.NO_OVERLAY, REST);
        pose.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addToPlayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : PlayerModelType.values()) {
            var renderer = event.getPlayerRenderer(type);
            if (renderer != null) renderer.addLayer(new ArmorExtrasLayer((RenderLayerParent) renderer));
        }
    }
}
