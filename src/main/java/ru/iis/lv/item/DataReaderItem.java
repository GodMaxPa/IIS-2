package ru.iis.lv.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.iis.lv.LvContent;

/**
 * Считыватель данных (GT Tool_DataReader_MV, _EV, Behaviour_DataReader, D122). В GT — окно со слотом флешки и анализ со временем:
 * 1000 / 2^(уровень − 2) тиков по 8 × 4^(уровень − 2) EU/t. Здесь флешка во второй руке, та же энергия снимается сразу, отчёт идёт в чат.
 */
public class DataReaderItem extends BatteryItem {
    public DataReaderItem(long capacity, long transfer, int tier, Properties properties) {
        super(capacity, transfer, tier, properties);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    /** Энергия анализа: тики × EU/t из Behaviour_DataReader. */
    public long analyzeCost() {
        return (1000L >> (tier - 2)) * (8L << (2 * (tier - 2)));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack reader = player.getItemInHand(hand), stick = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        Prospection p = stick.get(LvContent.PROSPECTION.get());
        if (p == null) {
            player.sendSystemMessage(Component.translatable("item.iis.data_reader.no_data"));
            return InteractionResult.CONSUME;
        }
        if (!p.analyzed()) {
            if (charge(reader) < analyzeCost()) {
                player.sendSystemMessage(Component.translatable("item.iis.data_reader.no_energy"));
                return InteractionResult.CONSUME;
            }
            setCharge(reader, charge(reader) - analyzeCost());
            p = p.withAnalyzed();
            stick.set(LvContent.PROSPECTION.get(), p);
        }
        for (Component c : p.report()) player.sendSystemMessage(c);
        return InteractionResult.CONSUME;
    }
}
