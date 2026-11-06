package ru.iis.rail.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.RailContent;

import java.util.List;
import java.util.UUID;

/**
 * Тоннельный бур Railcraft ({@code EntityTunnelBore}, D102). Едет по своему направлению со скоростью 0.03, пока есть
 * топливо (12 единиц горения за тик), и по очереди: подсыпает балласт (гравий) в пустоту под путём на 1.5 впереди,
 * кладёт путь на 0.8 впереди, выбуривает слой 3×3 на 3.3 впереди — задержка слоя = твёрдость слоя × 8 × (2 − скорость
 * головки), затем 40 тиков; лава рядом или неудача — пауза 200 тиков. Каждый выбуренный блок стоит головке 1 прочности;
 * добыча: топливо — в топку, гравий — в балласт, остальное — в сцепленные вагонетки, иначе выпадает позади.
 * Слоты (окно сундука 9×3): 0 — головка, 1–8 топливо, 9–17 балласт, 18–26 пути. В отличие от Railcraft — одна
 * сущность размером с вагонетку (без частей 6.2 блока) и с корпусом коробки передач вместо модели.
 */
public class TunnelBore extends AbstractMinecartContainer {
    public static final double SPEED = 0.03;
    public static final int SLOT_HEAD = 0, FUEL_FROM = 1, FUEL_TO = 9, BALLAST_FROM = 9, BALLAST_TO = 18, RAILS_FROM = 18, RAILS_TO = 27;
    static final int FAIL_DELAY = 200, STANDARD_DELAY = 5, LAYER_DELAY = 40, BALLAST_DELAY = 10, FUEL_CONSUMPTION = 12;
    static final float HARDNESS_MULTIPLIER = 8;
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(TunnelBore.class, EntityDataSerializers.INT);

    private int delay, fuel, clock;
    private boolean active, moving, powered, placeBallast, placeRail, boreLayer;

