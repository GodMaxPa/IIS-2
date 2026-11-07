package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvContent;
import ru.iis.machine.block.MachineBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Машина на мускульной силе (GT_MetaTileEntity_PrimitiveMuscleMachine, _PrimitiveMineDigger, _PrimitiveMiner, D133).
 * Кнопка в окне добавляет 8 силы из 64 и отнимает сытость; одна операция стоит 1 силы и прочности бура, длится
 * от полного времени (сила на нуле) до половины (сила полна). Входы 0–3, выходы 4–27.
 */
public class MuscleBlockEntity extends BlockEntity implements Container, MenuProvider, MachineBlock.TickingMachine {
    public static final int INPUTS = 4, SLOTS = 28, MAX_POWER = 64, POWER_PER_CLICK = 8, DATA = 6;
    static final TagKey<Block> ORES = BlockTags.create(Identifier.fromNamespaceAndPath("c", "ores"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    int power, progress, maxProgress;
    // шахтокопатель
    boolean started, foundOre, finalStop, pickup, meetStop;
    int digHeight, oreVeinHeight = 7, digsCount;
    private int ladderIdx = -1, cobbleIdx = -1, torchIdx = -1;

    final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> power;
                case 1 -> progress;
                case 2 -> maxProgress;
                case 3 -> started ? worldPosition.getY() - digHeight : 0;
                case 4 -> finalStop ? 2 : pickup ? 1 : 0;
                default -> digger() ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public MuscleBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.MUSCLE_BE.get(), pos, state);
    }

    public boolean digger() {
        return getBlockState().getBlock() instanceof MuscleBlock b && b.digger;
    }

    // --- числа GT ----------------------------------------------------------------------------------------------------------
    private int hungryTicks() {
        return digger() ? 320 : 480;
    }

    private int drillDamage() {
        return digger() ? 12 : 25;
    }

    private int ticksPerOre() {
        return digger() ? (pickup ? 5 : 20) : 480;
    }

    private int foodPerClick() {
        return digger() ? 1 : 2;
    }

    private boolean drillRequired() {
        return !digger() || !pickup;
    }

    /** Подъём с лестницами дешевле: сила тратится раз в 8 шагов. */
    private int powerPerOperation() {
        if (digger() && pickup) return ++digsCount % 8 == 0 ? 1 : 0;
        return 1;
    }

