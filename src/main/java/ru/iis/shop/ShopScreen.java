package ru.iis.shop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import ru.iis.net.IISNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Витрина магазина наград: поиск, сетка товаров, подсказка с ценой. */
public class ShopScreen extends AbstractContainerScreen<ShopContent.ShopMenu> {
    private static final int COLS = 9, ROWS = 6, CELL = 18, GRID_X = 8, GRID_Y = 34;
    private static final int FACE = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, SLOT = 0xFF8B8B8B, EDGE = 0xFF000000, TEXT = 0xFF303030;
    private final List<String> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;

    public ShopScreen(ShopContent.ShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 90, topPos + 5, 78, 12, Component.translatable("gui.iis.shop.search"));
        search.setHint(Component.translatable("gui.iis.shop.search"));
        search.setResponder(s -> refresh());
        addRenderableWidget(search);
        refresh();
    }

    private void refresh() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        shown.clear();
        for (String id : menu.seen) {
            if (!ShopContent.prices().containsKey(id)) continue;
            ItemStack s = stack(id);
            if (q.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) shown.add(id);
        }
        // дешёвое сверху, внутри одной цены по имени
        shown.sort((a, b) -> {
            int t = Integer.compare(ShopContent.prices().get(a).tier(), ShopContent.prices().get(b).tier());
            return t != 0 ? t : stack(a).getHoverName().getString().compareTo(stack(b).getHoverName().getString());
        });
        scroll = 0;
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private int at(double mx, double my) {
        int c = (int) Math.floor((mx - leftPos - GRID_X) / CELL), r = (int) Math.floor((my - topPos - GRID_Y) / CELL);
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return -1;
        int i = (r + scroll) * COLS + c;
        return i < shown.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = at(event.x(), event.y());
        if (i >= 0) {
            ClientPacketDistributor.sendToServer(new IISNetwork.ShopBuy(shown.get(i), minecraft.hasShiftDown() ? 8 : 1));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int rows = (shown.size() + COLS - 1) / COLS;
        scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // E не закрывает окно, пока печатаем в поиске
        if (search != null && search.isFocused() && event.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        g.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, DARK);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, FACE);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = x + GRID_X + c * CELL, sy = y + GRID_Y + r * CELL;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
                g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
                g.fill(sx, sy, sx + 16, sy + 16, SLOT);
                int i = (r + scroll) * COLS + c;
                if (i < shown.size()) g.item(stack(shown.get(i)), sx, sy);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, TEXT, false);
        long wallet = ShopLogic.total(ShopContent.countCredits(minecraft.player));
        g.text(font, Component.translatable("gui.iis.shop.wallet", wallet), 8, 21, TEXT, false);
        if (shown.isEmpty()) {
            int yy = GRID_Y + 40;
            for (var line : font.split(Component.translatable("gui.iis.shop.empty"), imageWidth - 24)) {
                g.text(font, line, 12, yy, 0xFFFFFFFF, true);
                yy += 10;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int i = at(mouseX, mouseY);
        if (i < 0) return;
        String id = shown.get(i);
        ShopContent.Price price = ShopContent.prices().get(id);
        ItemStack coin = new ItemStack(ShopContent.creditItems().get(price.tier()));
        ItemStack s = stack(id);
        g.setComponentTooltipForNextFrame(font, List.of(s.getHoverName(),
                Component.translatable("gui.iis.shop.gives", price.give()),
                Component.translatable("gui.iis.shop.price", coin.getHoverName(), 1),
                Component.translatable("gui.iis.shop.help").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
    }
}