    public TunnelBore(EntityType<? extends TunnelBore> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_FACING, Direction.SOUTH.get2DDataValue());
    }

    public Direction facing() {
        return Direction.from2DDataValue(entityData.get(DATA_FACING));
    }

    public void setFacing(Direction d) {
        entityData.set(DATA_FACING, d.get2DDataValue());
    }

    public int fuel() {
        return fuel;
    }

    /** Для модели: крутится ли голова. */
    public boolean isDrilling() {
        return getDeltaMovement().horizontalDistanceSqr() > 1e-5;
    }

    public boolean moving() {
        return moving;
    }

    private BlockPos ahead(double offset) {
        Direction f = facing();
        return BlockPos.containing(getX() + f.getStepX() * offset, getY(), getZ() + f.getStepZ() * offset);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel sl) serverLogic(sl);
        super.tick();
    }

    private void serverLogic(ServerLevel sl) {
        clock++;
        if (clock % 64 == 0) {
            powered = false;
            moving = false;
        }
        if (clock % 20 == 0) pullFromTrain(sl);
        // updateFuel
        if (powered) fuel -= FUEL_CONSUMPTION;
        if (fuel <= FUEL_CONSUMPTION) addFuel(sl);
        powered = fuel > 0 && active;
        if (fuel > 0 && delay == 0) {
            active = true;
            RailShape dir = facing().getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            int y = getBlockY();
            if (delay == 0) {
                BlockPos b = ahead(1.5).atY(y - 1);
                if (placeBallast) {
                    boolean ok = placeBallast(sl, b);
                    delay = ok ? STANDARD_DELAY : FAIL_DELAY;
                    if (!ok) active = false;
                    placeBallast = false;
                } else if (!sl.getBlockState(b).isFaceSturdy(sl, b, Direction.UP)) {
                    placeBallast = true;
                    delay = BALLAST_DELAY;
                }
            }
            if (delay == 0) {
                BlockPos r = ahead(0.8).atY(y);
                BlockState rs = sl.getBlockState(r);
                if (placeRail) {
                    boolean ok = placeTrack(sl, r, dir);
                    delay = ok ? STANDARD_DELAY : FAIL_DELAY;
                    if (!ok) active = false;
                    placeRail = false;
                } else if (rs.getBlock() instanceof BaseRailBlock rail) {
                    if (rail.getRailDirection(rs, sl, r, this) != dir && rs.hasProperty(RailBlock.SHAPE)) {
                        sl.setBlock(r, rs.setValue(RailBlock.SHAPE, dir), Block.UPDATE_ALL);
                        delay = STANDARD_DELAY;
                    }
                } else if (rs.isAir() || TrackLayerCart.replaceable(sl, r)) {
                    placeRail = true;
                    delay = STANDARD_DELAY;
                } else {
                    delay = FAIL_DELAY;
                    active = false;
                }
            }
            if (delay == 0) {
                BlockPos l = ahead(3.3).atY(y);
                if (boreLayer) {
                    boolean ok = boreLayer(sl, l, dir);
                    delay = ok ? LAYER_DELAY : FAIL_DELAY;
                    if (!ok) active = false;
                    boreLayer = false;
                } else if (lavaNear(sl, l)) {
                    delay = FAIL_DELAY;
                    active = false;
                } else {
                    delay = (int) Math.ceil(layerHardness(sl, l, dir));
                    if (delay != 0) boreLayer = true;
                }
            }
        }
        if (powered) {
            Vec3 c = Vec3.atBottomCenterOf(ahead(3.3).atY(getBlockY()));
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - 0.8, getY(), c.z - 0.8, c.x + 0.8, getY() + 2, c.z + 0.8))) {
                e.hurtServer(sl, damageSources().generic(), 2);
            }
        }
        moving = fuel > 0 && delay == 0;
        if (delay > 0) delay--;
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 movement) {
        if (!moving) return new Vec3(0, movement.y, 0);
        Direction f = facing();
        return new Vec3(f.getStepX() * SPEED, movement.y, f.getStepZ() * SPEED);
    }

    /** Клетки слоя 3×3: по ширине поперёк хода (−1..+1), по высоте y..y+2. */
    private static List<BlockPos> layer(BlockPos c, RailShape dir) {
        Direction side = dir == RailShape.NORTH_SOUTH ? Direction.EAST : Direction.SOUTH;
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>(9);
        for (int s = -1; s <= 1; s++) for (int h = 0; h < 3; h++) out.add(c.relative(side, s).above(h));
        return out;
    }

    private float layerHardness(ServerLevel sl, BlockPos c, RailShape dir) {
        float hardness = 0;
        for (BlockPos p : layer(c, dir)) hardness += blockHardness(sl, p, dir);
        hardness *= HARDNESS_MULTIPLIER;
        ItemStack head = getItem(SLOT_HEAD);
        if (head.getItem() instanceof BoreHeadItem bh) hardness *= 2f - bh.digModifier;
        return hardness;
    }

    private float blockHardness(ServerLevel sl, BlockPos p, RailShape dir) {
        BlockState s = sl.getBlockState(p);
        if (s.isAir() || skippable(sl, p, s, dir)) return 0;
        if (s.is(Blocks.OBSIDIAN)) return 15;
        if (!canMine(sl, p, s)) return 0.1f;
        float h = s.getDestroySpeed(sl, p);
        return h <= 0 ? 0.1f : h;
    }

    /** Путь в нужную сторону и факелы бур не трогает. */
    private boolean skippable(ServerLevel sl, BlockPos p, BlockState s, RailShape dir) {
        if (s.getBlock() instanceof BaseRailBlock rail) return rail.getRailDirection(s, sl, p, this) == dir;
        return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH);
    }

    /** canHeadHarvestBlock: уровень головки (железо и сталь — как железная кирка, алмаз — как алмазная); mineAllBlocks в ИИС 1. */
    private boolean canMine(ServerLevel sl, BlockPos p, BlockState s) {
        ItemStack head = getItem(SLOT_HEAD);
        if (!(head.getItem() instanceof BoreHeadItem bh)) return false;
        if (s.getDestroySpeed(sl, p) < 0) return false;
        if (!s.requiresCorrectToolForDrops()) return true;
        return bh.harvestLevel >= 3 ? !s.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL) : !s.is(BlockTags.INCORRECT_FOR_IRON_TOOL);
    }

    private boolean boreLayer(ServerLevel sl, BlockPos c, RailShape dir) {
        boolean clear = true;
        for (BlockPos p : layer(c, dir)) clear = clear && mineBlock(sl, p, dir);
        return clear;
    }

    private boolean mineBlock(ServerLevel sl, BlockPos p, RailShape dir) {
        BlockState s = sl.getBlockState(p);
        if (s.isAir() || skippable(sl, p, s, dir)) return true;
        ItemStack head = getItem(SLOT_HEAD);
        if (head.isEmpty() || !canMine(sl, p, s)) return false;
        for (ItemStack drop : Block.getDrops(s, sl, p, sl.getBlockEntity(p))) {
            ItemStack rest = drop;
            if (sl.fuelValues().burnDuration(rest) > 0) rest = insert(this, FUEL_FROM, FUEL_TO, rest);
            if (!rest.isEmpty() && rest.is(Items.GRAVEL)) rest = insert(this, BALLAST_FROM, BALLAST_TO, rest);
            if (!rest.isEmpty()) rest = pushToTrain(sl, rest);
            if (!rest.isEmpty()) {
                Direction f = facing();
                sl.addFreshEntity(new ItemEntity(sl, getX() - f.getStepX() * 3.2, getY() + 0.3, getZ() - f.getStepZ() * 3.2, rest));
            }
        }
        sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        head.setDamageValue(head.getDamageValue() + 1);
        if (head.getDamageValue() > head.getMaxDamage()) setItem(SLOT_HEAD, ItemStack.EMPTY);
        setChanged();
        return true;
    }

    private static boolean lavaNear(ServerLevel sl, BlockPos c) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 3; dy++) {
            if (sl.getFluidState(c.offset(dx, dy, dz)).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    private boolean placeBallast(ServerLevel sl, BlockPos p) {
        if (!TrackLayerCart.replaceable(sl, p)) return false;
        for (int i = BALLAST_FROM; i < BALLAST_TO; i++) {
            ItemStack s = getItem(i);
            if (s.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof BaseRailBlock)) {
                sl.setBlock(p, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                s.shrink(1);
                setChanged();
                return true;
            }
        }
        return false;
    }

    private boolean placeTrack(ServerLevel sl, BlockPos p, RailShape dir) {
        if (!TrackLayerCart.placeFromStock(sl, this, RAILS_FROM, RAILS_TO, p)) return false;
        BlockState s = sl.getBlockState(p);
        if (s.hasProperty(RailBlock.SHAPE)) sl.setBlock(p, s.setValue(RailBlock.SHAPE, dir), Block.UPDATE_ALL);
        return true;
    }

    private void addFuel(ServerLevel sl) {
        for (int i = FUEL_FROM; i < FUEL_TO; i++) {
            ItemStack s = getItem(i);
            int burn = s.isEmpty() ? 0 : sl.fuelValues().burnDuration(s);
            if (burn <= 0) continue;
            ItemStack rem = s.getCraftingRemainder() == null ? ItemStack.EMPTY : s.getCraftingRemainder().create();
            s.shrink(1);
            if (s.isEmpty() && !rem.isEmpty()) setItem(i, rem);
            fuel += burn;
            setChanged();
            return;
        }
    }

    /** stockFuel / stockBallast / stockTracks: пустые разделы пополняются из сцепленных вагонеток. */
    private void pullFromTrain(ServerLevel sl) {
        for (UUID u : TrainLinks.links(this)) {
            if (!(sl.getEntity(u) instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) continue;
                int from, to;
                if (sl.fuelValues().burnDuration(s) > 0) { from = FUEL_FROM; to = FUEL_TO; }
                else if (s.is(Items.GRAVEL)) { from = BALLAST_FROM; to = BALLAST_TO; }
                else if (s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof BaseRailBlock) { from = RAILS_FROM; to = RAILS_TO; }
                else continue;
                ItemStack rest = insert(this, from, to, s.copy());
                if (rest.getCount() != s.getCount()) {
                    c.setItem(i, rest);
                    c.setChanged();
                }
            }
        }
    }

    private ItemStack pushToTrain(ServerLevel sl, ItemStack stack) {
        for (UUID u : TrainLinks.links(this)) {
            if (sl.getEntity(u) instanceof Container c && !(c instanceof TunnelBore)) {
                stack = insert(c, 0, c.getContainerSize(), stack);
                if (stack.isEmpty()) return stack;
            }
        }
        return stack;
    }

    static ItemStack insert(Container c, int from, int to, ItemStack stack) {
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            ItemStack t = c.getItem(i);
            if (t.isEmpty()) {
                c.setItem(i, stack);
                c.setChanged();
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(t, stack) && t.getCount() < t.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), t.getMaxStackSize() - t.getCount());
                t.grow(n);
                stack.shrink(n);
                c.setChanged();
            }
        }
        return stack;
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_HEAD) return stack.getItem() instanceof BoreHeadItem;
        return true;
    }

    @Override
    protected Item getDropItem() {
        return RailContent.TUNNEL_BORE.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return ru.iis.multi.MultiContent.STEEL_GEAR_BOX_CASING.get().defaultBlockState();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("facing", facing().get2DDataValue());
        out.putInt("delay", delay);
        out.putInt("fuel", fuel);
        out.putBoolean("active", active);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setFacing(Direction.from2DDataValue(in.getIntOr("facing", Direction.SOUTH.get2DDataValue())));
        delay = in.getIntOr("delay", 0);
        fuel = in.getIntOr("fuel", 0);
        active = in.getBooleanOr("active", false);
    }
}