    // --- работа -----------------------------------------------------------------------------------------------------------
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (progress < maxProgress) {
            progress++;
        } else {
            if (progress > 0 && maxProgress > 0) endProcess(sl);
            progress = 0;
            maxProgress = 0;
            if (readyToDig(sl) && findDrill(false) && power > 0) {
                int cost = powerPerOperation();
                if (power >= cost) {
                    power -= cost;
                    if (findDrill(true)) maxProgress = ticksPerOre() - Math.round(power * ticksPerOre() / 2F / MAX_POWER);
                }
            }
            setChanged();
        }
        boolean active = progress > 0;
        if (getBlockState().getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ACTIVE, active), 3);
    }

    /** Кнопка «Работать»: GT_Container_PrimitiveMiner.slotClick, GT_Utility.drainMusclePlayerPower. */
    public boolean work(Player player) {
        if (power + POWER_PER_CLICK > MAX_POWER) return false;
        power += POWER_PER_CLICK;
        setChanged();
        if (player instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
            MobEffectInstance hunger = sp.getEffect(MobEffects.HUNGER);
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, hungryTicks() + (hunger != null ? hunger.getDuration() : 0)));
            FoodData food = sp.getFoodData();
            int cost = foodPerClick();
            var tag = sp.getPersistentData();
            if (food.getFoodLevel() < cost) {
                int hungryWork = tag.getIntOr("iis_hungry_work", 0) + 1;
                tag.putInt("iis_hungry_work", hungryWork);
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - cost));
                sp.hurtServer(sp.level(), sp.damageSources().starve(), 1F + hungryWork);
            } else {
                food.setFoodLevel(food.getFoodLevel() - cost);
                tag.putInt("iis_hungry_work", 0);
            }
        }
        return true;
    }

    private boolean findDrill(boolean damage) {
        if (!drillRequired()) return true;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack s = items.get(i);
            if (!s.is(LvContent.PRIMITIVE_DRILL.get())) continue;
            if (damage) {
                int d = s.getDamageValue() + drillDamage();
                if (d >= s.getMaxDamage()) items.set(i, ItemStack.EMPTY);
                else s.setDamageValue(d);
            }
            return true;
        }
        return false;
    }

    private boolean readyToDig(ServerLevel sl) {
        if (!digger()) return started ? digHeight > sl.getMinY() + 1 && consumable() >= 0 : consumable() >= 0;
        if (finalStop) return false;
        if (!meetStop && validate()) return true;
        if (meetStop) {
            if (!pickup) pickup = validateForPickUp();
            return pickup && pickUpLadder(sl);
        }
        return false;
    }

    private void endProcess(ServerLevel sl) {
        if (digger()) digNextLayer(sl);
        else digNextSmallOre(sl);
    }

    // --- шахтокопатель ------------------------------------------------------------------------------------------------------
    private Direction front() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    private BlockPos at(int forward, int y) {
        BlockPos p = worldPosition.relative(front(), forward);
        return new BlockPos(p.getX(), y, p.getZ());
    }

    private boolean validate() {
        ladderIdx = cobbleIdx = torchIdx = -1;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack s = items.get(i);
            if (ladderIdx < 0 && s.is(Items.LADDER)) ladderIdx = i;
            if (cobbleIdx < 0 && s.is(Items.COBBLESTONE)) cobbleIdx = i;
            if (torchIdx < 0 && s.is(Items.TORCH)) torchIdx = i;
        }
        return ladderIdx >= 0;
    }

    private boolean validateForPickUp() {
        validate();
        boolean drill = false;
        for (int i = 0; i < INPUTS; i++) if (items.get(i).is(LvContent.PRIMITIVE_DRILL.get())) drill = true;
        return !drill && ladderIdx < 0 && torchIdx < 0 && cobbleIdx >= 0;
    }

    static boolean isSmallOre(BlockState s) {
        return ru.iis.world.ore.OreBlocks.isSmallOre(s.getBlock());
    }

    /** GT isGtOre и не малая: жилы GT; ванильные руды в мире ИИС не генерируются (D12), но тег c:ores тоже считается. */
    static boolean isOre(BlockState s) {
        return !isSmallOre(s) && (ru.iis.world.ore.OreBlocks.isOre(s.getBlock()) || s.is(ORES));
    }

    private static boolean unstable(ServerLevel sl, BlockPos p) {
        return !sl.getBlockState(p).isCollisionShapeFullBlock(sl, p);
    }

    private void digNextLayer(ServerLevel sl) {
        if (!validate()) return;
        if (!started) {
            digHeight = worldPosition.getY();
            started = true;
        }
        boolean stop = false;
        for (BlockPos p : layer(digHeight - 1, 2)) {
            BlockState s = sl.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(Blocks.BEDROCK) || s.getDestroySpeed(sl, p) < 0 || oreVeinHeight <= 0 || nextLayerHasOre(sl)) {
                stop = true;
                break;
            } else if (isOre(s)) {
                foundOre = true;
            }
        }
        meetStop = stop;
        sl.playSound(null, worldPosition, stop ? SoundEvents.ANVIL_LAND : SoundEvents.STONE_BREAK, SoundSource.BLOCKS, stop ? 0.4F : 1F, 1F);
        if (!stop) {
            digHeight--;
            if (foundOre) oreVeinHeight--;
            digHole(sl);
        } else if (torchIdx >= 0) {
            setTorches(sl);
        }
    }

    /** Руда прямо в стволе не копается: шахта встаёт над ней. */
    private boolean nextLayerHasOre(ServerLevel sl) {
        return isOre(sl.getBlockState(at(0, digHeight - 1))) || isOre(sl.getBlockState(at(1, digHeight - 1)));
    }

    private void digHole(ServerLevel sl) {
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos p : new BlockPos[]{at(1, digHeight), at(0, digHeight)}) {
            BlockState s = sl.getBlockState(p);
            if (!s.isAir() && !isOre(s)) {
                drops.addAll(Block.getDrops(s, sl, p, sl.getBlockEntity(p)));
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        pushToOutputs(drops);
        BlockPos wall = at(2, digHeight);
        boolean stabilized = true;
        if (unstable(sl, wall)) {
            stabilized = take(cobbleIdx);
            if (stabilized) sl.setBlock(wall, Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        if (stabilized && take(ladderIdx)) {
            sl.setBlock(at(1, digHeight), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, front().getOpposite()), 3);
        }
    }

    private void setTorches(ServerLevel sl) {
        BlockPos floor = at(0, digHeight - 1);
        boolean place = true;
        if (unstable(sl, floor)) {
            if (take(cobbleIdx)) sl.setBlock(floor, Blocks.COBBLESTONE.defaultBlockState(), 3);
            else place = false;
        }
        if (place && take(torchIdx)) sl.setBlock(at(0, digHeight), Blocks.TORCH.defaultBlockState(), 3);
        // через каждые 16 блоков вверх — факел на задней стене ствола
        for (int y = digHeight + 16; y < worldPosition.getY() && torchIdx >= 0 && !items.get(torchIdx).isEmpty(); y += 16) {
            BlockPos back = at(-1, y);
            boolean ok = true;
            if (unstable(sl, back)) {
                if (take(cobbleIdx)) sl.setBlock(back, Blocks.COBBLESTONE.defaultBlockState(), 3);
                else ok = false;
            }
            if (ok && take(torchIdx)) sl.setBlock(at(0, y), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, front()), 3);
        }
    }

    private boolean pickUpLadder(ServerLevel sl) {
        boolean success = true;
        List<ItemStack> drops = new ArrayList<>();
        boolean nearTop = worldPosition.getY() - 3 <= digHeight;
        for (BlockPos p : new BlockPos[]{at(1, digHeight), at(0, digHeight)}) {
            BlockState s = sl.getBlockState(p);
            Block put = trashBlock(sl, nearTop);
            if (put != null) {
                if (!s.isAir()) drops.addAll(Block.getDrops(s, sl, p, sl.getBlockEntity(p)));
                sl.setBlock(p, put.defaultBlockState(), 3);
            } else {
                success = false;
            }
        }
        pushToOutputs(drops);
        if (success) {
            digHeight++;
            if (worldPosition.getY() == digHeight) finalStop = true;
            sl.playSound(null, worldPosition, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.3F, 1F);
        }
        return success;
    }

    /** Чем засыпать ствол: у поверхности земля или песок (что вокруг больше), глубже гравий, потом что есть. */
    private @Nullable Block trashBlock(ServerLevel sl, boolean nearTop) {
        int[] idx = {-1, -1, -1, -1, -1};
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!(items.get(i).getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            if (b == Blocks.LADDER || b == Blocks.TORCH) continue;
            if (idx[0] < 0 && (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK)) idx[0] = i;
            else if (idx[1] < 0 && (b == Blocks.SANDSTONE || b == Blocks.SAND)) idx[1] = i;
            else if (idx[2] < 0 && b == Blocks.GRAVEL) idx[2] = i;
            else if (idx[3] < 0) idx[3] = i;
            else if (idx[4] < 0) idx[4] = i;
        }
        int best = -1;
        if (nearTop && (idx[0] >= 0 || idx[1] >= 0)) best = grassAround(sl) && idx[0] >= 0 ? idx[0] : idx[1];
        else if (idx[2] >= 0) best = idx[2];
        if (best < 0) best = Math.max(idx[3], idx[4]);
        if (best < 0) best = Math.max(idx[0], idx[1]);
        if (best < 0) return null;
        Block b = ((BlockItem) items.get(best).getItem()).getBlock();
        take(best);
        return b;
    }

    private boolean grassAround(ServerLevel sl) {
        int grass = 0, sand = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-3, -3, -3), worldPosition.offset(3, 3, 3))) {
            Block b = sl.getBlockState(p).getBlock();
            if (b == Blocks.GRASS_BLOCK || b == Blocks.DIRT) grass++;
            if (b == Blocks.SAND || b == Blocks.SANDSTONE) sand++;
        }
        return grass >= sand;
    }

    // --- добытчик малых руд -------------------------------------------------------------------------------------------------
    /** Слот с булыжником или землёй; -1 — нечем заделывать. */
    private int consumable() {
        for (int i = 0; i < INPUTS; i++) {
            ItemStack s = items.get(i);
            if (s.is(Items.COBBLESTONE) || s.is(Items.DIRT)) return i;
        }
        return -1;
    }

    /** GT digNextOre: слой за слоем вниз до первой малой руды в квадрате 7 × 7; за операцию — одна руда, и слой дальше не досматривается (как в GT). */
    private void digNextSmallOre(ServerLevel sl) {
        while (true) {
            int fill = consumable();
            if (fill < 0) return;
            if (!started) {
                digHeight = worldPosition.getY() - 1;
                started = true;
            } else {
                digHeight--;
            }
            if (digHeight <= sl.getMinY() + 1) return;
            for (BlockPos p : layer(digHeight, 3)) {
                BlockState s = sl.getBlockState(p);
                if (s.isAir() || !isSmallOre(s)) continue;
                List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, sl, p, sl.getBlockEntity(p)));
                Block put = ((BlockItem) items.get(fill).getItem()).getBlock();
                take(fill);
                sl.setBlock(p, put.defaultBlockState(), 3);
                pushToOutputs(drops);
                sl.playSound(null, worldPosition, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1F, 1F);
                return;
            }
        }
    }

    // --- общее ------------------------------------------------------------------------------------------------------------
    private List<BlockPos> layer(int y, int r) {
        List<BlockPos> l = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) l.add(new BlockPos(worldPosition.getX() + x, y, worldPosition.getZ() + z));
        return l;
    }

    private boolean take(int idx) {
        if (idx < 0 || items.get(idx).isEmpty()) return false;
        items.get(idx).shrink(1);
        return true;
    }

    private void pushToOutputs(List<ItemStack> drops) {
        for (ItemStack d : drops) {
            for (int i = INPUTS; i < SLOTS && !d.isEmpty(); i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) {
                    items.set(i, d.copy());
                    d.setCount(0);
                } else if (ItemStack.isSameItemSameComponents(s, d) && s.getCount() < s.getMaxStackSize()) {
                    int n = Math.min(d.getCount(), s.getMaxStackSize() - s.getCount());
                    s.grow(n);
                    d.shrink(n);
                }
            }
            if (!d.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, d);
        }
        setChanged();
    }

    // --- Container, меню, сохранение ----------------------------------------------------------------------------------------
    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int i) {
        return items.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        ItemStack r = ContainerHelper.removeItem(items, i, n);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(items, i);
    }

    @Override
    public void setItem(int i, ItemStack s) {
        items.set(i, s);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MuscleMenu(id, inventory, this, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, this);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("Power", power);
        out.putInt("Progress", progress);
        out.putInt("MaxProgress", maxProgress);
        out.putBoolean("Started", started);
        out.putBoolean("FoundOre", foundOre);
        out.putBoolean("FinalStop", finalStop);
        out.putBoolean("Pickup", pickup);
        out.putBoolean("MeetStop", meetStop);
        out.putInt("DigHeight", digHeight);
        out.putInt("OreVeinHeight", oreVeinHeight);
        out.putInt("DigsCount", digsCount);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
        power = in.getIntOr("Power", 0);
        progress = in.getIntOr("Progress", 0);
        maxProgress = in.getIntOr("MaxProgress", 0);
        started = in.getBooleanOr("Started", false);
        foundOre = in.getBooleanOr("FoundOre", false);
        finalStop = in.getBooleanOr("FinalStop", false);
        pickup = in.getBooleanOr("Pickup", false);
        meetStop = in.getBooleanOr("MeetStop", false);
        digHeight = in.getIntOr("DigHeight", 0);
        oreVeinHeight = in.getIntOr("OreVeinHeight", 7);
        digsCount = in.getIntOr("DigsCount", 0);
    }
}
