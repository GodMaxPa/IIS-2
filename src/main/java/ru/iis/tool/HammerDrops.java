package ru.iis.tool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.iis.IIS;
import ru.iis.core.item.MaterialItems;
import ru.iis.core.material.IISMaterial;
import ru.iis.world.ore.OreBlocks;

/**
 * Молот GT дробит руду при добыче (GT_Tool_HardHammer#convertBlockDrops):
 * вместо блока руды выпадает дроблёная руда, количество = множитель руды материала (x2 для незерака/эндерняка, "богатые" руды).
 * Каждое такое превращение дополнительно стоит молоту прочности (GT5 getToolDamagePerDropConversion = 200 -> 4).
 */
public final class HammerDrops {
    private HammerDrops() {}

    static final int CONVERSION_DAMAGE = 200 / 50;

    public static void onBlockDrops(BlockDropsEvent event) {
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof GtToolItem gt) || !gt.toolType().equals("hammer")) return;
        int conversions = 0;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!key.getNamespace().equals(IIS.MODID)) continue;
            String path = key.getPath();
            int idx = path.indexOf("ore_");
            if (idx < 0) continue;
            String materialId = path.substring(idx + 4);
            IISMaterial m = OreBlocks.material(materialId);
            if (m == null) continue;
            var crushed = MaterialItems.find("crushed/" + materialId);
            if (crushed.isEmpty()) continue;
            boolean rich = path.startsWith("nether_") || path.startsWith("end_");
            int count = stack.getCount() * Math.max(1, m.oreMultiplier()) * (rich ? 2 : 1);
            drop.setItem(new ItemStack(crushed.get(), count));
            conversions++;
        }
        // event.getTool() — копия; урон наносим настоящему предмету в руке
        if (conversions > 0 && event.getBreaker() instanceof LivingEntity living
                && living.getMainHandItem().getItem() == tool.getItem()) {
            living.getMainHandItem().hurtAndBreak(CONVERSION_DAMAGE * conversions, living, EquipmentSlot.MAINHAND);
        }
    }
}
