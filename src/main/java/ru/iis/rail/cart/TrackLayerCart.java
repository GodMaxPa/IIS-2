package ru.iis.rail.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.RailContent;

/**
 * Путеукладчик Railcraft ({@code EntityCartTrackLayer}, D102): едет не быстрее 0.1 блока/тик и кладёт путь из своего
 * запаса (9 слотов) в следующую клетку по ходу, если там пусто или трава и снизу опора; на спуске — на клетку ниже,
 * на подъёме — на клетку выше. Своё торможение 0.9, как у ремонтных вагонеток Railcraft.
 */
public class TrackLayerCart extends AbstractMinecartContainer {
    public static final double MAX_SPEED = 0.1, DRAG = 0.9;
    public static final int SLOTS = 9;

    public TrackLayerCart(EntityType<? extends TrackLayerCart> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 m = getDeltaMovement();
        if (m.horizontalDistanceSqr() < 1e-4) return;
        BlockPos rail = getCurrentBlockPosOrRailBelow();
        if (!(sl.getBlockState(rail).getBlock() instanceof BaseRailBlock)) return;
        Direction dir = Math.abs(m.x) > Math.abs(m.z) ? (m.x > 0 ? Direction.EAST : Direction.WEST) : (m.z > 0 ? Direction.SOUTH : Direction.NORTH);
        placeTrack(sl, rail.relative(dir));
    }

    private void placeTrack(ServerLevel sl, BlockPos p) {
        if (sl.getBlockState(p).getBlock() instanceof BaseRailBlock) return;
        if (!replaceable(sl, p) && replaceable(sl, p.above())) p = p.above();
        else if (replaceable(sl, p) && replaceable(sl, p.below())) p = p.below();
        if (sl.getBlockState(p).getBlock() instanceof BaseRailBlock || !replaceable(sl, p) || !sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) return;
        placeFromStock(sl, this, 0, getContainerSize(), p);
    }

    /** Поставить первый путь из слотов {@code from..to} контейнера; заменяемый блок выпадает. */
    static boolean placeFromStock(ServerLevel sl, net.minecraft.world.Container inv, int from, int to, BlockPos p) {
        for (int i = from; i < to; i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof BaseRailBlock railBlock)) continue;
            if (!sl.getBlockState(p).isAir()) sl.destroyBlock(p, true);
            BlockState state = railBlock.defaultBlockState();
            if (!state.canSurvive(sl, p)) return false;
            sl.setBlock(p, state, Block.UPDATE_ALL);
            s.shrink(1);
            inv.setChanged();
            return true;
        }
        return false;
    }

    /** isValidReplacementBlock: воздух, растения и заменяемые блоки. */
    static boolean replaceable(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES);
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 movement) {
        Vec3 v = movement.multiply(DRAG, 0, DRAG);
        return new Vec3(Math.copySign(Math.min(Math.abs(v.x), MAX_SPEED), v.x), v.y, Math.copySign(Math.min(Math.abs(v.z), MAX_SPEED), v.z));
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof BaseRailBlock;
    }

    @Override
    protected Item getDropItem() {
        return RailContent.TRACK_LAYER.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return ru.iis.multi.MultiContent.STEEL_GEAR_BOX_CASING.get().defaultBlockState();
    }
}
