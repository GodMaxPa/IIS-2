package ru.iis.reactor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно реактора (своя текстура, IC2 закрыт, D5): сетка ячеек, закрытые столбцы — пластины, шкала нагрева с порогами перегрева, лампа работы, выход.
 * Пороги шкалы — те же, что в {@link NuclearReactorBlockEntity#step}: 40, 50, 70, 85 и 100 %.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/gui/reactor/reactor.png");
    private static final int DARK = 0xFF404040, BAR_X = 8, BAR_Y = 131, BAR_W = 160, BAR_H = 8;
    private static final int[] MARKS = {40, 50, 70, 85};

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ReactorMenu.WIDTH, ReactorMenu.HEIGHT);
    }

    private void sprite(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + x, topPos + y, u, v, w, h, 256, 256);
    }

    private static int heatColor(int percent) {
        return percent < 40 ? 0xFF3FB950 : percent < 50 ? 0xFFC8C830 : percent < 70 ? 0xFFE8A020 : percent < 85 ? 0xFFE06018 : 0xFFE02020;
    }

    private int percent() {
        return Math.min(100, menu.heat() * 100 / menu.maxHeat());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        sprite(g, 0, 0, 0, 0, imageWidth, imageHeight);
        for (Slot s : menu.slots) {
            if (s.index < NuclearReactorBlockEntity.SLOTS && s.index % ReactorCore.WIDTH >= menu.columns()) sprite(g, s.x - 1, s.y - 1, 176, 0, 18, 18);
        }
        // шкала: заливка цветом участка, в котором стоит нагрев, поверх — риски порогов
        int w = BAR_W * Math.min(menu.heat(), menu.maxHeat()) / menu.maxHeat();
        if (w > 0) g.fill(leftPos + BAR_X, topPos + BAR_Y, leftPos + BAR_X + w, topPos + BAR_Y + BAR_H, heatColor(percent()));
        for (int m : MARKS) {
            int x = leftPos + BAR_X + BAR_W * m / 100;
            g.fill(x, topPos + BAR_Y, x + 1, topPos + BAR_Y + BAR_H, 0xA0000000);
        }
        sprite(g, 160, 5, menu.active() ? 184 : 176, 18, 8, 8);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, font.plainSubstrByWidth(title.getString(), 148), 8, 6, DARK, false);
        int delta = menu.heatDelta();
        Component heat = Component.translatable("gui.iis.reactor.heat_short", percent(), (delta > 0 ? "+" : "") + delta);
        g.text(font, heat, 8, 145, percent() < 40 ? DARK : 0xFFA02000, false);
        Component out = menu.active() ? Component.translatable("gui.iis.reactor.output", menu.euPerTick()) : Component.translatable("gui.iis.reactor.stopped");
        g.text(font, out, imageWidth - 8 - font.width(out), 145, DARK, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.iis.reactor.heat", menu.heat(), menu.maxHeat()));
            for (String k : new String[]{"40", "50", "70", "85", "100"}) lines.add(Component.translatable("gui.iis.reactor.mark." + k));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if (isHovering(159, 4, 10, 10, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable(menu.active() ? "gui.iis.reactor.running" : "gui.iis.reactor.off"), mouseX, mouseY);
        } else if (menu.columns() < ReactorCore.WIDTH && isHovering(8 + menu.columns() * 18, ReactorMenu.GRID_Y, (ReactorCore.WIDTH - menu.columns()) * 18, ReactorCore.HEIGHT * 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.reactor.locked"), mouseX, mouseY);
        }
    }
}
