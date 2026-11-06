package ru.iis.equipment.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.iis.equipment.vehicle.Driveable;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.equipment.vehicle.VehicleMenu;

/** Окно машины. Панель и слоты рисует код: высота зависит от груза, текстура на каждый размер ничего бы не дала. */
public class VehicleScreen extends AbstractContainerScreen<VehicleMenu> {
    private static final int FACE = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, SLOT = 0xFF8B8B8B, EDGE = 0xFF000000, TEXT = 0xFF303030;

    public VehicleScreen(VehicleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, VehicleMenu.WIDTH, VehicleMenu.invY(menu.rows) + 82);
        inventoryLabelY = VehicleMenu.invY(menu.rows) - 10;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, FACE);
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, DARK);
            g.fill(x + s.x, y + s.y, x + s.x + 17, y + s.y + 17, LIGHT);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        bar(g, x + 52, y + 24, menu.fuel(), 0xFFE0A020);
        bar(g, x + 52, y + 36, menu.health(), 0xFF50B050);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, double part, int color) {
        g.fill(x, y, x + 116, y + 8, DARK);
        g.fill(x + 1, y + 1, x + 115, y + 7, 0xFF303030);
        g.fill(x + 1, y + 1, x + 1 + (int) (114 * Math.max(0, Math.min(1, part))), y + 7, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (menu.rows > 0) g.text(font, Component.translatable("gui.iis.vehicle.cargo"), 8, VehicleMenu.TOP - 10, TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        Driveable d = menu.vehicle();
        if (d != null && mx >= 52 && mx < 168) {
            if (my >= 24 && my < 32) {
                VehicleKind k = d.kind;
                g.setTooltipForNextFrame(font, Component.translatable("gui.iis.vehicle.fuel", (int) (menu.fuel() * 100), (int) (menu.fuel() * k.tank / k.burn / 20)), mouseX, mouseY);
            } else if (my >= 36 && my < 44) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.iis.vehicle.health", (int) (menu.health() * 100)), mouseX, mouseY);
            }
        }
        if (mx >= 25 && mx < 43 && my >= 25 && my < 43 && menu.slots.get(0).getItem().isEmpty() && d != null) {
            g.setTooltipForNextFrame(font, Component.translatable(d.kind.fuel == VehicleKind.Fuel.SOLID ? "gui.iis.vehicle.slot_solid" : "gui.iis.vehicle.slot_liquid"), mouseX, mouseY);
        }
    }
}
