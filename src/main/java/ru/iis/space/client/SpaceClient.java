package ru.iis.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.iis.IIS;
import ru.iis.space.NasaWorkbenchMenu;
import ru.iis.space.RocketEntity;
import ru.iis.space.RocketMenu;

/** Клиент космоса: модель ракеты и окна. Модель и текстура свои. */
public final class SpaceClient {
    private SpaceClient() {}

    public static final ModelLayerLocation ROCKET_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(IIS.MODID, "rocket"), "main");
    private static final Identifier ROCKET_TEXTURE = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/entity/rocket_t1.png");

    public static LayerDefinition rocketLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rocket", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -72, -8, 16, 64, 16)      // корпус
                .texOffs(0, 40).addBox(-5, -84, -5, 10, 12, 10)     // обтекатель
                .texOffs(0, 40).addBox(-2, -92, -2, 4, 8, 4)
                .texOffs(0, 40).addBox(-14, -20, -2, 6, 20, 4)      // стабилизаторы
                .texOffs(0, 40).addBox(8, -20, -2, 6, 20, 4)
                .texOffs(0, 40).addBox(-2, -20, -14, 4, 20, 6)
                .texOffs(0, 40).addBox(-2, -20, 8, 4, 20, 6)
                .texOffs(0, 40).addBox(-5, -8, -5, 10, 8, 10), PartPose.ZERO);   // сопло
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static final class RocketRenderer extends EntityRenderer<RocketEntity, EntityRenderState> {
        private final ModelPart model;

        public RocketRenderer(EntityRendererProvider.Context context) {
            super(context);
            model = context.bakeLayer(ROCKET_LAYER);
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }

        @Override
        public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            pose.pushPose();
            pose.scale(-1, -1, 1);
            collector.submitModelPart(model, pose, RenderTypes.entitySolid(ROCKET_TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
            super.submit(state, pose, collector, camera);
        }
    }

    private static final int BG = 0xFF2C2F33, SLOT = 0xFF15171A, EDGE = 0xFF6B7078, TEXT = 0xFFE4E6EA;

    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
    }

    private static void slots(GuiGraphicsExtractor g, int x, int y, Iterable<Slot> slots) {
        for (Slot s : slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT);
    }

    public static final class NasaScreen extends AbstractContainerScreen<NasaWorkbenchMenu> {
        public NasaScreen(NasaWorkbenchMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, NasaWorkbenchMenu.WIDTH, NasaWorkbenchMenu.HEIGHT);
            inventoryLabelY = NasaWorkbenchMenu.INV_Y - 11;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractBackground(g, mouseX, mouseY, a);
            frame(g, leftPos, topPos, imageWidth, imageHeight);
            slots(g, leftPos, topPos, menu.slots);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            g.text(font, title, titleLabelX, titleLabelY, TEXT, false);
            g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        }
    }

    public static final class RocketScreen extends AbstractContainerScreen<RocketMenu> {
        public RocketScreen(RocketMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, 176, 114 + menu.rows * 18);
            inventoryLabelY = 21 + menu.rows * 18;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractBackground(g, mouseX, mouseY, a);
            frame(g, leftPos, topPos, imageWidth, imageHeight);
            slots(g, leftPos, topPos, menu.slots);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            g.text(font, title, titleLabelX, titleLabelY, TEXT, false);
            g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        }
    }
}
