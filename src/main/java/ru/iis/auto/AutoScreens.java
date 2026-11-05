package ru.iis.auto;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.iis.lv.client.LvScreens;
import ru.iis.machine.client.MachineScreen;

/** Экраны автоматики, хранилищ, бура и настраиваемого трансформатора (D108): текстуры GT, кнопки в тех же местах. */
public final class AutoScreens {
    private AutoScreens() {}

    private static void click(MachineScreen<?> screen, int id) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, id);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static class Automation extends MachineScreen<AutomationMenu> {
        public Automation(AutomationMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "auto/" + menu.kind.gui);
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            for (int b = 0; b < menu.kind.buttons; b++) {
                if (!menu.on(b)) continue;
                int x = leftPos + 8 + b * 18, y = topPos + 63;
                g.fill(x, y, x + 16, y + 16, 0x4040FF40);
            }
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (menu.kind == AutomationType.REGULATOR) {
                int[] xs = {120, 137, 155}, ys = {9, 26, 43};
                for (int i = 0; i < 9; i++) g.text(font, String.valueOf(menu.targetSlot(i)), xs[i % 3], ys[i / 3], 0xFFFAFAFF, false);
            } else if (menu.kind == AutomationType.TYPE_FILTER) {
                g.text(font, Component.translatable("gui.iis.auto.type." + AutomationBlockEntity.TYPE_PREFIXES.get(menu.typeIndex())), 18, 24, 0xFFFAFAFF, false);
            } else if (menu.kind == AutomationType.SUPER_BUFFER) {
                g.text(font, title, 8, 8, 0xFF404040, false);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mx = event.x() - leftPos, my = event.y() - topPos;
            for (int b = 0; b < menu.kind.buttons; b++) {
                if (in(mx, my, 8 + b * 18, 63, 18, 18)) {
                    click(this, b);
                    return true;
                }
            }
            if (menu.kind == AutomationType.TYPE_FILTER && in(mx, my, 8, 14, 80, 30)) {
                click(this, event.button() == 0 ? AutomationMenu.TYPE_NEXT : AutomationMenu.TYPE_PREV);
                return true;
            }
            if (menu.kind == AutomationType.REGULATOR) {
                int[] xs = {119, 136, 153}, ys = {7, 24, 41};
                for (int i = 0; i < 9; i++) {
                    if (in(mx, my, xs[i % 3], ys[i / 3], 17, 17)) {
                        boolean shift = hasShiftDownCompat();
                        click(this, (event.button() == 0 ? AutomationMenu.SLOT_DOWN : AutomationMenu.SLOT_UP) + i + (shift ? 9 : 0));
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    static boolean hasShiftDownCompat() {
        return Minecraft.getInstance().hasShiftDown();
    }

    /** Квантовый сундук (GT_GUIContainer_QuantumChest): вход (80,17), выход (80,53), показ (59,42), число предметов. */
    public static class QuantumChest extends MachineScreen<QuantumChestMenu> {
        public QuantumChest(QuantumChestMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "auto/quantum_chest");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            Item it = BuiltInRegistries.ITEM.byId(menu.itemId());
            if (menu.count() > 0) g.item(new ItemStack(it), leftPos + 59, topPos + 42);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            g.text(font, title, 8, 4, 0xFFFAFAFF, false);
            g.text(font, Component.translatable("gui.iis.quantum.stored"), 8, 16, 0xFFFAFAFF, false);
            g.text(font, String.valueOf(menu.count()), 8, 26, 0xFFFAFAFF, false);
            g.text(font, Component.translatable("gui.iis.quantum.max", menu.capacity()), 8, 72, 0xFFFAFAFF, false);
        }
    }

    /** Автобур и помпа (GT Miner.png): входы, выходы, батарейка, кнопки автовыгрузки. */
    public static class Driller extends MachineScreen<BasicDrillerMenu> {
        public Driller(BasicDrillerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "auto/miner");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {
            if (menu.maxProgress() > 0) blit(g, 78, 24, 176, 0, Math.min(20, menu.progress() * 20 / menu.maxProgress()), 18);
            if (menu.itemTransfer()) g.fill(leftPos + 26, topPos + 63, leftPos + 42, topPos + 79, 0x4040FF40);
            if (menu.fluidTransfer()) g.fill(leftPos + 8, topPos + 63, leftPos + 24, topPos + 79, 0x4040FF40);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractLabels(g, mouseX, mouseY);
            if (menu.fluidAmount() > 0) g.text(font, LvScreens.fluid(menu.fluidId()).getFluidType().getDescription().copy().append(" " + menu.fluidAmount() + " L"), 8, 52, 0xFF404040, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mx = event.x() - leftPos, my = event.y() - topPos;
            for (int b = 0; b < 2; b++) {
                if (in(mx, my, 7 + b * 18, 62, 18, 18)) {
                    click(this, b);
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    /** Настраиваемый трансформатор (GT DigitalTransformer.png): ряды «вход А», «уровень выхода», «выход А»; слева −, справа +. */
    public static class Transformer extends MachineScreen<AdjustableTransformerMenu> {
        public Transformer(AdjustableTransformerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "auto/digital_transformer");
        }

        @Override
        protected void extractGauges(GuiGraphicsExtractor g) {}

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            int white = 0xFFFAFAFF;
            g.text(font, title, 8, 6, white, false);
            g.text(font, Component.translatable("gui.iis.dtransformer.in", menu.voltage(), menu.iAmp()), 8, 28, white, false);
            g.text(font, Component.translatable("gui.iis.dtransformer.tier", menu.oVoltage()), 8, 46, white, false);
            g.text(font, Component.translatable("gui.iis.dtransformer.out", menu.oAmp()), 8, 64, white, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mx = event.x() - leftPos, my = event.y() - topPos;
            for (int col = 0; col < 2; col++) {
                for (int row = 0; row < 3; row++) {
                    if (in(mx, my, 134 + col * 18, 23 + row * 18, 18, 18)) {
                        click(this, (col == 0 ? 0 : 3) + row + (hasShiftDownCompat() ? 6 : 0));
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
    }
}
