package ru.iis.lv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.multi.block.MultiblockControllerBlockEntity;
import ru.iis.pollution.Pollution;
import ru.iis.world.UndergroundFluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Портативный сканер (GT Tool_Scanner, Behaviour_Scanner, D122): ПКМ по блоку пишет в чат всё, что о нём известно.
 * Нужен заряд 20000 EU, цена — по собранным сведениям (500 бак, 400 ход работы, 300 энергия, 400 структура). Режим «только многоблочные» (Shift + ПКМ у GT) не перенесён.
 */
public class PortableScannerItem extends BatteryItem {
    public static final long NEED = 20000;

    public PortableScannerItem(long capacity, long transfer, int tier, Properties properties) {
        super(capacity, transfer, tier, properties);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public record Scan(List<Component> lines, long cost) {}

    /** Что сканер пишет про блок. */
    public static Scan scan(ServerLevel sl, BlockPos pos) {
        List<Component> l = new ArrayList<>();
        long cost = 0;
        BlockState s = sl.getBlockState(pos);
        l.add(Component.translatable("item.iis.portable_scanner.pos", pos.getX(), pos.getY(), pos.getZ(), sl.dimension().identifier().toString()));
        l.add(Component.translatable("item.iis.portable_scanner.name", s.getBlock().getName()));
        l.add(Component.translatable("item.iis.portable_scanner.hard", String.format(java.util.Locale.ROOT, "%.1f", s.getDestroySpeed(sl, pos)), String.format(java.util.Locale.ROOT, "%.1f", s.getBlock().getExplosionResistance())));
        BlockEntity be = sl.getBlockEntity(pos);
        ResourceHandler<FluidResource> tanks = sl.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        if (tanks != null) {
            cost += 500;
            for (int i = 0; i < tanks.size(); i++) {
                FluidResource r = tanks.getResource(i);
                Component name = r.isEmpty() ? Component.translatable("gui.iis.empty") : new FluidStack(r.getFluid(), 1).getHoverName();
                l.add(Component.translatable("item.iis.portable_scanner.tank", i, tanks.getAmountAsLong(i), tanks.getCapacityAsLong(i, r), name));
            }
        }
        if (be instanceof ElectricMachineBlockEntity m && m.logic().maxProgress > 0) {
            cost += 400;
            l.add(Component.translatable("item.iis.portable_scanner.progress", m.logic().progress, m.logic().maxProgress));
        }
        if (be instanceof ElectricBlockEntity e) {
            cost += 300;
            l.add(Component.translatable("item.iis.portable_scanner.energy", e.energy.stored, e.energy.capacity));
        }
        if (be instanceof MultiblockControllerBlockEntity c) {
            cost += 400;
            l.add(Component.translatable(c.formed() ? "item.iis.portable_scanner.formed" : "item.iis.portable_scanner.not_formed"));
        }
        UndergroundFluids.Sample f = UndergroundFluids.read(sl, ChunkPos.containing(pos));
        if (f != null) l.add(Component.translatable("item.iis.portable_scanner.underground", new FluidStack(f.fluid(), 1).getHoverName(), f.amount()));
        int pollution = Pollution.get(sl, pos);
        l.add(pollution > 0 ? Component.translatable("item.iis.portable_scanner.pollution", pollution) : Component.translatable("item.iis.portable_scanner.clean"));
        return new Scan(l, cost);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (charge(stack) < NEED) {
            player.sendSystemMessage(Component.translatable("item.iis.data_reader.no_energy"));
            return InteractionResult.CONSUME;
        }
        Scan r = scan(sl, ctx.getClickedPos());
        setCharge(stack, charge(stack) - r.cost());
        for (Component c : r.lines()) player.sendSystemMessage(c);
        return InteractionResult.CONSUME;
    }
}
