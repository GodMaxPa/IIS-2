package ru.iis.machine.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.machine.menu.BbfMenu;

/** GT_GUIContainer_PrimitiveBlastFurnace: стрелка (58,28) 20×11. */
public class BbfScreen extends MachineScreen<BbfMenu> {
    public BbfScreen(BbfMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "brickedblastfurnace");
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        int p = menu.progress();
        if (p > 0) blit(g, 58, 28, 176, 0, Math.max(0, Math.min(20, 1 + p * 20 / 1000)), 11);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        if (!menu.formed()) g.text(font, Component.translatable("gui.iis.structure_incomplete"), 8, 72, 0xFFAA0000, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (menu.progress() > 0 && hovering(58, 28, 20, 11, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("jei.iis.duration", menu.secondsLeft()), mouseX, mouseY);
        }
    }
}
