package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.lv.menu.BatteryBufferMenu;
import ru.iis.machine.client.MachineScreen;

public class BatteryBufferScreen extends MachineScreen<BatteryBufferMenu> {
    public BatteryBufferScreen(BatteryBufferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lv/" + (int) Math.round(Math.sqrt(menu.slots)) + "by" + (int) Math.round(Math.sqrt(menu.slots)));
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        if (menu.slots <= 4) {
            g.text(font, LvScreens.euLine(menu.energy(), menu.capacity()), 8, 70, 0xFF404040, false);
            return;
        }
        // большие сетки занимают середину окна: заряд столбиком слева, точно — в подсказке
        g.text(font, compact(menu.energy()), 8, 62, 0xFF404040, false);
        g.text(font, "/ " + compact(menu.capacity()), 8, 71, 0xFF404040, false);
    }

    private static String compact(long v) {
        if (v >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000) return (v / 1000) + "k";
        return Long.toString(v);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (menu.slots > 4 && hovering(8, 60, 44, 20, mouseX, mouseY)) g.setTooltipForNextFrame(LvScreens.euLine(menu.energy(), menu.capacity()), mouseX, mouseY);
    }
}
