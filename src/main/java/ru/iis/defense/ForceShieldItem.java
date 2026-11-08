package ru.iis.defense;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import ru.iis.lv.item.BatteryItem;

/** Силовой щит IV (D140): держит любой урон спереди и вместо прочности тратит заряд — 5000 EU за единицу урона; без заряда не держит. */
public class ForceShieldItem extends BatteryItem {
    public static final long EU_PER_DAMAGE = 5000;

    public ForceShieldItem(Properties properties) {
        super(10_000_000L, 8192, 5, properties);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public static void onBlock(LivingShieldBlockEvent event) {
        LivingEntity e = event.getEntity();
        ItemStack s = e.getUseItem();
        if (!(s.getItem() instanceof ForceShieldItem) || !event.getBlocked()) return;
        event.setShieldDamage(0);
        if (e instanceof Player p && p.getAbilities().instabuild) return;
        float blocked = event.getBlockedDamage();
        long need = (long) Math.ceil(blocked * EU_PER_DAMAGE), have = charge(s);
        if (have >= need) {
            setCharge(s, have - need);
            return;
        }
        float can = (float) have / EU_PER_DAMAGE;
        setCharge(s, 0);
        if (can <= 0.01F) event.setBlocked(false);
        else event.setBlockedDamage(can);
    }
}
