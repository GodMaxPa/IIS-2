package ru.iis.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import ru.iis.IIS;
import ru.iis.lv.block.ElectricBlockEntity;

/**
 * Реактор: блок, сетка 3 столбца по 6 ячеек и ещё столбец за каждую камеру рядом (до 54 ячеек, IC2). Работает при сигнале красного камня на реакторе или камере.
 * Раз в секунду считает {@link ReactorCore}, EU отдаёт в провода. Перегрев: IC2 calculateHeatEffects: от 40 % пламя, от 50 % вода испаряется, от 70 % радиация вокруг,
 * от 85 % лава, на 100 % взрыв с разрушением блоков.
 */
public class NuclearReactorBlockEntity extends ElectricBlockEntity {
    public static final int SLOTS = ReactorCore.WIDTH * ReactorCore.HEIGHT;
    /** Буфер отдачи: секунда работы при самом большом выходе. */
    public static final long BUFFER = 1L << 22;

    private final ReactorCore core = new ReactorCore();
    private float euPerTick;
    private int tick;

    public final ContainerData reactorData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> core.heat;
                case 1 -> core.maxHeat;
                case 2 -> Math.round(euPerTick);
                case 3 -> columns();
                case 4 -> core.active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 5;
        }
    };

    public NuclearReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorContent.REACTOR_BE.get(), pos, state, SLOTS, BUFFER, 0);
    }

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> itemHandler(Direction side) {
        return side == null ? null : new net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper(this, side);
    }

    public int heat() {
        return core.heat;
    }

    public void setHeat(int h) {
        core.heat = h;
    }

    public int maxHeat() {
        return core.maxHeat;
    }

    public float euPerTick() {
        return euPerTick;
    }

    public int columns() {
        if (level == null) return 3;
        int n = 3;
        for (Direction d : Direction.values()) if (level.getBlockState(worldPosition.relative(d)).is(ReactorContent.REACTOR_CHAMBER.get())) n++;
        return Math.min(n, ReactorCore.WIDTH);
    }

    private boolean powered() {
        if (level.hasNeighborSignal(worldPosition)) return true;
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockState(p).is(ReactorContent.REACTOR_CHAMBER.get()) && level.hasNeighborSignal(p)) return true;
        }
        return false;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return false;
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return true;
    }

    /** Напряжение пакета: наименьший уровень, в который влезает выход за тик (как отдача IC2 пакетами). */
    @Override
    public long outputVoltage() {
        for (long v : new long[]{32, 128, 512, 2048, 8192}) if (euPerTick <= v) return energy.stored > 0 ? v : 0;
        return energy.stored > 0 ? 8192 : 0;
    }

    @Override
    public long outputAmperes() {
        long v = outputVoltage();
        return v <= 0 ? 0 : Math.max(1, (long) Math.ceil(euPerTick / v));
    }

    @Override
    protected void work(ServerLevel sl) {
        energy.stored = Math.min(energy.capacity, energy.stored + Math.round(euPerTick));
        if (++tick < 20) return;
        tick = 0;
        step(sl);
    }

    /** Секунда реактора: предметы → расчёт → предметы, затем последствия нагрева. */
    public void step(ServerLevel sl) {
        core.columns = columns();
        core.active = powered();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.get(i);
            core.cells[i] = s.getItem() instanceof ReactorPartItem p ? new ReactorCore.Cell(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), p.part, s.getDamageValue()) : null;
        }
        euPerTick = core.second();
        for (int i = 0; i < SLOTS; i++) {
            ReactorCore.Cell c = core.cells[i];
            if (c == null) continue;
            if (c.removed) items.set(i, ItemStack.EMPTY);
            else if (c.replacedBy != null) items.set(i, new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(IIS.MODID, c.replacedBy))));
            else if (c.part.capacity() > 0) items.get(i).setDamageValue(Math.min(c.damage, c.part.capacity()));
        }
        core.heat = Math.max(0, core.heat);
        setChanged();
        heatEffects(sl);
    }

    private void heatEffects(ServerLevel sl) {
        float power = core.heat / (float) core.maxHeat;
        if (power >= 1) {
            meltdown(sl);
            return;
        }
        var rnd = sl.getRandom();
        if (power >= 0.4F && rnd.nextFloat() < power) {
            BlockPos p = near(sl, 2);
            if (sl.getBlockState(p).isAir()) sl.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
        }
        if (power >= 0.5F) {
            BlockPos p = near(sl, 2);
            if (sl.getFluidState(p).is(Fluids.WATER) || sl.getFluidState(p).is(Fluids.FLOWING_WATER)) sl.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        if (power >= 0.7F) {
            DamageSource rad = new DamageSource(sl.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ReactorContent.RADIATION_DAMAGE));
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(3))) {
                if (!ReactorContent.fullHazmat(e)) e.hurtServer(sl, rad, (int) (sl.getRandom().nextInt(4) * core.heatEffect));
            }
        }
        if (power >= 0.85F && rnd.nextFloat() < power) {
            BlockPos p = near(sl, 2);
            BlockState s = sl.getBlockState(p);
            if (!p.equals(worldPosition) && s.getDestroySpeed(sl, p) >= 0 && !s.is(ReactorContent.REACTOR_CHAMBER.get())) sl.setBlockAndUpdate(p, Blocks.LAVA.defaultBlockState());
        }
    }

    private BlockPos near(ServerLevel sl, int range) {
        var r = sl.getRandom();
        return worldPosition.offset(r.nextInt(range * 2 + 1) - range, r.nextInt(range * 2 + 1) - range, r.nextInt(range * 2 + 1) - range);
    }

    /** Взрыв: реактор и его содержимое пропадают. */
    public void meltdown(ServerLevel sl) {
        float power = core.explosionPower();
        items.clear();
        BlockPos p = worldPosition;
        sl.removeBlock(p, false);
        sl.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, power, Level.ExplosionInteraction.BLOCK);
    }

    public float explosionPower() {
        return core.explosionPower();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof ReactorPartItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) all[i] = i;
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack) && slot % ReactorCore.WIDTH < columns() && items.get(slot).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ReactorMenu(id, inventory, this, reactorData);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("ReactorHeat", core.heat);
        out.putFloat("ReactorOutput", euPerTick);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        core.heat = in.getIntOr("ReactorHeat", 0);
        euPerTick = in.getFloatOr("ReactorOutput", 0F);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    public void dropContents(ServerLevel sl) {
        Containers.dropContents(sl, worldPosition, this);
    }
}
