package ru.iis.ship.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.iis.IIS;
import ru.iis.ship.ShipContent;
import ru.iis.ship.entity.Brute;
import ru.iis.ship.entity.Crawler;
import ru.iis.ship.entity.Overmind;

/**
 * Вид существ материнского корабля (D156): свои модели из коробок и процедурные текстуры ({@code tools/gen/ship.py}).
 * Черновой вид: ползун — приземистое шестиногое тело, супермутант — грузный двуногий, ядро — пульсирующий ком со щупальцами.
 */
public final class ShipClient {
    private ShipClient() {}

    public static final ModelLayerLocation CRAWLER_LAYER = layer("ship_crawler"), BRUTE_LAYER = layer("ship_brute"), OVERMIND_LAYER = layer("ship_overmind");

    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(IIS.MODID, id), "main");
    }

    private static Identifier texture(String id) {
        return Identifier.fromNamespaceAndPath(IIS.MODID, "textures/entity/" + id + ".png");
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> {
            e.registerLayerDefinition(CRAWLER_LAYER, CrawlerModel::createLayer);
            e.registerLayerDefinition(BRUTE_LAYER, BruteModel::createLayer);
            e.registerLayerDefinition(OVERMIND_LAYER, OvermindModel::createLayer);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ShipContent.CRAWLER.get(), CrawlerRenderer::new);
            e.registerEntityRenderer(ShipContent.BRUTE.get(), BruteRenderer::new);
            e.registerEntityRenderer(ShipContent.OVERMIND.get(), OvermindRenderer::new);
        });
    }

    // --- ползун -----------------------------------------------------------------------------------------------------
    public static class CrawlerModel extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head;
        private final ModelPart[] legs = new ModelPart[6];

        public CrawlerModel(ModelPart root) {
            super(root);
            this.head = root.getChild("head");
            for (int i = 0; i < 6; i++) legs[i] = root.getChild("leg" + i);
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -3.0F, -7.0F, 10.0F, 6.0F, 14.0F), PartPose.offset(0.0F, 17.0F, 0.0F));
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-3.0F, -2.5F, -5.0F, 6.0F, 5.0F, 5.0F), PartPose.offset(0.0F, 17.0F, -7.0F));
            for (int i = 0; i < 6; i++) {
                boolean left = i % 2 == 0;
                root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                        PartPose.offsetAndRotation(left ? 5.0F : -5.0F, 17.0F, -5.0F + 5.0F * (i / 2), 0.0F, 0.0F, left ? -0.6F : 0.6F));
            }
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
            this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
            for (int i = 0; i < 6; i++) {
                legs[i].xRot = Mth.cos(state.walkAnimationPos * 1.2F + i * Mth.PI / 3.0F) * 0.9F * state.walkAnimationSpeed;
            }
        }
    }

    public static class CrawlerRenderer extends MobRenderer<Crawler, LivingEntityRenderState, CrawlerModel> {
        private static final Identifier TEXTURE = texture("ship_crawler");

        public CrawlerRenderer(EntityRendererProvider.Context context) {
            super(context, new CrawlerModel(context.bakeLayer(CRAWLER_LAYER)), 0.5F);
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return TEXTURE;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }

    // --- супермутант ------------------------------------------------------------------------------------------------
    public static class BruteModel extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head, armL, armR, legL, legR;

        public BruteModel(ModelPart root) {
            super(root);
            this.head = root.getChild("head");
            this.armL = root.getChild("arm_l");
            this.armR = root.getChild("arm_r");
            this.legL = root.getChild("leg_l");
            this.legR = root.getChild("leg_r");
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -5.0F, 16.0F, 16.0F, 10.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 26).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, -4.0F, -1.0F));
            root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(32, 26).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 18.0F, 6.0F), PartPose.offset(11.0F, -2.0F, 0.0F));
            root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(32, 26).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 18.0F, 6.0F), PartPose.offset(-11.0F, -2.0F, 0.0F));
            root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(0, 42).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F), PartPose.offset(4.0F, 12.0F, 0.0F));
            root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 42).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F), PartPose.offset(-4.0F, 12.0F, 0.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
            this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
            float swing = Mth.cos(state.walkAnimationPos * 0.6F) * 0.9F * state.walkAnimationSpeed;
            this.legL.xRot = swing;
            this.legR.xRot = -swing;
            this.armL.xRot = -swing * 0.8F;
            this.armR.xRot = swing * 0.8F;
            this.armL.zRot = -0.12F;
            this.armR.zRot = 0.12F;
        }
    }

    public static class BruteRenderer extends MobRenderer<Brute, LivingEntityRenderState, BruteModel> {
        private static final Identifier TEXTURE = texture("ship_brute");

        public BruteRenderer(EntityRendererProvider.Context context) {
            super(context, new BruteModel(context.bakeLayer(BRUTE_LAYER)), 0.9F);
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return TEXTURE;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }

    // --- ядро сверхорганизма ----------------------------------------------------------------------------------------
    public static class OvermindModel extends EntityModel<LivingEntityRenderState> {
        private final ModelPart core;
        private final ModelPart[] lobes = new ModelPart[4], tentacles = new ModelPart[4];

        public OvermindModel(ModelPart root) {
            super(root);
            this.core = root.getChild("core");
            for (int i = 0; i < 4; i++) {
                lobes[i] = root.getChild("lobe" + i);
                tentacles[i] = root.getChild("tentacle" + i);
            }
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -12.0F, -12.0F, 24.0F, 24.0F, 24.0F), PartPose.offset(0.0F, 10.0F, 0.0F));
            for (int i = 0; i < 4; i++) {
                float sx = i % 2 == 0 ? 1.0F : -1.0F, sz = i < 2 ? 1.0F : -1.0F;
                root.addOrReplaceChild("lobe" + i, CubeListBuilder.create().texOffs(0, 48).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 10.0F, 10.0F),
                        PartPose.offset(13.0F * sx, 18.0F, 13.0F * sz));
                root.addOrReplaceChild("tentacle" + i, CubeListBuilder.create().texOffs(96, 0).addBox(-1.5F, -20.0F, -1.5F, 3.0F, 20.0F, 3.0F),
                        PartPose.offset(8.0F * sx, -2.0F, 8.0F * sz));
            }
            return LayerDefinition.create(mesh, 128, 80);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float beat = 1.0F + 0.04F * Mth.sin(state.ageInTicks * 0.25F);
            this.core.xScale = this.core.yScale = this.core.zScale = beat;
            for (int i = 0; i < 4; i++) {
                float pulse = 1.0F + 0.08F * Mth.sin(state.ageInTicks * 0.25F + i * 1.3F);
                lobes[i].xScale = lobes[i].yScale = lobes[i].zScale = pulse;
                tentacles[i].xRot = 0.25F * Mth.sin(state.ageInTicks * 0.08F + i * 1.7F);
                tentacles[i].zRot = 0.25F * Mth.cos(state.ageInTicks * 0.07F + i * 2.1F);
            }
        }
    }

    public static class OvermindRenderer extends MobRenderer<Overmind, LivingEntityRenderState, OvermindModel> {
        private static final Identifier TEXTURE = texture("ship_overmind");

        public OvermindRenderer(EntityRendererProvider.Context context) {
            super(context, new OvermindModel(context.bakeLayer(OVERMIND_LAYER)), 1.6F);
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return TEXTURE;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }
}
