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
import ru.iis.multi.block.MaintenanceHatchBlockEntity;
import ru.iis.multi.logic.Maintenance;
import ru.iis.multi.menu.ControllerMenu;
import ru.iis.multi.menu.EbfMenu;
import ru.iis.multi.menu.FluidHatchMenu;
import ru.iis.multi.menu.ItemBusMenu;

/** Экраны люков и контроллера. */
public final class MultiScreens {
    private MultiScreens() {}

    /** Строка экрана контроллера: не шире чёрного поля. */
    static void line(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component text, int x, int y, int color, boolean shadow) {
        line(g, font, text.getString(), x, y, color, shadow);
    }

    static void line(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, int color, boolean shadow) {
        int room = 138;
        String t = font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("...")) + "...";
        g.text(font, t, x, y, color, shadow);
    }

    public static class Bus extends MachineScreen<ItemBusMenu> {
        public Bus(ItemBusMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, menu.slots.size() - 36 <= 1 ? "lv/1by1" : menu.slots.size() - 36 <= 4 ? "lv/2by2" : menu.slots.size() - 36 <= 9 ? "lv/3by3" : "lv/4by4");
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
            line(g, font, f == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : f.getFluidType().getDescription(), 10, 20, 0xFFFAFAFF, false);
            line(g, font, Component.literal(menu.amount() + " / " + menu.capacity() + " L"), 10, 30, 0xFFFAFAFF, false);
        }
    }

    public static class Ebf extends MachineScreen<EbfMenu> {
        public Ebf(EbfMenu menu, Inventory inventory, Component title) {
            this(menu, inventory, title, "multi/electric_blast_furnace");
        }

        protected Ebf(EbfMenu menu, Inventory inventory, Component title, String texture) {
            super(menu, inventory, title, texture);
        }

        /** На дисплее GT нарисован слот (152,5), у этих контроллеров он не нужен: закрываем панелью, чтобы не обманывал. */
        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            g.fill(leftPos + 151, topPos + 4, leftPos + 169, topPos + 22, 0xFFC6C6C6);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = 8, white = 0xFFFAFAFF, red = 0xFFFF5555;
            line(g, font, font.plainSubstrByWidth(title.getString(), 136), 10, y, white, false);
            y += 10;
            if (!menu.formed()) {
                line(g, font, Component.translatable("gui.iis.ebf.structure_bad"), 10, y, red, false);
                return;
            }
            int problems = Maintenance.IDEAL - Integer.bitCount(menu.okMask());
            line(g, font, Component.translatable("gui.iis.ebf.problems", problems), 10, y, problems > 0 ? red : white, false);
            y += 10;
            if (problems > 0) {
                for (Maintenance.Problem p : Maintenance.Problem.values()) {
                    if ((menu.okMask() & (1 << p.ordinal())) != 0) continue;
                    line(g, font, Component.literal("- ").append(MaintenanceHatchBlockEntity.problemName(p)).withStyle(ChatFormatting.RED), 10, y, red, false);
                    y += 9;
                    if (y > 70) break;
                }
            }
            if (!menu.works()) {
                line(g, font, Component.translatable("gui.iis.ebf.disabled"), 10, y, red, false);
                y += 10;
                line(g, font, Component.translatable("gui.iis.ebf.disabled_hint"), 10, y, red, false);
                y += 10;
            }
            if (menu.mufflerBlocked()) {
                line(g, font, Component.translatable("gui.iis.ebf.muffler"), 10, y, red, false);
                y += 10;
            }
            if (y > 70) return;
            if (menu.heat() > 0) {
                line(g, font, Component.translatable("gui.iis.ebf.heat", menu.heat()), 10, y, white, false);
                y += 10;
            }
            line(g, font, Component.translatable("gui.iis.ebf.voltage", menu.voltage()), 10, y, white, false);
            y += 10;
            if (menu.maxProgress() > 0 && y <= 70) {
                line(g, font, Component.translatable("gui.iis.ebf.progress", menu.progress(), menu.maxProgress()), 10, y, white, false);
                y += 10;
                if (y <= 70) line(g, font, Component.translatable("gui.iis.ebf.eut", menu.eut()), 10, y, white, false);
            }
        }
    }

    /**
     * Строки состояния, общие для экранов контроллеров: форма, неисправности, выключение, выхлоп (GT_GUIContainer_MultiMachine).
     * Возвращает следующую строку или -1, если дальше писать не нужно.
     */
    static int statusLines(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component title, boolean formed, int okMask,
                           boolean works, boolean mufflerBlocked) {
        int y = 8, white = 0xFFFAFAFF, red = 0xFFFF5555;
        line(g, font, font.plainSubstrByWidth(title.getString(), 136), 10, y, white, false);
        y += 10;
        if (!formed) {
            line(g, font, Component.translatable("gui.iis.ebf.structure_bad"), 10, y, red, false);
            return -1;
        }
        int problems = Maintenance.IDEAL - Integer.bitCount(okMask);
        line(g, font, Component.translatable("gui.iis.ebf.problems", problems), 10, y, problems > 0 ? red : white, false);
        y += 10;
        if (problems > 0) {
            for (Maintenance.Problem p : Maintenance.Problem.values()) {
                if ((okMask & (1 << p.ordinal())) != 0) continue;
                line(g, font, Component.literal("- ").append(MaintenanceHatchBlockEntity.problemName(p)).withStyle(ChatFormatting.RED), 10, y, red, false);
                y += 9;
                if (y > 70) return -1;
            }
        }
        if (!works) {
            line(g, font, Component.translatable("gui.iis.ebf.disabled"), 10, y, red, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.ebf.disabled_hint"), 10, y, red, false);
            y += 10;
        }
        if (mufflerBlocked) {
            line(g, font, Component.translatable("gui.iis.ebf.muffler"), 10, y, red, false);
            y += 10;
        }
        return y > 70 ? -1 : y;
    }

    /** Большой котёл (D88): экран GT LargeBoiler.png со слотом схемы; строки GT getInfoData — пар, КПД, запас топлива. */
    public static class LargeBoiler extends MachineScreen<ControllerMenu> {
        public LargeBoiler(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/large_boiler");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF;
            line(g, font, Component.translatable(menu.extra(3) != 0 ? "gui.iis.boiler.sh_steam" : "gui.iis.boiler.steam", menu.extra(0)), 10, y, white, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.boiler.efficiency", menu.efficiency()), 10, y, white, false);
            y += 10;
            if (y <= 70) line(g, font, Component.translatable("gui.iis.boiler.fuel", menu.extra(1)), 10, y, white, false);
            y += 10;
            if (y <= 70 && menu.extra(2) > 0) line(g, font, Component.translatable("gui.iis.boiler.circuit", menu.extra(2)), 10, y, white, false);
        }
    }

    /** Большая турбина (D89): экран GT LargeTurbine.png со слотом ротора; строки GT getInfoData. */
    public static class LargeTurbine extends MachineScreen<ControllerMenu> {
        public LargeTurbine(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/large_turbine");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF, red = 0xFFFF5555;
            if (menu.extra(4) == 0) {
                line(g, font, Component.translatable("gui.iis.turbine.no_rotor"), 10, y, red, false);
                return;
            }
            line(g, font, Component.translatable("gui.iis.turbine.output", menu.extra(0)), 10, y, white, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.turbine.flow", menu.extra(1)), 10, y, white, false);
            y += 10;
            if (y <= 70) line(g, font, Component.translatable("gui.iis.turbine.speed", menu.efficiency(), menu.extra(2)), 10, y, white, false);
            y += 10;
            if (y <= 70) line(g, font, Component.translatable("gui.iis.turbine.damage", menu.extra(3)), 10, y, white, false);
        }
    }

    /** Большой теплообменник (D105): экран GT LargeHeatExchanger.png со слотом схемы; строки GT getInfoData. */
    public static class HeatExchanger extends MachineScreen<ControllerMenu> {
        public HeatExchanger(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/heat_exchanger");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF;
            line(g, font, Component.translatable(menu.extra(1) != 0 ? "gui.iis.boiler.sh_steam" : "gui.iis.boiler.steam", menu.extra(0)), 10, y, white, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.boiler.efficiency", menu.efficiency()), 10, y, white, false);
            y += 10;
            if (y <= 70) line(g, font, Component.translatable("gui.iis.heat_exchanger.threshold", menu.extra(2)), 10, y, white, false);
            y += 10;
            if (y <= 70 && menu.extra(3) > 0) line(g, font, Component.translatable("gui.iis.boiler.circuit", menu.extra(3)), 10, y, white, false);
        }
    }

    /** Термоядерный реактор (D116): экран GT FusionComputer.png, запас в поле нагрева, тысячи EU. */
    public static class Fusion extends MachineScreen<ControllerMenu> {
        public Fusion(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/fusion");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int white = 0xFFFAFAFF;
            line(g, font, font.plainSubstrByWidth(title.getString(), 150), 10, 8, white, false);
            line(g, font, Component.translatable(menu.formed() ? "gui.iis.fusion.store" : "gui.iis.ebf.structure_bad", menu.data(6), menu.data(7)), 10, 18, menu.formed() ? white : 0xFFFF5555, false);
        }
    }

    /** Сборочная линия (D110): экран GT AssemblyLine.png со слотом флешки. */
    public static class AssemblyLine extends MachineScreen<ControllerMenu> {
        public AssemblyLine(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/assembly_line");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
        }
    }

    /** Обрабатывающий массив (D105): экран GT ProcessingArray.png со слотом машин. */
    public static class ProcessingArray extends MachineScreen<ControllerMenu> {
        public ProcessingArray(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/processing_array");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF, red = 0xFFFF5555;
            if (menu.extra(1) == 0) {
                line(g, font, Component.translatable("gui.iis.processing_array.no_machine"), 10, y, red, false);
                return;
            }
            line(g, font, Component.translatable("gui.iis.processing_array.parallel", menu.extra(0)), 10, y, white, false);
            y += 10;
            if (menu.eut() > 0) line(g, font, Component.translatable("gui.iis.ebf.eut", menu.eut()), 10, y, white, false);
        }
    }

    /** Буровые (D106): экран GT DrillingRig.png со слотом шахтных труб. */
    public static class Drill extends MachineScreen<ControllerMenu> {
        public Drill(ControllerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/drill");
        }

        /** Слот шахтных труб есть, а на DrillingRig.png рамки нет. */
        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            slotFrame(g, 151, 4);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF;
            line(g, font, Component.translatable("gui.iis.drill.state." + Math.max(0, Math.min(2, menu.extra(1)))), 10, y, white, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.drill.depth", menu.extra(0), menu.extra(2)), 10, y, white, false);
            y += 10;
            if (y <= 70 && menu.eut() > 0) line(g, font, Component.translatable("gui.iis.ebf.eut", menu.eut()), 10, y, white, false);
        }
    }

    /** Чистая комната (D105): экран GT MultiblockDisplay.png; чистота — это КПД машины. */
    public static class Cleanroom extends Ebf {
        public Cleanroom(EbfMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/cleanroom");
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int y = statusLines(g, font, title, menu.formed(), menu.okMask(), menu.works(), menu.mufflerBlocked());
            if (y < 0) return;
            int white = 0xFFFAFAFF;
            line(g, font, Component.translatable("gui.iis.cleanroom.cleanliness", menu.efficiency()), 10, y, white, false);
            y += 10;
            line(g, font, Component.translatable("gui.iis.ebf.voltage", menu.voltage()), 10, y, white, false);
            y += 10;
            if (y <= 70 && menu.eut() > 0) line(g, font, Component.translatable("gui.iis.ebf.eut", menu.eut()), 10, y, white, false);
        }
    }

    /** Вакуумный морозильник: экран GT VacuumFreezer.png, те же строки без нагрева (D81). */
    public static class Freezer extends Ebf {
        public Freezer(EbfMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "multi/vacuum_freezer");
        }
    }
}
