package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.lv.menu.ElectricMachineMenu;
import ru.iis.machine.client.MachineScreen;

/** GT_GUIContainer_BasicMachine: стрелка (78,24), «нет энергии» (79,44), жидкость (53,63). */
public class ElectricMachineScreen extends MachineScreen<ElectricMachineMenu> {
    public ElectricMachineScreen(ElectricMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lv/" + menu.type.gui);
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        if (menu.stuttering()) blit(g, 79, 44, 176, 54, 18, 18);
        if (menu.working()) blit(g, 78, 24, 176, 0, Math.min(20, menu.progressPermille() * 20 / 1000), 18);
        if (menu.type.tank > 0) LvScreens.fluidIcon(g, leftPos + 53, topPos + 63, LvScreens.fluid(menu.fluidId()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovering(78, 24, 20, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(LvScreens.euLine(menu.energy(), menu.capacity()), mouseX, mouseY);
        } else if (menu.stuttering() && hovering(79, 44, 18, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.no_energy"), mouseX, mouseY);
        } else if (menu.type.tank > 0 && hovering(53, 63, 16, 16, mouseX, mouseY)) {
            g.setTooltipForNextFrame(LvScreens.fluidLine(LvScreens.fluid(menu.fluidId()), menu.fluidAmount(), menu.type.tank), mouseX, mouseY);
        }
    }
}
