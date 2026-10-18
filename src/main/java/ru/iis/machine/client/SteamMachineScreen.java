package ru.iis.machine.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.machine.logic.SteamMachineLogic;
import ru.iis.machine.menu.SteamMachineMenu;

/** GT_GUIContainer_BasicMachine (паровые): стрелка прогресса (78,24), знак «нет пара / выход перекрыт» (79,44). */
public class SteamMachineScreen extends MachineScreen<SteamMachineMenu> {
    public SteamMachineScreen(SteamMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.tier.id + menu.type.gui);
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        if (menu.stuttering()) blit(g, 79, 44, 176, 54, 18, 18);
        int max = menu.maxProgress();
        if (max > 0) {
            int p = Math.min(20, menu.progress() * 20 / max);
            blit(g, 78, 24, 176, 0, p, 18);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovering(78, 24, 20, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.steam").append(": ")
                    .append(Component.translatable("gui.iis.amount", menu.steam(), SteamMachineLogic.MAX_STEAM_UNITS * 2)), mouseX, mouseY);
        } else if (menu.stuttering() && hovering(79, 44, 18, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable(menu.steam() < SteamMachineLogic.MIN_UNITS_TO_START * 2
                    ? "gui.iis.no_steam" : "gui.iis.steam_blocked"), mouseX, mouseY);
        }
    }
}
