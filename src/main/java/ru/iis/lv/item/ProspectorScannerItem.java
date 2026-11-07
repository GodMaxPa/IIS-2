package ru.iis.lv.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.fluids.FluidStack;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.world.UndergroundFluids;
import ru.iis.world.ore.OreBlocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Сканер старателя LuV, ZPM, UV (GT_Scanner_Mod ИИС 1, D133). Исходников мода в сборке нет, известно из квеста: ищет рудные жилы и
 * жидкости под коренной породой, режим меняется Shift+ПКМ по воздуху. Обзор и цена — предложение ИИ: (2r+1)² чанков, 16 × V уровня за скан.
 * Читает только загруженные чанки: незагруженные не грузит.
 */
public class ProspectorScannerItem extends BatteryItem {
    public final int radius;

    public ProspectorScannerItem(int radius, long capacity, long transfer, int tier, Properties properties) {
        super(capacity, transfer, tier, properties);
        this.radius = radius;
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public long cost() {
        return BlastLogic.V[tier] * 16;
    }

    public static boolean fluidMode(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("FluidMode", false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            boolean fluids = !fluidMode(stack);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("FluidMode", fluids));
            player.sendOverlayMessage(Component.translatable("item.iis.prospector.mode", Component.translatable(fluids ? "item.iis.prospector.mode.fluids" : "item.iis.prospector.mode.ores")));
            return InteractionResult.CONSUME;
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && charge(stack) < cost()) {
            player.sendOverlayMessage(Component.translatable("item.iis.prospector.no_charge", cost()));
            return InteractionResult.CONSUME;
        }
        if (!free) setCharge(stack, charge(stack) - cost());
        List<Component> lines = fluidMode(stack) ? scanFluids(sl, player.blockPosition(), radius) : scanOres(sl, player.blockPosition(), radius);
        lines.forEach(player::sendSystemMessage);
        sl.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
        return InteractionResult.CONSUME;
    }

    private record Count(String name, int[] total, Map<Long, Integer> perChunk) {}

    /** Руды жил в квадрате чанков: сколько блоков и в каком чанке их больше всего. Малые руды не считаются. */
    public static List<Component> scanOres(ServerLevel sl, BlockPos at, int r) {
        ChunkPos c = ChunkPos.containing(at);
        Map<String, Count> found = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = sl.getChunkSource().getChunkNow(c.x() + dx, c.z() + dz);
                if (chunk == null) continue;
                long key = ChunkPos.pack(c.x() + dx, c.z() + dz);
                for (LevelChunkSection sec : chunk.getSections()) {
                    if (sec.hasOnlyAir() || !sec.getStates().maybeHas(s -> OreBlocks.isOre(s.getBlock()) && !OreBlocks.isSmallOre(s.getBlock()))) continue;
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        String k = Prospection.oreKey(s);
                        if (k == null || !OreBlocks.isOre(s.getBlock())) continue;
                        Count n = found.computeIfAbsent(k, kk -> new Count(kk, new int[1], new HashMap<>()));
                        n.total[0]++;
                        n.perChunk.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        int side = 2 * r + 1;
        List<Component> l = new java.util.ArrayList<>();
        l.add(Component.translatable("item.iis.prospector.ores", side, side).withStyle(ChatFormatting.GOLD));
        if (found.isEmpty()) l.add(Component.translatable("item.iis.prospector.nothing"));
        found.values().stream().sorted((a, b) -> b.total[0] - a.total[0]).limit(12).forEach(n -> {
            long best = n.perChunk.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            l.add(Component.translatable("item.iis.prospector.ore", Component.translatable(n.name), n.total[0], ChunkPos.getX(best) * 16 + 8, ChunkPos.getZ(best) * 16 + 8));
        });
        return l;
    }

    /** Жидкости под коренной породой по чанкам (как у буровой): всего литров и самый богатый чанк. */
    public static List<Component> scanFluids(ServerLevel sl, BlockPos at, int r) {
        ChunkPos c = ChunkPos.containing(at);
        Map<net.minecraft.world.level.material.Fluid, long[]> found = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                UndergroundFluids.Sample s = UndergroundFluids.read(sl, new ChunkPos(c.x() + dx, c.z() + dz));
                if (s == null || s.amount() <= 0) continue;
                long[] v = found.computeIfAbsent(s.fluid(), f -> new long[]{0, 0, -1, 0});
                v[0] += s.amount();
                v[1]++;
                if (s.amount() > v[2]) {
                    v[2] = s.amount();
                    v[3] = ChunkPos.pack(c.x() + dx, c.z() + dz);
                }
            }
        }
        int side = 2 * r + 1;
        List<Component> l = new java.util.ArrayList<>();
        l.add(Component.translatable("item.iis.prospector.fluids", side, side).withStyle(ChatFormatting.AQUA));
        if (found.isEmpty()) l.add(Component.translatable("item.iis.prospector.nothing"));
        found.forEach((f, v) -> l.add(Component.translatable("item.iis.prospector.fluid", new FluidStack(f, 1).getHoverName(), v[0], v[1], ChunkPos.getX(v[3]) * 16 + 8, ChunkPos.getZ(v[3]) * 16 + 8)));
        return l;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        int side = 2 * radius + 1;
        builder.accept(Component.translatable("item.iis.prospector.radius", side, side, cost()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.iis.prospector.mode", Component.translatable(fluidMode(stack) ? "item.iis.prospector.mode.fluids" : "item.iis.prospector.mode.ores")).withStyle(ChatFormatting.GRAY));
    }
}
