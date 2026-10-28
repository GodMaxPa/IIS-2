package ru.iis.cover.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.iis.IIS;
import ru.iis.cover.CoverKind;
import ru.iis.cover.CoverMenu;
import ru.iis.cover.Covers;

/** GT_GUICover на текстурах GT (GuiCover.png 176×107, иконки GuiButtons.png по 32×32, рисуются 16×16), D101. */
public class CoverScreen extends AbstractContainerScreen<CoverMenu> {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/gui/cover/gui_cover.png");
    private static final Identifier BUTTONS = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/gui/cover/gui_buttons.png");
    /** GT_Cover_Conveyor.GUI: startX 10, startY 25, шаг 18. */
    private static final int SX = 10, SY = 25, STEP = 18;
    /** Кнопки: столбец, строка, иконка (u, v в GuiButtons.png), подсказка. */
    private static final int[][] ICONS = {{0, 0, 0, 64}, {1, 0, 32, 64}, {0, 1, 96, 32}, {1, 1, 64, 32}, {2, 1, 32, 32}, {0, 2, 64, 64}, {1, 2, 96, 64}};
    private static final int[] TIPS = {6, 7, 228, 229, 230, 231, 232};
    private static final int ROW_Y = 22, ROW_H = 10;

    public CoverScreen(CoverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 107);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        g.item(new ItemStack(Covers.itemOf(menu.kind, menu.tier)), leftPos + 5, topPos + 4);
        if (!CoverMenu.iconButtons(menu.kind)) return;
        int v = menu.value();
        for (int b = 0; b < ICONS.length; b++) {
            int x = leftPos + SX + ICONS[b][0] * STEP, y = topPos + SY + ICONS[b][1] * STEP;
            boolean enabled = CoverMenu.clickable(v, b), over = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
            int bu = !enabled ? 128 : over ? 64 : 0;   // GT_GuiIcon BUTTON_DISABLED / BUTTON_HIGHLIGHT / BUTTON_NORMAL
            g.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, x, y, bu, 0, 16, 16, 32, 32, 256, 256);
            g.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, x, y, ICONS[b][2], ICONS[b][3], 16, 16, 32, 32, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 25, 8, 0xFF404040, false);
        if (CoverMenu.iconButtons(menu.kind)) {
            String[] rows = {"gui.iis.cover.io", CoverKind.msg(229), "gui.iis.cover.input"};
            for (int r = 0; r < rows.length; r++) g.text(font, Component.translatable(rows[r]), SX + STEP * 3, 3 + SY + STEP * r, 0xFF555555, false);
            return;
        }
        int mode = CoverMenu.modeOf(menu.kind, menu.value());
        for (int i = 0; i < CoverMenu.modeCount(menu.kind); i++) {
            int y = ROW_Y + i * ROW_H;
            boolean over = isHovering(8, y - 1, 160, ROW_H, mouseX, mouseY);
            int color = i == mode ? 0xFF1E7A1E : over ? 0xFF2050A0 : 0xFF555555;
            g.text(font, Component.literal(i == mode ? "▶ " : "  ").append(CoverMenu.modeName(menu.kind, i)), 10, y, color, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (!CoverMenu.iconButtons(menu.kind)) return;
        for (int b = 0; b < ICONS.length; b++) {
            if (isHovering(SX + ICONS[b][0] * STEP, SY + ICONS[b][1] * STEP, 16, 16, mouseX, mouseY)) {
                g.setTooltipForNextFrame(Component.translatable(CoverKind.msg(TIPS[b])), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        int button = -1;
        if (CoverMenu.iconButtons(menu.kind)) {
            for (int b = 0; b < ICONS.length; b++) {
                if (isHovering(SX + ICONS[b][0] * STEP, SY + ICONS[b][1] * STEP, 16, 16, mx, my) && CoverMenu.clickable(menu.value(), b)) button = b;
            }
        } else {
            for (int i = 0; i < CoverMenu.modeCount(menu.kind); i++) {
                if (isHovering(8, ROW_Y + i * ROW_H - 1, 160, ROW_H, mx, my)) button = i;
            }
        }
        if (button < 0) return super.mouseClicked(event, doubleClick);
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        mc.gameMode.handleInventoryButtonClick(menu.containerId, button);
        return true;
    }
}
