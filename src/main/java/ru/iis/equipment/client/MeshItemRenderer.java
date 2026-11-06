package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Предмет рисуется той же моделью, что и сущность: ружьё в руке и машина в слоте. Положение в руке задаёт базовая модель (tools/gen/equipment.py). */
public record MeshItemRenderer(MeshModel model) implements SpecialModelRenderer<Void> {
    private static final MeshModel.Pose REST = new MeshModel.Pose();

    @Override
    public void submit(@Nullable Void argument, PoseStack stack, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        stack.pushPose();
        stack.translate(0.5F, 0.5F, 0.5F);
        stack.scale(1 / 16F, 1 / 16F, 1 / 16F);
        model.submit(stack, out, light, overlay, REST);
        stack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float[] b = model.bounds;
        for (int i = 0; i < 8; i++) {
            output.accept(new Vector3f(((i & 1) == 0 ? b[0] : b[3]) / 16 + 0.5F, ((i & 2) == 0 ? b[1] : b[4]) / 16 + 0.5F, ((i & 4) == 0 ? b[2] : b[5]) / 16 + 0.5F));
        }
    }

    @Override
    public @Nullable Void extractArgument(ItemStack stack) {
        return null;
    }

    public record Unbaked(String mesh) implements SpecialModelRenderer.Unbaked<Void> {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("mesh").forGetter(Unbaked::mesh)).apply(i, Unbaked::new));

        @Override
        public @Nullable SpecialModelRenderer<Void> bake(BakingContext context) {
            return new MeshItemRenderer(MeshModel.get(mesh));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}
