package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.block.CokeOvenBlockEntity;
import ru.iis.lv.menu.CokeOvenMenu;
import ru.iis.machine.client.MachineScreen;

/** Коксовая печь: стрелка (66,34) из (176,0), бак креозота (120,15)–(137,72) — заливка цветом креозота. */
public class CokeOvenScreen extends MachineScreen<CokeOvenMenu> {
    public CokeOvenScreen(CokeOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lv/coke_oven");
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        blit(g, 66, 34, 176, 0, menu.progressPermille() * 24 / 1000, 17);
        int h = menu.creosote() * 56 / CokeOvenBlockEntity.TANK;
        if (h > 0) g.fill(leftPos + 121, topPos + 16 + 56 - h, leftPos + 137, topPos + 72, 0xFF5A3C14);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        if (!menu.formed()) g.text(font, Component.translatable("gui.iis.coke_oven.incomplete"), 8, 70, 0xFFAA2020, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovering(120, 15, 18, 58, mouseX, mouseY)) {
            g.setTooltipForNextFrame(LvScreens.fluidLine(IISFluids.CREOSOTE.get(), menu.creosote(), CokeOvenBlockEntity.TANK), mouseX, mouseY);
        }
    }
}
