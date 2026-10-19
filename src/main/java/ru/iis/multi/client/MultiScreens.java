package ru.iis.multi.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.machine.client.MachineScreen;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.block.MaintenanceHatchBlockEntity;
import ru.iis.multi.logic.Maintenance;
import ru.iis.multi.menu.EbfMenu;
import ru.iis.multi.menu.FluidHatchMenu;
import ru.iis.multi.menu.ItemBusMenu;

/** Экраны люков и контроллера. */
public final class MultiScreens {
    private MultiScreens() {}

    public static class Bus extends MachineScreen<ItemBusMenu> {
        public Bus(ItemBusMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "lv/2by2");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}
    }

    public static class FluidHatch extends MachineScreen<FluidHatchMenu> {
        public FluidHatch(FluidHatchMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "lv/basictank");
        }

        private Fluid fluid() {
            return menu.fluidId() < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(menu.fluidId());
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            Fluid f = fluid();
            if (f != Fluids.EMPTY && f.getBucket() != Items.AIR) g.item(new ItemStack(f.getBucket()), leftPos + 80, topPos + 35);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractLabels(g, mouseX, mouseY);
            Fluid f = fluid();
            g.text(font, f == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : f.getFluidType().getDescription(), 10, 20, 0xFFFAFAFF, false);
            g.text(font, Component.literal(menu.amount() + " / " + HatchType.TANK + " L"), 10, 30, 0xFFFAFAFF, false);
        }
    }

    public static class Ebf extends MachineScreen<EbfMenu> {
        public Ebf(EbfMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/electric_blast_furnace");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = 8, white = 0xFFFAFAFF, red = 0xFFFF5555;
            g.text(font, font.plainSubstrByWidth(title.getString(), 136), 10, y, white, false);
            y += 10;
            if (!menu.formed()) {
                g.text(font, Component.translatable("gui.iis.ebf.structure_bad"), 10, y, red, false);
                return;
            }
            int problems = Maintenance.IDEAL - Integer.bitCount(menu.okMask());
            g.text(font, Component.translatable("gui.iis.ebf.problems", problems), 10, y, problems > 0 ? red : white, false);
            y += 10;
            if (problems > 0) {
                for (Maintenance.Problem p : Maintenance.Problem.values()) {
                    if ((menu.okMask() & (1 << p.ordinal())) != 0) continue;
                    g.text(font, Component.literal("- ").append(MaintenanceHatchBlockEntity.problemName(p)).withStyle(ChatFormatting.RED), 10, y, red, false);
                    y += 9;
                    if (y > 70) break;
                }
            }
            if (!menu.works()) {
                g.text(font, Component.translatable("gui.iis.ebf.disabled"), 10, y, red, false);
                y += 10;
                g.text(font, Component.translatable("gui.iis.ebf.disabled_hint"), 10, y, red, false);
                y += 10;
            }
            if (menu.mufflerBlocked()) {
                g.text(font, Component.translatable("gui.iis.ebf.muffler"), 10, y, red, false);
                y += 10;
            }
            if (y > 70) return;
            g.text(font, Component.translatable("gui.iis.ebf.heat", menu.heat()), 10, y, white, false);
            y += 10;
            g.text(font, Component.translatable("gui.iis.ebf.voltage", menu.voltage()), 10, y, white, false);
            y += 10;
            if (menu.maxProgress() > 0 && y <= 70) {
                g.text(font, Component.translatable("gui.iis.ebf.progress", menu.progress(), menu.maxProgress()), 10, y, white, false);
                y += 10;
                if (y <= 70) g.text(font, Component.translatable("gui.iis.ebf.eut", menu.eut()), 10, y, white, false);
            }
        }
    }
}
