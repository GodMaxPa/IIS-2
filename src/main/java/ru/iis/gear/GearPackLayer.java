package ru.iis.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.iis.IIS;

/** Ранцы на спине игрока объёмные: ящик с батареями или два баллона с соплами и пламенем. Текстуры — tools/gen/gear_art.py. */
public class GearPackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ModelPart pack, jet, flames;

    public GearPackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
        MeshDefinition box = new MeshDefinition();
        box.getRoot().addOrReplaceChild("pack", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 1F, 2.5F, 8F, 10F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
        pack = LayerDefinition.create(box, 64, 32).bakeRoot();

        MeshDefinition tanks = new MeshDefinition();
        tanks.getRoot().addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).addBox(0.5F, 0F, 2.5F, 3F, 10F, 3F).texOffs(0, 16).addBox(1F, 10F, 3F, 2F, 3F, 2F), PartPose.ZERO);
        tanks.getRoot().addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0F, 2.5F, 3F, 10F, 3F).texOffs(0, 16).addBox(-3F, 10F, 3F, 2F, 3F, 2F), PartPose.ZERO);
        jet = LayerDefinition.create(tanks, 64, 32).bakeRoot();

        MeshDefinition fire = new MeshDefinition();
        fire.getRoot().addOrReplaceChild("left", CubeListBuilder.create().texOffs(16, 16).addBox(1F, 13F, 3F, 2F, 4F, 2F), PartPose.ZERO);
        fire.getRoot().addOrReplaceChild("right", CubeListBuilder.create().texOffs(16, 16).addBox(-3F, 13F, 3F, 2F, 4F, 2F), PartPose.ZERO);
        flames = LayerDefinition.create(fire, 64, 32).bakeRoot();
    }

    private static Identifier texture(Item item) {
        String id = item == GearContent.ADV_BATPACK.get() ? "adv_batpack" : item == GearContent.ENERGY_PACK.get() ? "energy_pack" : item == GearContent.LAPPACK.get() ? "lappack" : "batpack";
        return Identifier.fromNamespaceAndPath(IIS.MODID, "textures/entity/gear/" + id + ".png");
    }

    private static final Identifier JET_TEXTURE = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/entity/gear/jetpack.png");

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Item item = state.chestEquipment.getItem();
        boolean isJet = item == GearContent.JETPACK.get();
        boolean isPack = item == GearContent.BATPACK.get() || item == GearContent.ADV_BATPACK.get() || item == GearContent.ENERGY_PACK.get() || item == GearContent.LAPPACK.get();
        if (!isJet && !isPack) return;
        pose.pushPose();
        getParentModel().root().translateAndRotate(pose);
        getParentModel().body.translateAndRotate(pose);
        if (isJet) {
            out.submitModelPart(jet, pose, RenderTypes.entityCutout(JET_TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
            // пламя в полёте: игрок в воздухе и не падает камнем
            var level = net.minecraft.client.Minecraft.getInstance().level;
            var entity = level == null ? null : level.getEntity(state.id);
            if (entity != null && !entity.onGround() && entity.getDeltaMovement().y > -0.35) out.submitModelPart(flames, pose, RenderTypes.entityCutout(JET_TEXTURE), 15728880, OverlayTexture.NO_OVERLAY, null);
        } else {
            out.submitModelPart(pack, pose, RenderTypes.entityCutout(texture(item)), light, OverlayTexture.NO_OVERLAY, null);
        }
        pose.popPose();
    }

    /** Слой обоим видам модели игрока. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addToPlayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : PlayerModelType.values()) {
            var renderer = event.getPlayerRenderer(type);
            if (renderer != null) renderer.addLayer(new GearPackLayer((RenderLayerParent) renderer));
        }
    }
}
