package ru.iis.machine.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.machine.menu.BoilerMenu;

/** GT_GUIContainer_Boiler: шкалы пара (70,25), воды (83,25), температуры (96,25), пламя (117,44). */
public class BoilerScreen extends MachineScreen<BoilerMenu> {
    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.type.gui);
    }

    private static int scale(int value, int max, int px) {
        return Math.min(px, Math.max(0, value * px / max));
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        int steam = scale(menu.steam(), menu.type.guiSteamCapacity - 100, 54);
        int water = scale(menu.water(), 15900, 54);
        int temp = scale(menu.temperature(), menu.type.kind.maxTemperature - 10, 54);
        int e = menu.energy();
        int fire = Math.min(14, Math.max(e > 0 ? 1 : 0, e * 14 / 1000));
        blit(g, 70, 25 + 54 - steam, 194, 54 - steam, 10, steam);
        blit(g, 83, 25 + 54 - water, 204, 54 - water, 10, water);
        blit(g, 96, 25 + 54 - temp, 214, 54 - temp, 10, temp);
        if (fire > 0) blit(g, 117, 44 + 14 - fire, 177, 14 - fire, 15, fire + 1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovering(70, 25, 10, 54, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.steam").append(": ")
                    .append(Component.translatable("gui.iis.amount", menu.steam(), menu.type.kind.capacity)), mouseX, mouseY);
        } else if (hovering(83, 25, 10, 54, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.water").append(": ")
                    .append(Component.translatable("gui.iis.amount", menu.water(), menu.type.kind.capacity)), mouseX, mouseY);
        } else if (hovering(96, 25, 10, 54, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.translatable("gui.iis.temperature").append(": ")
                    .append(Component.translatable("gui.iis.heat", menu.temperature(), menu.type.kind.maxTemperature)), mouseX, mouseY);
        }
    }
}
