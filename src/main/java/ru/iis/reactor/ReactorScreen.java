package ru.iis.reactor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Окно реактора: шкала нагрева, выход, ячейки. Рисуется кодом, текстура IC2 закрыта. */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private static final int BG = 0xFF2C2F33, SLOT = 0xFF15171A, LOCKED = 0xFF3A3E44, EDGE = 0xFF6B7078, TEXT = 0xFFE4E6EA;

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ReactorMenu.WIDTH, ReactorMenu.HEIGHT);
        inventoryLabelY = ReactorMenu.INV_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        for (Slot s : menu.slots) {
            boolean grid = s.index < NuclearReactorBlockEntity.SLOTS;
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, grid && s.index % ReactorCore.WIDTH >= menu.columns() ? LOCKED : SLOT);
        }
        float heat = Math.min(1F, menu.heat() / (float) menu.maxHeat());
        int barX = x + 8, barY = y + 20, barW = 160;
        g.fill(barX, barY, barX + barW, barY + 6, SLOT);
        int color = heat < 0.4F ? 0xFF40B040 : heat < 0.7F ? 0xFFE0C030 : heat < 0.85F ? 0xFFE07020 : 0xFFE02020;
        g.fill(barX, barY, barX + (int) (barW * heat), barY + 6, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.translatable("gui.iis.reactor.heat", menu.heat(), menu.maxHeat()), 8, 10, TEXT, false);
        Component right = menu.active() ? Component.translatable("gui.iis.reactor.output", menu.euPerTick()) : Component.translatable("gui.iis.reactor.off");
        g.text(font, right, imageWidth - 8 - font.width(right), 10, menu.active() ? TEXT : 0xFFE07020, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }
}
