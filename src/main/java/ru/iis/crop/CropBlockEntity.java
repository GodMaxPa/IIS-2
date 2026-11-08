package ru.iis.crop;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.world.ore.OreBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Грядка IC2 (TileEntityCrop, D139). Логика IC2 закрыта и восстановлена по памяти: раз в 256 тиков культура набирает очки роста
 * 3 + random(7) + рост, умноженные на условия (влажность + питание + воздух) × 5 против потребности (уровень − 1) × 4 + рост + урожайность + стойкость;
 * при сильной нехватке может погибнуть. Пустая грядка зарастает сорняком (1 из 100), двойная скрещивает соседей. Культуры GT — по GT_BaseCrop.
 */
public class CropBlockEntity extends BlockEntity {
    public static final int TICK_RATE = 256;
    static final TagKey<Biome> SWAMP = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_swamp"));
    static final TagKey<Biome> MUSHROOM = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_mushroom"));
    static final TagKey<Biome> PLAINS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_plains"));

    public int growth, gain, resistance, scan, growthPoints;
    public int nutrients, water, weedEx;
    public int humidity, soil, air;
    private int ticker = -1;

    public CropBlockEntity(BlockPos pos, BlockState state) {
        super(CropContent.CROP_BE.get(), pos, state);
    }

    // --- состояние ---------------------------------------------------------------------------------------------------------
    public @Nullable CropCard card() {
        int c = getBlockState().getValue(CropBlock.CROP);
        return c >= 2 && c - 2 < CropCard.ALL.size() ? CropCard.ALL.get(c - 2) : null;
    }

    public boolean cross() {
        return getBlockState().getValue(CropBlock.CROP) == 1;
    }

    public int size() {
        return getBlockState().getValue(CropBlock.SIZE);
    }

    private void setState(int crop, int size) {
        if (level == null) return;
        BlockState s = getBlockState().setValue(CropBlock.CROP, crop).setValue(CropBlock.SIZE, Math.max(0, Math.min(7, size)));
        level.setBlock(worldPosition, s, Block.UPDATE_ALL);
        setChanged();
    }

    public void setCrop(CropCard card, int size) {
        setState(card.index() + 2, size);
    }

    private void setSize(int size) {
        CropCard c = card();
        if (c != null) setCrop(c, size);
    }

    /** TileEntityCrop#reset: культура исчезает, запасы удобрения, воды и гербицида остаются. */
    public void reset() {
        growth = gain = resistance = scan = growthPoints = 0;
        setState(0, 0);
    }

    public void makeCross() {
        setState(1, 0);
    }

    /** Посадка мешочка семян: только на пустую (не двойную) грядку. */
    public boolean plant(CropSeedItem.Seed seed) {
        CropCard c = seed.card();
        if (c == null || card() != null || cross()) return false;
        growth = clamp(seed.growth());
        gain = clamp(seed.gain());
        resistance = clamp(seed.resistance());
        scan = seed.scan();
        growthPoints = 0;
        setCrop(c, 1);
        return true;
    }

    /** IC2 базовые семена (Crops#registerBaseSeed): свойства 1/1/1. */
    public boolean plantBase(ItemStack held) {
        if (card() != null || cross()) return false;
        CropCard c = baseSeedFor(held);
        if (c == null) return false;
        held.shrink(c.baseSeedCount());
        growth = gain = resistance = 1;
        scan = 0;
        growthPoints = 0;
        setCrop(c, 1);
        return true;
    }

