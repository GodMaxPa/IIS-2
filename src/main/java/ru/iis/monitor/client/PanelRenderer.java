package ru.iis.monitor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.monitor.PanelBlock;
import ru.iis.monitor.PanelBlockEntity;

import java.util.ArrayList;
import java.util.List;

/** Текст на экране панели (D141). Экран — панель в левом нижнем углу и расширители того же рода вправо и вверх (до 8 × 8), повёрнутые так же. */
public class PanelRenderer implements BlockEntityRenderer<PanelBlockEntity, PanelRenderer.State> {
    private final Font font;

    public PanelRenderer(Font font) {
        this.font = font;
    }

    public static final class State extends BlockEntityRenderState {
        final List<FormattedCharSequence> lines = new ArrayList<>();
        Direction facing = Direction.NORTH;
        int w = 1, h = 1, color;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PanelBlockEntity be, State s, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, camera, breakProgress);
        s.facing = be.getBlockState().getValue(PanelBlock.FACING);
        s.color = 0xFF000000 | be.color;
        s.lines.clear();
        be.lines.forEach(c -> s.lines.add(c.getVisualOrderText()));
        Level level = be.getLevel();
        s.w = 1;
        s.h = 1;
        if (level == null) return;
        Direction right = s.facing.getCounterClockWise();
        BlockPos o = be.getBlockPos();
        boolean adv = be.advanced();
        while (s.w < 8 && extender(level.getBlockState(o.relative(right, s.w)), adv, s.facing)) s.w++;
        outer:
        while (s.h < 8) {
            for (int x = 0; x < s.w; x++) if (!extender(level.getBlockState(o.relative(right, x).above(s.h)), adv, s.facing)) break outer;
            s.h++;
        }
    }

    private static boolean extender(BlockState st, boolean adv, Direction f) {
        return st.getBlock() instanceof PanelBlock p && p.extender && p.advanced == adv && st.getValue(PanelBlock.FACING) == f;
    }

    @Override
    public void submit(State s, PoseStack stack, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.lines.isEmpty()) return;
        stack.pushPose();
        stack.translate(0.5F, 0.5F, 0.5F);
        stack.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        stack.translate(-0.5F, -0.5F, 0.502F);
        float m = 1 / 16F;
        int maxW = 1;
        for (FormattedCharSequence l : s.lines) maxW = Math.max(maxW, font.width(l));
        float scale = Math.min((s.w - 2 * m) / maxW, (s.h - 2 * m) / (s.lines.size() * 10F));
        scale = Math.min(scale, Math.min(s.w, s.h) / 48F);
        stack.translate(m, s.h - m, 0);
        stack.scale(scale, -scale, scale);
        for (int i = 0; i < s.lines.size(); i++) {
            out.submitText(stack, 0, i * 10, s.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, 15728880, s.color, 0, 0);
        }
        stack.popPose();
    }
}
