package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.lv.Deferred;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.Maintenance;
import ru.iis.pollution.Pollution;
import ru.iis.pollution.PollutionLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая часть контроллера многоблочной машины GT5-II (GT_MetaTileEntity_MultiBlockBase): проверка формы раз в секунду,
 * люк обслуживания и случайные поломки, ход работы, снятие энергии с энергетических люков, выдача результата в выходные
 * шины и люки, загрязнение через глушители, взрыв всей машины, остановка. Форму и поиск рецепта задаёт наследник
 * (ЭДП, вакуумный морозильник D81, большие котлы D88).
 */
public abstract class MultiblockControllerBlockEntity extends BaseContainerBlockEntity implements MachineBlock.TickingMachine {
    /** GT explodeMultiblock: каждый люк и контроллер взрываются с мощностью V[8] (сила 10). */
    public static final long MULTIBLOCK_EXPLOSION = 131072;
    /** GT explodeMultiblock: загрязнение самой машины сверх взрывов её блоков. */
    public static final int MULTIBLOCK_EXPLOSION_POLLUTION = 300000;

    protected final Maintenance maintenance = new Maintenance();
    public boolean allowedToWork = true;
    protected boolean formed;
    protected int progress, maxProgress, eut, efficiency, efficiencyIncrease;
    protected List<ItemStack> pendingItems = List.of();
    protected List<FluidOutput> pendingFluids = List.of();
    protected final Parts parts = new Parts();
    /** GT mPollution: накопленное загрязнение, глушители выпускают его порциями по 10000. */
    protected int pollution;
    protected boolean mufflerBlocked;
    private boolean exploded;
    private int recheck;

    /** Люки, найденные при проверке формы. */
    static final class Parts {
        final List<BlockPos> energy = new ArrayList<>(), inputBuses = new ArrayList<>(), outputBuses = new ArrayList<>(),
                inputHatches = new ArrayList<>(), outputHatches = new ArrayList<>(), maintenance = new ArrayList<>(),
                mufflers = new ArrayList<>(), dynamos = new ArrayList<>();

        void clear() {
            energy.clear();
            inputBuses.clear();
            outputBuses.clear();
            inputHatches.clear();
            outputHatches.clear();
            maintenance.clear();
            mufflers.clear();
            dynamos.clear();
        }

        List<BlockPos> all() {
            List<BlockPos> l = new ArrayList<>();
            for (List<BlockPos> x : List.of(inputBuses, outputBuses, inputHatches, outputHatches, dynamos, mufflers, energy, maintenance)) l.addAll(x);
            return l;
        }
    }

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** GT checkMachine: собрать {@link #parts}. */
    abstract boolean checkStructure(ServerLevel sl);

    /** GT checkRecipe: забрать входы, задать eut, maxProgress, pendingItems/pendingFluids. */
    protected abstract boolean checkRecipe(ServerLevel sl);

    /** Нагрев катушек для экрана (у машин без катушек 0). */
    public int heat() {
        return 0;
    }

    /** Перекрыт ли выхлоп: загрязнению некуда выйти (GT polluteEnvironment вернул false). */
    public boolean mufflerBlocked() {
        return mufflerBlocked;
    }

    /** GT isCorrectMachinePart: без нужной детали в слоте контроллера (ротора у турбины) работающая машина выключается. */
    protected boolean correctMachinePart() {
        return true;
    }

    /** GT getMaxEfficiency − неисправности: выше неё КПД не растёт (газовая турбина с кислородом идёт выше 100 %). */
    protected int efficiencyCap() {
        return maintenance.maxEfficiency();
    }

    /** Износ детали в слоте контроллера раз в 1000 тиков работы (GT doRandomMaintenanceDamage: ротор турбины). */
    protected void componentWear(ServerLevel sl) {}

    /** Как часто простаивающая машина ищет работу и теряет 10 % КПД, если не нашла: в GT5 раз в 100 тиков. */
    protected int idleInterval() {
        return 20;
    }

    /** Вид люка в клетке (шины и люки MV–IV — как LV, D103); null — не люк. */
    protected HatchType hatchAt(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).getBlock() instanceof HatchBlock h ? h.type.kind() : null;
    }

