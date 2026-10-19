package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.lv.menu.GeneratorMenu;
import ru.iis.machine.client.MachineScreen;

/** GT_GUIContainer_BasicTank: сведения в чёрном окне, жидкость (80,35). */
public class GeneratorScreen extends MachineScreen<GeneratorMenu> {
    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lv/basictank");
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        LvScreens.fluidIcon(g, leftPos + 80, topPos + 35, LvScreens.fluid(menu.fluidId()));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, LvScreens.fluidName(LvScreens.fluid(menu.fluidId())), 10, 20, 0xFFFAFAFF, false);
        g.text(font, Component.literal(menu.fluidAmount() + " / " + menu.type.tank + " L"), 10, 30, 0xFFFAFAFF, false);
        g.text(font, Component.literal(menu.energy() + " / " + menu.capacity() + " EU"), 10, 46, 0xFFFAFAFF, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovering(80, 35, 16, 16, mouseX, mouseY)) {
            g.setTooltipForNextFrame(LvScreens.fluidLine(LvScreens.fluid(menu.fluidId()), menu.fluidAmount(), menu.type.tank), mouseX, mouseY);
        }
    }
}
