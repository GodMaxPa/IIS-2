package ru.iis.machine.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import ru.iis.IIS;
import ru.iis.machine.menu.MachineMenu;

/** GUI машины на текстуре GT5 (176×166, служебные элементы в правой части текстуры 256×256). */
public abstract class MachineScreen<M extends MachineMenu> extends AbstractContainerScreen<M> {
    protected final Identifier texture;
    /** GT пишет имя тёмным; на тёмных панелях стали и кирпича его не прочесть, там оно светлое. */
    protected int titleColor;

    protected MachineScreen(M menu, Inventory inventory, Component title, String gui) {
        super(menu, inventory, title, 176, 166);
        this.texture = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/gui/" + gui + ".png");
        this.titleColor = gui.startsWith("steel") || gui.startsWith("brick") ? 0xFFE8E4DC : 0xFF404040;
    }

    public Identifier textureId() {
        return texture;
    }

    protected void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos + x, topPos + y, u, v, w, h, 256, 256);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        extractGauges(g);
    }

    protected abstract void extractGauges(GuiGraphicsExtractor g);

    /** GT: имя машины в (8,4), подписи инвентаря игрока нет. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawTitle(g, 8, 4, titleColor, titleRoom());
    }

    /** Сколько места у имени: до первого слота в верхней полосе окна. */
    protected int titleRoom() {
        int room = imageWidth - 16;
        for (net.minecraft.world.inventory.Slot s : menu.slots) {
            if (s.y < 14 && s.x > 8 && s.isActive()) room = Math.min(room, s.x - 11);
        }
        return Math.max(24, room);
    }

    /** Имя в отведённое место: узкое — в несколько строк, широкое — с многоточием (полное имя в подсказке). */
    protected void drawTitle(GuiGraphicsExtractor g, int x, int y, int color, int room) {
        if (font.width(title) <= room) {
            g.text(font, title, x, y, color, false);
            return;
        }
        if (room < 120) {
            int line = 0;
            for (var seq : font.split(title, room)) g.text(font, seq, x, y + 9 * line++, color, false);
            return;
        }
        g.text(font, clip(title.getString(), room), x, y, color, false);
    }

    /** Строка, обрезанная по ширине. */
    protected String clip(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    /** Рамка слота там, где на текстуре GT её нет. */
    protected void slotFrame(GuiGraphicsExtractor g, int x, int y) {
        int l = leftPos + x, t = topPos + y;
        g.fill(l, t, l + 18, t + 18, 0xFF8B8B8B);
        g.fill(l, t, l + 17, t + 1, 0xFF373737);
        g.fill(l, t, l + 1, t + 17, 0xFF373737);
        g.fill(l + 1, t + 17, l + 18, t + 18, 0xFFFFFFFF);
        g.fill(l + 17, t + 1, l + 18, t + 18, 0xFFFFFFFF);
    }

    protected boolean hovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return isHovering(x, y, w, h, mouseX, mouseY);
    }
}
