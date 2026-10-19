package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.energy.EuNode;
import ru.iis.energy.WorldEnergy;
import ru.iis.energy.logic.EnergyNet;
import ru.iis.energy.logic.EnergyStorage;
import ru.iis.lv.Deferred;
import ru.iis.machine.block.MachineBlock;

import java.util.Arrays;
import java.util.List;

/**
 * Общая часть электрических блоков GT5-II (BaseMetaTileEntity): запас EU, приём из сети, выдача в сеть,
 * опасности (IIS GregTech.cfg: rain/fire/lightning_causes_explosions, wirefire_on_explosion, machines_explosion_damage = true).
 *
 * Опасности, как в оригинале:
 *  - пакет выше входного напряжения — взрыв;
 *  - огонь рядом — 1/1000 за тик проверить случайную сторону, огонь там — «энергетический» взрыв;
 *  - каждые 512 из 1024 тиков с шансом 1/3 за тик: вода или лава рядом (кроме снизу) — взрыв;
 *    под открытым небом в дождь — 1/10 взрыв, иначе вокруг загорается огонь; в грозу — ещё 1/3 взрыв;
 *  - «энергетический» взрыв бывает, только если запас ≥ 1/5 ёмкости;
 *  - взорвавшаяся машина выпускает свой запас пакетом 8192 EU/t во все стороны (wirefire): провода перегреваются,
 *    подключённые машины взрываются от перенапряжения.
 */
public abstract class ElectricBlockEntity extends BaseContainerBlockEntity implements EuNode, WorldlyContainer, MachineBlock.TickingMachine {
    /** GT V[5] (IV): напряжение выброса при взрыве. */
    public static final long RELEASE_VOLTAGE = 8192;

    protected NonNullList<ItemStack> items;
    public final EnergyStorage energy;
    protected int tickTimer;
    private boolean exploded;
    /** Учёт для мультиметра (GT mAverageEUInput/Output): EU за текущие и прошлые 20 тиков, максимум принятого напряжения. */
    private long euInWindow, euOutWindow, voltageInWindow;
    public long avgEuIn, avgEuOut, lastVoltageIn;

