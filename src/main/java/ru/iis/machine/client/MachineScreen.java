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

    protected MachineScreen(M menu, Inventory inventory, Component title, String gui) {
        super(menu, inventory, title, 176, 166);
        this.texture = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/gui/" + gui + ".png");
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
        g.text(font, title, 8, 4, 0xFF404040, false);
    }

    protected boolean hovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return isHovering(x, y, w, h, mouseX, mouseY);
    }
}