    // --- работа ------------------------------------------------------------------------------------------------------
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || exploded) return;
        if (--recheck <= 0) {
            recheck = 20;
            formed = checkStructure(sl);
        }
        if (formed) {
            for (BlockPos p : parts.maintenance) {
                if (sl.getBlockEntity(p) instanceof MaintenanceHatchBlockEntity m) {
                    int fixes = m.takeFixes();
                    for (Maintenance.Problem pr : Maintenance.Problem.values()) if ((fixes & (1 << pr.ordinal())) != 0) maintenance.fix(pr);
                    m.machineMask = maintenance.mask();
                    m.machineSeen = sl.getGameTime();
                }
            }
            if (maintenance.repairStatus() > 0) {
                if (maxProgress > 0) {
                    if (!correctMachinePart()) {
                        stop(true);
                    } else if (maintenance.randomDamage(sl.getRandom()::nextInt, () -> componentWear(sl)) && runningTick(sl)) {
                        if (maxProgress > 0 && ++progress >= maxProgress) finish(sl);
                    }
                } else if (sl.getGameTime() % idleInterval() == 0 && allowedToWork) {
                    if (!checkRecipe(sl)) efficiency = Math.max(0, efficiency - 1000);
                }
            } else {
                stop(false);
            }
        } else {
            stop(false);
        }
        if (exploded) return;
        boolean active = maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
        afterTick(sl, active);
    }

    /** Дополнение такта: дым над глушителями работающей машины. */
    protected void afterTick(ServerLevel sl, boolean active) {
        if (!active || sl.getGameTime() % 10 != 0) return;
        for (BlockPos p : parts.mufflers) {
            BlockPos m = p.above();
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX() + 0.5, m.getY() + 0.2, m.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /** GT onRunningTick: энергия за тик; нет энергии — остановка с выключением. */
    protected boolean runningTick(ServerLevel sl) {
        if (!drainEnergy(sl, BlastLogic.drainPerTick(eut, efficiency))) {
            stop(true);
            return false;
        }
        return true;
    }

    /**
     * GT polluteEnvironment: загрязнение копится в машине; глушитель, перед которым воздух, выпускает в чанк порцию 10000.
     * Если накопилось 10000, а выпустить некуда (глушителей нет или они перекрыты), — false: машина должна встать.
     */
    protected boolean polluteEnvironment(ServerLevel sl, int amount) {
        pollution += amount;
        mufflerBlocked = false;
        for (BlockPos m : parts.mufflers) {
            if (pollution < PollutionLogic.MUFFLER_PORTION) break;
            BlockState ms = sl.getBlockState(m);
            if (!(ms.getBlock() instanceof HatchBlock) || !sl.getBlockState(m.relative(ms.getValue(HatchBlock.FACING))).isAir()) continue;
            Pollution.add(sl, m, PollutionLogic.mufflerOutput(PollutionLogic.MUFFLER_PORTION, ((HatchBlock) ms.getBlock()).type.tier));
            pollution -= PollutionLogic.MUFFLER_PORTION;
        }
        if (pollution >= PollutionLogic.MUFFLER_PORTION) {
            mufflerBlocked = true;
            return false;
        }
        return true;
    }

    /**
     * GT explodeMultiblock: +300000 загрязнения, деталь из слота контроллера пропадает, все люки и сам контроллер
     * взрываются с силой 10 (содержимое не выпадает, ExplosionItemDrops=false).
     */
    protected void explodeMultiblock(ServerLevel sl) {
        if (exploded) return;
        exploded = true;
        Pollution.add(sl, worldPosition, MULTIBLOCK_EXPLOSION_POLLUTION);
        clearContent();
        List<BlockPos> blocks = parts.all();
        blocks.add(worldPosition);
        for (BlockPos p : blocks) {
            if (sl.getBlockEntity(p) instanceof ElectricBlockEntity e) {
                e.doExplosion(MULTIBLOCK_EXPLOSION);
                continue;
            }
            Deferred.run(sl, () -> {
                if (sl.getBlockEntity(p) instanceof Container c) c.clearContent();
                Pollution.add(sl, p, PollutionLogic.MACHINE_EXPLOSION);
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                sl.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, ElectricBlockEntity.explosionStrength(MULTIBLOCK_EXPLOSION),
                        Level.ExplosionInteraction.BLOCK);
            });
        }
    }

    public boolean exploded() {
        return exploded;
    }

    private boolean drainEnergy(ServerLevel sl, long eu) {
        if (eu <= 0) return true;
        for (BlockPos p : parts.energy) {
            if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity e && e.drain(eu)) return true;
        }
        return false;
    }

    /** GT getMaxInputVoltage: сумма напряжений энергетических люков. */
    protected long maxInputVoltage(ServerLevel sl) {
        long v = 0;
        for (BlockPos p : parts.energy) if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity e) v += e.voltage();
        return v;
    }

    /** Слоты всех входных шин подряд (GT getStoredInputs). */
    protected List<ItemStack> inputSlots(ServerLevel sl) {
        List<ItemStack> slots = new ArrayList<>();
        for (BlockPos p : parts.inputBuses) if (sl.getBlockEntity(p) instanceof ItemBusBlockEntity b) slots.addAll(b.stacks());
        return slots;
    }

    protected void inputBusesChanged(ServerLevel sl) {
        for (BlockPos p : parts.inputBuses) if (sl.getBlockEntity(p) instanceof ItemBusBlockEntity b) b.setChanged();
    }

    /** GT depleteInput(FluidStack): забрать из первого входного люка с этой жидкостью, где её хватает целиком. */
    protected boolean depleteInput(ServerLevel sl, Fluid fluid, int amount) {
        for (BlockPos p : parts.inputHatches) {
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0
                    || !h.tank().resource().getFluid().isSame(fluid)) continue;
            if (h.tank().amount() < amount) continue;
            h.tank().drainInternal(amount);
            return true;
        }
        return false;
    }

    /** GT addOutput(FluidStack): в выходные люки по порядку; что не поместилось, пропадает. */
    protected void addFluidOutput(ServerLevel sl, Fluid fluid, int amount) {
        int left = amount;
        for (BlockPos p : parts.outputHatches) {
            if (left > 0 && sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) left -= h.fillInternal(fluid, left);
        }
    }

    /** Выдача жидкостей результата (ректификационная колонна раскладывает их по этажам). */
    protected void outputFluids(ServerLevel sl, List<FluidOutput> fluids) {
        for (FluidOutput f : fluids) addFluidOutput(sl, f.fluid(), f.amount());
    }

    /** Выдача: предметы по одному в выходные шины (с последнего слота), жидкости — в выходные люки; лишнее пропадает (как в GT5). */
    protected void finish(ServerLevel sl) {
        for (ItemStack st : pendingItems) {
            if (st.isEmpty()) continue;
            ItemStack s = st.copy();
            boolean ok = true;
            while (ok && !s.isEmpty()) {
                ok = false;
                ItemStack single = s.split(1);
                for (BlockPos p : parts.outputBuses) {
                    if (ok || !(sl.getBlockEntity(p) instanceof ItemBusBlockEntity b)) continue;
                    for (int i = b.getContainerSize() - 1; i >= 0 && !ok; i--) ok = b.addToSlot(i, single);
                }
            }
        }
        outputFluids(sl, pendingFluids);
        efficiency = Math.max(0, Math.min(efficiency + efficiencyIncrease, efficiencyCap()));
        pendingItems = List.of();
        pendingFluids = List.of();
        progress = maxProgress = 0;
        efficiencyIncrease = 0;
        setChanged();
        if (allowedToWork) checkRecipe(sl);
    }

    /** GT stopMachine. disable — машина выключается до включения киянкой (нехватка энергии, перекрытый выхлоп). */
    protected void stop(boolean disable) {
        if (maxProgress > 0 || eut != 0) setChanged();
        pendingItems = List.of();
        pendingFluids = List.of();
        eut = 0;
        efficiency = 0;
        progress = maxProgress = 0;
        efficiencyIncrease = 0;
        if (disable) allowedToWork = false;
    }

    public Maintenance maintenance() {
        return maintenance;
    }

    public boolean formed() {
        return formed;
    }

    public int progress() {
        return progress;
    }

    public int maxProgress() {
        return maxProgress;
    }

    public int efficiency() {
        return efficiency;
    }

    // --- GUI ---------------------------------------------------------------------------------------------------------
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> formed ? 1 : 0;
                case 1 -> maintenance.mask();
                case 2 -> efficiency / 100;
                case 3 -> progress / 20;
                case 4 -> maxProgress / 20;
                case 5 -> eut;
                case 6 -> heat();
                case 7 -> level instanceof ServerLevel sl ? (int) maxInputVoltage(sl) : 0;
                case 8 -> allowedToWork ? 1 : 0;
                case 9 -> mufflerBlocked() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 10;
        }
    };

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return NonNullList.create();
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {}

    @Override
    public int getContainerSize() {
        return 0;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("maintenance", maintenance.mask());
        out.putInt("runtime", maintenance.runtime());
        out.putBoolean("works", allowedToWork);
        out.putInt("progress", progress);
        out.putInt("maxProgress", maxProgress);
        out.putInt("eut", eut);
        out.putInt("efficiency", efficiency);
        out.putInt("efficiencyIncrease", efficiencyIncrease);
        out.putInt("pollution", pollution);
        if (!pendingItems.isEmpty()) out.store("pending", ItemStack.OPTIONAL_CODEC.listOf(), pendingItems);
        if (!pendingFluids.isEmpty()) out.store("pendingFluids", FluidOutput.CODEC.listOf(), pendingFluids);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        maintenance.setMask(in.getIntOr("maintenance", 0));
        maintenance.setRuntime(in.getIntOr("runtime", 0));
        allowedToWork = in.getBooleanOr("works", true);
        progress = in.getIntOr("progress", 0);
        maxProgress = in.getIntOr("maxProgress", 0);
        eut = in.getIntOr("eut", 0);
        efficiency = in.getIntOr("efficiency", 0);
        efficiencyIncrease = in.getIntOr("efficiencyIncrease", 0);
        pollution = in.getIntOr("pollution", 0);
        pendingItems = in.read("pending", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        pendingFluids = in.read("pendingFluids", FluidOutput.CODEC.listOf()).orElse(List.of());
    }
}
