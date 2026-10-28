package ru.iis.rail.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.rail.cart.ElectricLocomotive;
import ru.iis.rail.cart.SteamLocomotive;
import ru.iis.rail.menu.LocomotiveMenu;

/** Окно локомотива: кнопки режима (3) и скорости (5), шкалы воды, пара и нагрева или заряда, слоты паровоза. */
public class LocomotiveScreen extends AbstractContainerScreen<LocomotiveMenu> {
    private static final int BG = 0xFF2C2F33, PANEL = 0xFF3A3E44, SLOT = 0xFF15171A, EDGE = 0xFF6B7078, TEXT = 0xFFE4E6EA,
            BTN = 0xFF4A5058, BTN_ON = 0xFF7A8E3A;
    private static final int MODE_Y = 16, SPEED_Y = 30, BTN_H = 12, GAUGE_Y = 46, GAUGE_H = 48;
    private static final String[] MODES = {"running", "idle", "shutdown"};
    private static final String[] SPEEDS = {"max", "slow", "slower", "slowest", "reverse"};

    public LocomotiveScreen(LocomotiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 184);
        inventoryLabelY = LocomotiveMenu.INV_Y - 11;
    }

    private static int modeX(int i) {
        return 8 + 54 * i;
    }

    private static int speedX(int i) {
        return 8 + 32 * i;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        g.fill(x + 4, y + GAUGE_Y - 2, x + imageWidth - 4, y + GAUGE_Y + GAUGE_H + 2, PANEL);
        for (int i = 0; i < 3; i++) button(g, modeX(i), MODE_Y, 50, menu.mode().ordinal() == i, "gui.iis.loco.mode." + MODES[i]);
        for (int i = 0; i < 5; i++) button(g, speedX(i), SPEED_Y, 30, menu.speed().ordinal() == i, "gui.iis.loco.speed." + SPEEDS[i]);
        if (menu.steam) {
            gauge(g, 8, menu.water(), SteamLocomotive.WATER_CAPACITY, 0xFF3060D0);
            gauge(g, 24, menu.steamAmount(), SteamLocomotive.STEAM_CAPACITY, 0xFFD0D0D0);
            gauge(g, 40, menu.heat() - 20, 480, 0xFFD04020);
            slot(g, 62, 48);
            slot(g, 62, 76);
            slot(g, 92, 76);
            for (int i = 0; i < 3; i++) slot(g, 116 + 18 * i, 76);
            int fx = x + 93, fy = y + 58;
            g.fill(fx, fy, fx + 14, fy + 14, SLOT);
            if (menu.burning()) {
                g.fill(fx + 1, fy + 3, fx + 13, fy + 14, 0xFFE07020);
                g.fill(fx + 4, fy + 6, fx + 10, fy + 14, 0xFFFFC040);
            }
        } else {
            gauge(g, 8, menu.charge(), ElectricLocomotive.MAX_CHARGE, 0xFFE0C030);
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, LocomotiveMenu.INV_Y + 18 * r);
        for (int c = 0; c < 9; c++) slot(g, 8 + 18 * c, LocomotiveMenu.INV_Y + 58);
    }

    private void button(GuiGraphicsExtractor g, int bx, int by, int w, boolean on, String key) {
        int x = leftPos + bx, y = topPos + by;
        g.fill(x, y, x + w, y + BTN_H, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, on ? BTN_ON : BTN);
        Component c = Component.translatable(key);
        g.text(font, c, x + (w - font.width(c)) / 2, y + 2, TEXT, false);
    }

    private void gauge(GuiGraphicsExtractor g, int gx, int value, int max, int color) {
        int x = leftPos + gx, y = topPos + GAUGE_Y;
        g.fill(x, y, x + 12, y + GAUGE_H, EDGE);
        g.fill(x + 1, y + 1, x + 11, y + GAUGE_H - 1, SLOT);
        int h = max <= 0 ? 0 : Math.round((GAUGE_H - 2) * Math.max(0, Math.min(1f, value / (float) max)));
        if (h > 0) g.fill(x + 1, y + GAUGE_H - 1 - h, x + 11, y + GAUGE_H - 1, color);
    }

    private void slot(GuiGraphicsExtractor g, int sx, int sy) {
        int x = leftPos + sx - 1, y = topPos + sy - 1;
        g.fill(x, y, x + 18, y + 18, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 4, TEXT, false);
        g.text(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
        int tx = menu.steam ? 116 : 26;
        if (menu.steam) {
            g.text(font, Component.translatable("gui.iis.loco.water", menu.water()), tx, GAUGE_Y, 0xFF9AB8F0, false);
            g.text(font, Component.translatable("gui.iis.loco.steam", menu.steamAmount()), tx, GAUGE_Y + 10, 0xFFD8D8D8, false);
            g.text(font, Component.translatable("gui.iis.loco.heat", menu.heat()), tx, GAUGE_Y + 20, 0xFFF0A080, false);
        } else {
            g.text(font, Component.translatable("gui.iis.loco.charge", menu.charge(), ElectricLocomotive.MAX_CHARGE), tx, GAUGE_Y + 4, 0xFFF0E090, false);
            g.text(font, Component.translatable("gui.iis.loco.electric_hint"), tx, GAUGE_Y + 18, 0xFFB0B4BA, false);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        int button = -1;
        for (int i = 0; i < 3; i++) if (isHovering(modeX(i), MODE_Y, 50, BTN_H, mx, my)) button = i;
        for (int i = 0; i < 5; i++) if (isHovering(speedX(i), SPEED_Y, 30, BTN_H, mx, my)) button = 3 + i;
        if (button < 0) return super.mouseClicked(event, doubleClick);
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        mc.gameMode.handleInventoryButtonClick(menu.containerId, button);
        return true;
    }
}
