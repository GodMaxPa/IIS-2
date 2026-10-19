package ru.iis.lv.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Общие кусочки GUI LV: значок жидкости (ведро) и подписи. */
final class LvScreens {
    private LvScreens() {}

    static Fluid fluid(int id) {
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    static ItemStack icon(Fluid f) {
        if (f == Fluids.EMPTY) return ItemStack.EMPTY;
        var bucket = f.getBucket();
        return bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
    }

    static Component fluidName(Fluid f) {
        return f == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : f.getFluidType().getDescription();
    }

    static Component fluidLine(Fluid f, int amount, int capacity) {
        return Component.translatable("gui.iis.fluid", fluidName(f), amount, capacity);
    }

    static Component euLine(int stored, int capacity) {
        return Component.translatable("gui.iis.energy").append(": ").append(Component.translatable("gui.iis.eu", stored, capacity));
    }

    static void fluidIcon(GuiGraphicsExtractor g, int x, int y, Fluid f) {
        ItemStack s = icon(f);
        if (!s.isEmpty()) g.item(s, x, y);
    }
}
