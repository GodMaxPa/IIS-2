package ru.iis.bio.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.bio.production.HearthMenu;

/** Окно очага: слот топлива и пламя над ним (как у печи), остаток жара в секундах. */
public class HearthScreen extends AbstractContainerScreen<HearthMenu> {
    private static final int BG = 0xFF2A2624, PANEL = 0xFF3C3632, SLOT = 0xFF141110, EDGE = 0xFF6A5A50, TEXT = 0xFFE6DCD2;

    public HearthScreen(HearthMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        g.fill(x + 4, y + 14, x + imageWidth - 4, y + 78, PANEL);
        slot(g, 80, 53);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, 84 + 18 * r);
        for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, 142);
        // пламя 14×14 над слотом: тает сверху вниз по мере сгорания
        int fx = x + 81, fy = y + 36, h = Math.round(14 * menu.flame());
        g.fill(fx, fy, fx + 14, fy + 14, SLOT);
        if (h > 0) {
            g.fill(fx + 1, fy + 14 - h, fx + 13, fy + 14, 0xFFE07020);
            g.fill(fx + 4, fy + 14 - Math.max(1, h * 2 / 3), fx + 10, fy + 14, 0xFFFFC040);
        }
    }

    private void slot(GuiGraphicsExtractor g, int sx, int sy) {
        int x = leftPos + sx - 1, y = topPos + sy - 1;
        g.fill(x, y, x + 18, y + 18, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 4, TEXT, false);
        g.text(font, Component.translatable("gui.iis.bio.hearth.heat", menu.heatSeconds()), 104, 40, 0xFFB8A898, false);
    }
}
