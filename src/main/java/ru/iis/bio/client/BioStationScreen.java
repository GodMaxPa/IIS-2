package ru.iis.bio.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.production.BioStationMenu;
import ru.iis.bio.production.Station;
import ru.iis.bio.production.StationLogic;

/**
 * Общий интерфейс мест Био (D70), рисуется без текстуры: тёмная хитиновая панель, ячейки, баки-столбцы, полоса партии,
 * причина остановки и кнопка режима.
 */
public class BioStationScreen extends AbstractContainerScreen<BioStationMenu> {
    private static final int BG = 0xFF231B22, PANEL = 0xFF3A2A33, SLOT = 0xFF120D11, EDGE = 0xFF6E3446, TEXT = 0xFFE6CFD6;
    private static final int TANK_X = 118, TANK_Y = 16, TANK_H = 46;
    private static final int MODE_X = 112, MODE_Y = 3, MODE_W = 60, MODE_H = 11;

    public BioStationScreen(BioStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    private Station st() {
        return menu.station;
    }

    /** Шаг и ширина бака: до 7 баков — 8/6 пикселей, больше (питательная железа) — плотнее. */
    private int step() {
        return Math.min(8, 56 / Math.max(1, st().tanks()));
    }

    private int tankW() {
        return step() - 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        g.fill(x + 4, y + 14, x + imageWidth - 4, y + 80, PANEL);
        // ячейки места
        for (int i = 0; i < st().inputs; i++) slot(g, 8 + 18 * i, 17);
        for (int i = 0; i < st().outputs; i++) slot(g, 30 + 18 * i, 44);
        if (st().toolSlot) slot(g, 8, 44);
        // инвентарь игрока
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, 84 + 18 * r);
        for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, 142);
        // баки
        for (int k = 0; k < st().tanks(); k++) {
            int tx = x + TANK_X + step() * k, ty = y + TANK_Y;
            g.fill(tx - 1, ty - 1, tx + tankW() + 1, ty + TANK_H + 1, EDGE);
            g.fill(tx, ty, tx + tankW(), ty + TANK_H, SLOT);
            Fluid f = StationLogic.fluidBySync(menu.tankFluid(k));
            int cap = capacity(k);
            int h = f == Fluids.EMPTY || cap <= 0 ? 0 : Math.min(TANK_H, menu.tankAmount(k) * TANK_H / cap);
            if (h > 0) g.fill(tx, ty + TANK_H - h, tx + tankW(), ty + TANK_H, BioFluids.color(f));
            // входной бак — светлая полоска сверху, бак продукта — тёмная
            g.fill(tx, ty - 3, tx + tankW(), ty - 2, isOutputTank(k) ? 0xFF8A4060 : 0xFFB8A0A8);
        }
        // полоса партии
        int bw = 104;
        g.fill(x + 8, y + 64, x + 8 + bw, y + 68, SLOT);
        int p = menu.progressPermille() * bw / 1000;
        if (p > 0) g.fill(x + 8, y + 64, x + 8 + p, y + 68, 0xFFC0405A);
        // кнопка режима
        if (st().modes > 1) {
            boolean hover = isHovering(MODE_X, MODE_Y, MODE_W, MODE_H, mouseX, mouseY);
            g.fill(x + MODE_X, y + MODE_Y, x + MODE_X + MODE_W, y + MODE_Y + MODE_H, hover ? 0xFF7A3A52 : 0xFF55283A);
        }
    }

    private void slot(GuiGraphicsExtractor g, int sx, int sy) {
        int x = leftPos + sx - 1, y = topPos + sy - 1;
        g.fill(x, y, x + 18, y + 18, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    private boolean isOutputTank(int k) {
        return k >= st().inTanks.length && k < st().inTanks.length + st().outTanks.length;
    }

    private int capacity(int k) {
        return st().tankCapacity(k);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 4, TEXT, false);
        if (st().modes > 1) {
            Component mode = Component.translatable("gui.iis.bio." + st().id + ".mode." + menu.mode());
            g.text(font, mode, MODE_X + 3, MODE_Y + 2, TEXT, false);
        }
        StationLogic.Status s = menu.status();
        int color = switch (s) {
            case RUNNING -> 0xFF8FE0A0;
            case IDLE, WAITING_INPUT -> 0xFFB8A0A8;
            default -> 0xFFFF7070;
        };
        g.text(font, Component.translatable("gui.iis.bio.status." + s.name().toLowerCase()), 8, 70, color, false);
        if (st().toolSlot) g.text(font, Component.translatable("gui.iis.bio.tool"), 8, 36, 0xFF9A8890, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        for (int k = 0; k < st().tanks(); k++) {
            if (isHovering(TANK_X + step() * k, TANK_Y, tankW(), TANK_H, mouseX, mouseY)) {
                Fluid f = StationLogic.fluidBySync(menu.tankFluid(k));
                Component name = f == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : f.getFluidType().getDescription();
                String key = k >= st().inTanks.length + st().outTanks.length ? (st().lv() ? "gui.iis.bio.tank.buffer" : "gui.iis.bio.tank.colony")
                        : isOutputTank(k) ? "gui.iis.bio.tank.output" : "gui.iis.bio.tank.input";
                g.setTooltipForNextFrame(Component.translatable(key, name, menu.tankAmount(k), capacity(k)), mouseX, mouseY);
            }
        }
        if (isHovering(8, 64, 104, 4, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.bio.progress", menu.progressPermille() / 10), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (st().modes > 1 && isHovering(MODE_X, MODE_Y, MODE_W, MODE_H, event.x(), event.y()) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