    public static @Nullable CropCard baseSeedFor(ItemStack held) {
        if (held.isEmpty()) return null;
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        for (CropCard c : CropCard.ALL) {
            if (id.equals(c.baseSeed()) && held.getCount() >= c.baseSeedCount()) return c;
        }
        return null;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(31, v));
    }

    // --- работа ---------------------------------------------------------------------------------------------------------------
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticker < 0) ticker = sl.getRandom().nextInt(TICK_RATE);
        if (++ticker % TICK_RATE != 0) return;
        tick(sl);
    }

    /** TileEntityCrop#tick: одно действие грядки (раз в 256 тиков). */
    public void tick(ServerLevel sl) {
        RandomSource r = sl.getRandom();
        updateTerrain(sl);
        CropCard c = card();
        if (c == null) {
            if (cross() && attemptCrossing(sl)) return;
            if (r.nextInt(100) != 0 || weedEx > 0) {
                if (weedEx > 0 && r.nextInt(10) == 0) weedEx--;
                setChanged();
                return;
            }
            growth = gain = resistance = scan = growthPoints = 0;
            setCrop(CropCard.weed(), 1);
            return;
        }
        if (canGrow(sl, c)) {
            growthPoints += growthRate(r, c);
            if (card() == null) return;   // погибла
            if (growthPoints >= c.growthDuration()) {
                growthPoints = 0;
                setSize(size() + 1);
            }
        }
        if (nutrients > 0) nutrients--;
        if (water > 0) water--;
        if (isWeed(c) && r.nextInt(50) - growth <= 2) generateWeed(sl);
        setChanged();
    }

    /** CropCard#canGrow; GT_BaseCrop: на последний шаг нужна руда или блок материала под грядкой (CropNeedBlockBelow=true в ИИС 1). */
    public boolean canGrow(Level level, CropCard c) {
        int s = size();
        if (s >= c.maxSize()) return false;
        if (c.block() != null && s == c.maxSize() - 1) return blockBelow(level, worldPosition, c.block());
        return true;
    }

    /** CropCard#isWeed: сорняк или культура с ростом от 24 расползается. */
    public boolean isWeed(CropCard c) {
        return size() >= 2 && (c.isWeedCard() || growth >= 24);
    }

    /** TileEntityCrop#calcGrowthRate. */
    int growthRate(RandomSource r, CropCard c) {
        int base = 3 + r.nextInt(7) + growth;
        int need = Math.max(0, (c.tier() - 1) * 4 + growth + gain + resistance);
        int have = (humidity + soil + air) * 5;
        if (have >= need) {
            base = base * (100 + (have - need)) / 100;
        } else {
            int neg = (need - have) * 4;
            if (neg > 100 && r.nextInt(32) > resistance) {
                reset();
                return 0;
            }
            base = Math.max(0, base * (100 - neg) / 100);
        }
        return base;
    }

    /** TileEntityCrop#updateTerrainHumidity / Nutrients / AirQuality. Влажность по биомам — Biomes.zs ИИС 1, питание — IC2 по умолчанию. */
    public void updateTerrain(Level level) {
        Holder<Biome> biome = level.getBiome(worldPosition);
        BlockState below = level.getBlockState(worldPosition.below());
        int h = humidityBonus(biome);
        if (below.getBlock() instanceof FarmlandBlock && below.getValue(FarmlandBlock.MOISTURE) >= FarmlandBlock.MAX_MOISTURE) h += 2;
        if (water >= 5) h += 2;
        h += (water + 24) / 25;
        humidity = h;
        int n = nutrientBonus(biome);
        for (int i = 2; i < 5 && level.getBlockState(worldPosition.below(i)).is(BlockTags.DIRT); i++) n++;
        n += (nutrients + 19) / 20;
        soil = n;
        int a = Math.max(0, Math.min(4, (worldPosition.getY() - 64) / 15));
        int fresh = 9;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos p = worldPosition.offset(x, 0, z);
            if (level.getBlockState(p).isRedstoneConductor(level, p)) fresh--;
        }
        a += fresh / 2;
        if (level.canSeeSky(worldPosition.above())) a += 2;
        air = a;
    }

    static int humidityBonus(Holder<Biome> b) {
        int v = 0;
        if (b.is(BiomeTags.IS_JUNGLE)) v += 7;
        if (b.is(SWAMP)) v += 10;
        if (b.is(MUSHROOM)) v += 5;
        if (b.is(BiomeTags.IS_FOREST)) v += 5;
        if (b.is(BiomeTags.IS_RIVER)) v += 10;
        if (b.is(PLAINS)) v += 2;
        return v;
    }

    static int nutrientBonus(Holder<Biome> b) {
        int v = 0;
        if (b.is(BiomeTags.IS_JUNGLE)) v += 10;
        if (b.is(SWAMP)) v += 10;
        if (b.is(MUSHROOM)) v += 5;
        if (b.is(BiomeTags.IS_FOREST)) v += 5;
        if (b.is(BiomeTags.IS_RIVER)) v += 2;
        return v;
    }

    /** GT_BaseCrop#isBlockBelow: на глубине 1–4 под грядкой руда (в том числе малая) или блок материала; чужая руда GT обрывает поиск. */
    public static boolean blockBelow(Level level, BlockPos pos, String material) {
        TagKey<Block> ores = BlockTags.create(Identifier.fromNamespaceAndPath("c", "ores/" + material));
        TagKey<Block> blocks = BlockTags.create(Identifier.fromNamespaceAndPath("c", "storage_blocks/" + material));
        for (int i = 1; i < 5; i++) {
            BlockState s = level.getBlockState(pos.below(i));
            String ore = oreMaterial(s.getBlock());
            if (ore != null) return ore.equals(material);
            if (s.is(ores) || s.is(blocks)) return true;
        }
        return false;
    }

    private static Map<Block, String> ORE_MATERIAL;

    private static @Nullable String oreMaterial(Block b) {
        if (ORE_MATERIAL == null) {
            Map<Block, String> m = new HashMap<>();
            OreBlocks.all().forEach((mat, hosts) -> hosts.values().forEach(d -> m.put(d.get(), mat)));
            OreBlocks.allSmall().forEach((mat, hosts) -> hosts.values().forEach(d -> m.put(d.get(), mat)));
            ORE_MATERIAL = m;
        }
        return ORE_MATERIAL.get(b);
    }

    private List<CropBlockEntity> neighbours() {
        List<CropBlockEntity> l = new ArrayList<>();
        if (level == null) return l;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof CropBlockEntity te) l.add(te);
        }
        return l;
    }

    /** TileEntityCrop#attemptCrossing: шанс 1/3; нужны хотя бы две созревшие соседние культуры, каждая соглашается по своим свойствам. */
    boolean attemptCrossing(ServerLevel sl) {
        RandomSource r = sl.getRandom();
        if (r.nextInt(3) != 0) return false;
        List<CropBlockEntity> parents = new ArrayList<>();
        for (CropBlockEntity te : neighbours()) {
            CropCard pc = te.card();
            if (pc == null || !pc.canCross(te.size())) continue;
            int base = 4;
            if (te.growth >= 16) base++;
            if (te.growth >= 30) base++;
            if (te.resistance >= 28) base += 27 - te.resistance;
            if (base >= r.nextInt(20)) parents.add(te);
        }
        if (parents.size() < 2) return false;
        CropCard child = pickChild(r, parents);
        if (child == null) return false;
        int count = parents.size(), g = 0, ga = 0, re = 0;
        for (CropBlockEntity p : parents) {
            g += p.growth;
            ga += p.gain;
            re += p.resistance;
        }
        growth = clamp(g / count + r.nextInt(1 + 2 * count) - count);
        gain = clamp(ga / count + r.nextInt(1 + 2 * count) - count);
        resistance = clamp(re / count + r.nextInt(1 + 2 * count) - count);
        scan = 0;
        growthPoints = 0;
        setCrop(child, 1);
        return true;
    }

    /** Выбор потомка: каждая культура весит сумму похожестей на родителей (TileEntityCrop#attemptCrossing). */
    static @Nullable CropCard pickChild(RandomSource r, List<CropBlockEntity> parents) {
        int[] acc = new int[CropCard.ALL.size()];
        int total = 0;
        for (CropCard c : CropCard.ALL) {
            for (CropBlockEntity p : parents) {
                CropCard pc = p.card();
                if (pc != null) total += CropCard.ratio(c, pc);
            }
            acc[c.index()] = total;
        }
        if (total <= 0) return null;
        int search = r.nextInt(total);
        for (CropCard c : CropCard.ALL) if (acc[c.index()] > search) return c;
        return null;
    }

    /** TileEntityCrop#generateWeed: сорняк захватывает соседнюю грядку (если не устоит её стойкость и нет гербицида) или прорастает травой. */
    void generateWeed(ServerLevel sl) {
        RandomSource r = sl.getRandom();
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockPos p = worldPosition.relative(d);
        if (sl.getBlockEntity(p) instanceof CropBlockEntity te) {
            CropCard nc = te.card();
            if (nc == null || (!te.isWeed(nc) && r.nextInt(32) >= te.resistance && te.weedEx <= 0)) {
                int g = Math.max(growth, te.growth);
                if (g < 31 && r.nextBoolean()) g++;
                te.growth = g;
                te.gain = te.resistance = te.scan = te.growthPoints = 0;
                te.setCrop(CropCard.weed(), 1);
            }
        } else if (sl.getBlockState(p).isAir()) {
            BlockState b = sl.getBlockState(p.below());
            if (b.is(Blocks.DIRT) || b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.FARMLAND)) {
                sl.setBlock(p.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                sl.setBlock(p, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // --- уход ---------------------------------------------------------------------------------------------------------------
    /** TileEntityCrop#applyFertilizer: до 100; вручную +100, машиной +90. */
    public boolean applyFertilizer(boolean manual) {
        if (nutrients >= 100) return false;
        nutrients += manual ? 100 : 90;
        setChanged();
        return true;
    }

    /** TileEntityCrop#applyHydration: запас воды до 200; возвращает, сколько взято. */
    public int applyHydration(int amount) {
        if (water >= 200 || amount <= 0) return 0;
        int take = Math.min(200 - water, amount);
        water += take;
        setChanged();
        return take;
    }

    /** TileEntityCrop#applyWeedEx: до 150; вручную +50 (и шанс, что сорняк погибнет), машиной +50. */
    public boolean applyWeedEx(boolean manual) {
        if (weedEx >= 100) return false;
        weedEx += 50;
        CropCard c = card();
        if (manual && c != null && c.isWeedCard() && level != null && level.getRandom().nextInt(3) == 0) reset();
        setChanged();
        return true;
    }

    // --- урожай ---------------------------------------------------------------------------------------------------------------
    /** TileEntityCrop#harvest: (0,95^уровень × 1,03^урожайность) плодов по Гауссу, к каждому +1 с шансом урожайность %; размер — «после сбора». */
    public List<ItemStack> harvest(RandomSource r) {
        CropCard c = card();
        if (c == null || !c.canBeHarvested(size())) return new ArrayList<>();
        List<ItemStack> out = harvestDrops(r, c);
        growthPoints = 0;
        setCrop(c, c.afterHarvest());
        return out;
    }

    private List<ItemStack> harvestDrops(RandomSource r, CropCard c) {
        List<ItemStack> out = new ArrayList<>();
        double chance = c.dropGainChance() * Math.pow(1.03, gain);
        int count = (int) Math.max(0, Math.round(r.nextGaussian() * chance * 0.6827 + chance));
        for (int i = 0; i < count; i++) {
            ItemStack s = c.gain(r);
            if (s.isEmpty()) continue;
            if (r.nextInt(100) <= gain) s.grow(1);
            out.add(s);
        }
        return out;
    }

    /** TileEntityCrop#pick: при сломе — семена с шансом по размеру, уровню, стойкости и росту; со спелой культуры и её плоды. Состояние не меняет. */
    public List<ItemStack> pick(RandomSource r, boolean manual) {
        List<ItemStack> out = new ArrayList<>();
        CropCard c = card();
        if (c == null) return out;
        boolean ripe = c.canBeHarvested(size());
        float first = c.dropSeedChance(size());
        for (int i = 0; i < resistance; i++) first *= 1.1F;
        int seeds = 0;
        if (ripe) {
            if (r.nextFloat() <= (first + 1F) * 0.8F) seeds++;
            float chance = c.dropSeedChance(size()) + growth / 100F;
            if (!manual) chance *= 0.8F;
            for (int i = 23; i < gain; i++) chance *= 0.95F;
            if (r.nextFloat() <= chance) seeds++;
            out.addAll(harvestDrops(r, c));
        } else if (r.nextFloat() <= first * 1.5F) {
            seeds++;
        }
        for (int i = 0; i < seeds; i++) out.add(CropSeedItem.of(c, growth, gain, resistance, scan));
        return out;
    }

    public void drop(List<ItemStack> stacks) {
        if (level == null) return;
        for (ItemStack s : stacks) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
    }

    /** Совок: сорняк уходит в мешочек со своими свойствами (ItemToolWeedingTrowel). */
    public @Nullable ItemStack weedOut() {
        CropCard c = card();
        if (c == null || !c.isWeedCard()) return null;
        ItemStack s = CropSeedItem.of(c, growth, gain, resistance, 4);
        reset();
        return s;
    }

    public List<Component> describe() {
        List<Component> l = new ArrayList<>();
        if (level != null) updateTerrain(level);
        CropCard c = card();
        if (c == null) l.add(Component.translatable(cross() ? "crop.iis.lens.cross" : "crop.iis.lens.empty").withStyle(ChatFormatting.GOLD));
        else {
            l.add(Component.translatable("crop.iis.lens.title", c.displayName(), size(), c.maxSize()).withStyle(ChatFormatting.GOLD));
            l.add(Component.translatable("crop.iis.lens.stats", growth, gain, resistance));
            if (c.block() != null) l.add(Component.translatable("crop.iis.lens.block", c.block()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        l.add(Component.translatable("crop.iis.lens.storage", nutrients, water, weedEx));
        l.add(Component.translatable("crop.iis.lens.env", humidity, soil, air));
        return l;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel sl) {
            List<ItemStack> d = new ArrayList<>(pick(sl.getRandom(), true));
            d.add(new ItemStack(CropContent.CROP_STICKS.get(), cross() ? 2 : 1));
            for (ItemStack s : d) Containers.dropItemStack(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("growth", growth);
        out.putInt("gain", gain);
        out.putInt("resistance", resistance);
        out.putInt("scan", scan);
        out.putInt("points", growthPoints);
        out.putInt("nutrients", nutrients);
        out.putInt("water", water);
        out.putInt("weedEx", weedEx);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        growth = in.getIntOr("growth", 0);
        gain = in.getIntOr("gain", 0);
        resistance = in.getIntOr("resistance", 0);
        scan = in.getIntOr("scan", 0);
        growthPoints = in.getIntOr("points", 0);
        nutrients = in.getIntOr("nutrients", 0);
        water = in.getIntOr("water", 0);
        weedEx = in.getIntOr("weedEx", 0);
    }
}
