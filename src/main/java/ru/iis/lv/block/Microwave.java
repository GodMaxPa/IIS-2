package ru.iis.lv.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import ru.iis.core.material.IISMaterial;
import ru.iis.core.material.MaterialData;

/**
 * Опасности микроволновки GT (GT_Recipe_Map_Microwave, D109): от незерита, TNT, яиц, фейерверков, огненного заряда, металлов и взрывчатых материалов — взрыв;
 * от горючих материалов и топлива — пожар. Материал предмета GT определяется по концу его id.
 */
final class Microwave {
    private Microwave() {}

    enum Hazard { NONE, BURN, EXPLODE }

    static Hazard hazard(ServerLevel sl, ItemStack s) {
        if (s.isEmpty()) return Hazard.NONE;
        if (s.is(Items.NETHERRACK) || s.is(Items.TNT) || s.is(Items.EGG) || s.is(Items.FIREWORK_STAR) || s.is(Items.FIREWORK_ROCKET) || s.is(Items.FIRE_CHARGE)) return Hazard.EXPLODE;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id.getNamespace().equals(ru.iis.IIS.MODID)) {
            IISMaterial best = null;
            for (IISMaterial m : MaterialData.materials()) {
                if (id.getPath().endsWith("_" + m.id()) && (best == null || m.id().length() > best.id().length())) best = m;
            }
            if (best != null) {
                if (best.has("METAL") || best.has("EXPLOSIVE")) return Hazard.EXPLODE;
                if (best.has("FLAMMABLE")) return Hazard.BURN;
            }
        } else if (s.is(Tags.Items.INGOTS) || s.is(Tags.Items.NUGGETS) || s.is(Tags.Items.RAW_MATERIALS) || s.is(Tags.Items.ORES) || s.is(Tags.Items.STORAGE_BLOCKS_IRON) || s.is(Tags.Items.STORAGE_BLOCKS_GOLD)) {
            return Hazard.EXPLODE;
        }
        return sl.fuelValues().burnDuration(s) > 0 ? Hazard.BURN : Hazard.NONE;
    }
}
