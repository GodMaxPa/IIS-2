package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.lv.menu.BatteryBufferMenu;
import ru.iis.machine.client.MachineScreen;

public class BatteryBufferScreen extends MachineScreen<BatteryBufferMenu> {
    public BatteryBufferScreen(BatteryBufferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.slots == 1 ? "lv/1by1" : "lv/2by2");
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, LvScreens.euLine(menu.energy(), menu.capacity()), 8, 70, 0xFF404040, false);
    }
}
