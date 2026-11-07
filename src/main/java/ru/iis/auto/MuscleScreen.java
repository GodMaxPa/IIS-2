package ru.iis.auto;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.iis.machine.client.MachineScreen;

/** Окно машины на мускульной силе: кнопка с киркой, под ней шкалы силы и хода работы; подробности — в подсказках. */
public class MuscleScreen extends MachineScreen<MuscleMenu> {
    private static final int BX = 31, BY = 8, BW = 26, BH = 22;

    public MuscleScreen(MuscleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "auto/muscle");
    }

    @Override
    protected void extractGauges(GuiGraphicsExtractor g) {
        int x = leftPos + BX, y = topPos;
        g.item(new ItemStack(menu.digger() ? Items.STONE_PICKAXE : Items.IRON_SHOVEL), x + 5, y + BY + 3);
        int p = menu.power() * 22 / MuscleBlockEntity.MAX_POWER;
        g.fill(x + 2, y + 36, x + 2 + p, y + 40, 0xFFE0A030);
        if (menu.maxProgress() > 0) g.fill(x + 2, y + 46, x + 2 + menu.progress() * 22 / menu.maxProgress(), y + 50, 0xFF40C040);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (in(mx, my, BX, BY, BW, BH)) g.setTooltipForNextFrame(Component.translatable("gui.iis.muscle.work"), mouseX, mouseY);
        else if (in(mx, my, BX, 34, BW, 8)) g.setTooltipForNextFrame(Component.translatable("gui.iis.muscle.power", menu.power(), MuscleBlockEntity.MAX_POWER), mouseX, mouseY);
        else if (in(mx, my, BX, 44, BW, 8)) {
            Component c = menu.state() == 2 ? Component.translatable("gui.iis.muscle.done") : menu.state() == 1 ? Component.translatable("gui.iis.muscle.pickup")
                    : Component.translatable("gui.iis.muscle.depth", menu.depth());
            g.setTooltipForNextFrame(c, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - leftPos, my = event.y() - topPos;
        if (in(mx, my, BX, BY, BW, BH)) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            mc.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