    protected ElectricBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, long capacity, long inputVoltage) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.energy = new EnergyStorage(capacity, inputVoltage);
    }

    // --- настройка ---------------------------------------------------------------------------------------------
    /** Принимает ли EU с этой стороны. */
    public abstract boolean inputsEnergyFrom(Direction side);

    /** Отдаёт ли EU в эту сторону. */
    public boolean outputsEnergyTo(Direction side) {
        return false;
    }

    public long outputVoltage() {
        return 0;
    }

    public long outputAmperes() {
        return 0;
    }

    public long minimumStored() {
        return 0;
    }

    /** Работа блока за тик (до выдачи энергии). */
    protected abstract void work(ServerLevel level);

    // --- тик ---------------------------------------------------------------------------------------------------
    @Override
    public final void serverTick() {
        if (!(level instanceof ServerLevel sl) || exploded) return;
        tickTimer++;
        if (tickTimer % 20 == 0) {
            avgEuIn = euInWindow / 20;
            avgEuOut = euOutWindow / 20;
            lastVoltageIn = voltageInWindow;
            euInWindow = euOutWindow = voltageInWindow = 0;
        }
        work(sl);
        if (exploded) return;
        emitEnergy(sl);
        if (exploded) return;
        hazards(sl);
    }

    /** BaseMetaTileEntity: выдача 1..N ампер; каждый выпущенный ампер стоит V + 2^уровень EU. */
    private void emitEnergy(ServerLevel sl) {
        long v = outputVoltage();
        if (v <= 0) return;
        long cost = EnergyNet.emissionCost(v);
        long usable = Math.min(outputAmperes(), (energy.stored - minimumStored()) / cost);
        if (usable <= 0) return;
        List<Direction> sides = Arrays.stream(Direction.values()).filter(this::outputsEnergyTo).toList();
        long used = WorldEnergy.emit(sl, worldPosition, sides, v, usable);
        if (used > 0) {
            euOutWindow += used * v;
            energy.stored = Math.max(0, energy.stored - used * cost);
            setChanged();
        }
    }

    private void hazards(ServerLevel sl) {
        if (energy.capacity <= 0 || !ru.iis.IISConfig.on(ru.iis.IISConfig.WEATHER_EXPLOSIONS)) return;
        var rnd = sl.getRandom();
        if (rnd.nextInt(1000) == 0) {
            BlockState s = sl.getBlockState(worldPosition.relative(Direction.from3DDataValue(rnd.nextInt(6))));
            if (s.getBlock() instanceof BaseFireBlock) doEnergyExplosion();
        }
        if (exploded || (tickTimer & 512) != 512 || rnd.nextInt(3) != 0) return;
        for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.SOUTH, Direction.NORTH}) {
            FluidState f = sl.getFluidState(worldPosition.relative(d));
            if (!f.isEmpty()) {
                doEnergyExplosion();
                break;
            }
        }
        if (exploded || !exposedToRain(sl)) return;
        if (sl.isRaining() && rainPossible(sl)) {
            if (rnd.nextInt(10) == 0) doEnergyExplosion();
            else setOnFire(sl);
        }
        if (!exploded && sl.isThundering() && rainPossible(sl) && rnd.nextInt(3) == 0) doEnergyExplosion();
    }

    /** GT: над блоком (или над соседними столбцами на его высоте) нет крыши. */
    private boolean exposedToRain(ServerLevel sl) {
        int x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
        java.util.function.BiFunction<Integer, Integer, Integer> h = (px, pz) -> sl.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
        return h.apply(x, z) - 2 < y || h.apply(x, z - 1) - 1 < y || h.apply(x, z + 1) - 1 < y
                || h.apply(x - 1, z) - 1 < y || h.apply(x + 1, z) - 1 < y;
    }

    private boolean rainPossible(Level l) {
        return l.getBiome(worldPosition).value().hasPrecipitation();
    }

    /** GT_Utility#setCoordsOnFire: огонь на свободных соседних клетках. */
    private void setOnFire(ServerLevel sl) {
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (sl.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(sl, p, d)) sl.setBlockAndUpdate(p, BaseFireBlock.getState(sl, p));
        }
    }

    // --- приём энергии -----------------------------------------------------------------------------------------
    @Override
    public long injectEnergy(Direction from, long voltage, long amperes) {
        if (exploded || !(level instanceof ServerLevel sl) || !inputsEnergyFrom(from)) return 0;
        EnergyStorage.Inject r = energy.inject(sl.getGameTime(), voltage, amperes);
        if (r.overvoltage()) {
            if (ru.iis.IISConfig.on(ru.iis.IISConfig.OVERVOLTAGE_EXPLOSIONS)) doExplosion(voltage);
            return 0;
        }
        if (r.amperes() > 0) {
            euInWindow += voltage * r.amperes();
            voltageInWindow = Math.max(voltageInWindow, voltage);
            setChanged();
        }
        return r.amperes();
    }

    // --- взрывы ------------------------------------------------------------------------------------------------
    public boolean exploded() {
        return exploded;
    }

    /** BaseMetaTileEntity#doEnergyExplosion: только если запас ≥ 1/5; сила по выходному напряжению ×1/×2/×4. */
    public void doEnergyExplosion() {
        if (energy.capacity > 0 && energy.stored >= energy.capacity / 5) {
            long mult = energy.stored >= energy.capacity ? 4 : energy.stored >= energy.capacity / 2 ? 2 : 1;
            doExplosion(outputVoltage() * mult);
        }
    }

    /** MetaTileEntity#doExplosion: сила 1..10 по мощности, блок исчезает без выпадения (ExplosionItemDrops=false). */
    public static float explosionStrength(long power) {
        long[] v = {8, 32, 128, 512, 2048, 4096, 8192, 32768, 131072};
        float[] s = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 0; i < v.length; i++) if (power < v[i]) return s[i];
        return 10;
    }

    public void doExplosion(long power) {
        if (exploded || !(level instanceof ServerLevel sl)) return;
        exploded = true;
        BlockPos pos = worldPosition;
        long release = energy.stored;
        Deferred.run(sl, () -> {
            // блок мог уже исчезнуть (его разрушил чужой взрыв — цепная реакция): взрыв всё равно происходит
            boolean present = sl.getBlockEntity(pos) == this;
            // wirefire_on_explosion: выброс запаса во все стороны пакетом IV
            if (ru.iis.IISConfig.on(ru.iis.IISConfig.WIRE_FIRE)) {
                WorldEnergy.emit(sl, pos, List.of(Direction.values()), RELEASE_VOLTAGE, Math.max(1, release / RELEASE_VOLTAGE));
            }
            energy.stored = 0;
            if (present) sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            sl.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, explosionStrength(power), Level.ExplosionInteraction.BLOCK);
        });
    }

    // --- инвентарь ---------------------------------------------------------------------------------------------
    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    protected Direction front() {
        BlockState s = getBlockState();
        return s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING)
                : s.hasProperty(TransformerBlock.FACING) ? s.getValue(TransformerBlock.FACING) : Direction.NORTH;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putLong("eu", energy.stored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        energy.stored = in.getLongOr("eu", 0L);
    }
}
