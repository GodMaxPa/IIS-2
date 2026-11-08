package ru.iis.crop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.iis.lv.item.BatteryItem;

/**
 * Анализатор культур IC2 (ItemCropnalyzer, D139): 100 000 EU, уровень 1. У IC2 — окно со слотами; здесь семена берутся из другой руки.
 * Цена уровней анализа как у IC2: 10, 90, 900, 9000 EU за мешочек; за одно использование — сколько хватит заряда.
 */
public class CropnalyzerItem extends BatteryItem {
    static final long[] COST = {10, 90, 900, 9000};

    public CropnalyzerItem(Properties properties) {
        super(100_000, 100, 1, properties);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        ItemStack seeds = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        CropSeedItem.Seed s = CropSeedItem.seed(seeds);
        if (s == null) {
            player.sendOverlayMessage(Component.translatable("crop.iis.cropnalyzer.no_seed"));
            return InteractionResult.CONSUME;
        }
        int scan = scan(seeds, self, player.getAbilities().instabuild);
        if (scan == s.scan() && scan < 4) {
            player.sendOverlayMessage(Component.translatable("crop.iis.cropnalyzer.no_charge", COST[scan] * seeds.getCount()));
            return InteractionResult.CONSUME;
        }
        sl.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /** Поднимает уровень анализа мешочка, пока хватает заряда; возвращает новый уровень. */
    public static int scan(ItemStack seeds, ItemStack analyzer, boolean free) {
        CropSeedItem.Seed s = CropSeedItem.seed(seeds);
        if (s == null) return 0;
        int lvl = s.scan();
        while (lvl < 4) {
            long c = COST[lvl] * seeds.getCount();
            if (!free) {
                if (charge(analyzer) < c) break;
                setCharge(analyzer, charge(analyzer) - c);
            }
            lvl++;
        }
        if (lvl != s.scan()) seeds.set(CropContent.SEED.get(), s.withScan(lvl));
        return lvl;
    }
}
