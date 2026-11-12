package ru.iis.bio.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Туши (D70, решение владельца Р4): взрослое животное, убитое ножом Био, оставляет вместо обычной добычи тушу своего размера —
 * малую, среднюю, большую — или гнилую (нежить). В туше записано, что ещё с неё снимается (кожа, шерсть, перо). Детёныши
 * туш не дают (их обычная добыча не меняется). Разделка — на площадке ножом.
 */
public final class Butchery {
    private Butchery() {}

    private enum Size { SMALL, MEDIUM, LARGE, ROTTEN }

    private static final Map<EntityType<?>, Size> SIZE = Map.ofEntries(
            Map.entry(EntityType.CHICKEN, Size.SMALL), Map.entry(EntityType.RABBIT, Size.SMALL),
            Map.entry(EntityType.PIG, Size.MEDIUM), Map.entry(EntityType.SHEEP, Size.MEDIUM), Map.entry(EntityType.GOAT, Size.MEDIUM),
            Map.entry(EntityType.COW, Size.LARGE), Map.entry(EntityType.MOOSHROOM, Size.LARGE), Map.entry(EntityType.HORSE, Size.LARGE),
            Map.entry(EntityType.DONKEY, Size.LARGE), Map.entry(EntityType.MULE, Size.LARGE),
            Map.entry(EntityType.ZOMBIE, Size.ROTTEN), Map.entry(EntityType.HUSK, Size.ROTTEN), Map.entry(EntityType.DROWNED, Size.ROTTEN),
            Map.entry(EntityType.ZOMBIE_HORSE, Size.ROTTEN));

    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ItemStack knife = player.getMainHandItem();
        if (!knife.is(BioItems.KNIFE.get()) || mob.isBaby()) return;
        ItemStack carcass = carcassFor(mob);
        if (carcass == null) return;
        event.getDrops().clear();
        event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.3, mob.getZ(), carcass));
        knife.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
    }

    /** Туша для этого существа или null, если оно не разделывается. */
    public static @Nullable ItemStack carcassFor(LivingEntity mob) {
        Size size = SIZE.get(mob.getType());
        if (size == null) return null;
        Item item = switch (size) {
            case SMALL -> BioItems.CARCASS_SMALL.get();
            case MEDIUM -> BioItems.CARCASS_MEDIUM.get();
            case LARGE -> BioItems.CARCASS_LARGE.get();
            case ROTTEN -> BioItems.CARCASS_ROTTEN.get();
        };
        // D148: рабочая мясистая линия свиньи даёт свою тушу (больше мясного и соединительного сырья)
        if (ru.iis.bio.mutant.Mutants.working(mob) == ru.iis.bio.mutant.Mutants.Profile.PIG_FLESHY) item = BioItems.CARCASS_FLESHY.get();
        ItemStack stack = new ItemStack(item);
        List<ItemStack> extras = new ArrayList<>();
        EntityType<?> t = mob.getType();
        if (t == EntityType.CHICKEN) extras.add(new ItemStack(Items.FEATHER));
        if (t == EntityType.RABBIT) extras.add(new ItemStack(Items.RABBIT_HIDE));
        if (size == Size.LARGE) extras.add(new ItemStack(Items.LEATHER));
        if (mob instanceof Sheep sheep && !sheep.isSheared()) {
            Item wool = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(sheep.getColor().getSerializedName() + "_wool"));
            extras.add(new ItemStack(wool == Items.AIR ? Items.WHITE_WOOL : wool));
        }
        if (!extras.isEmpty()) stack.set(BioItems.CARCASS_EXTRAS.get(), extras);
        return stack;
    }
}
