package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.Maintenance;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая часть контроллера многоблочной машины GT5-II (GT_MetaTileEntity_MultiBlockBase): проверка формы раз в секунду,
 * люк обслуживания и случайные поломки, ход работы, снятие энергии с энергетических люков, выдача результата в выходные
 * шины и люки, остановка. Форму и поиск рецепта задаёт наследник (ЭДП, вакуумный морозильник, D81).
 */
public abstract class MultiblockControllerBlockEntity extends BaseContainerBlockEntity implements MachineBlock.TickingMachine {
    protected final Maintenance maintenance = new Maintenance();
    public boolean allowedToWork = true;
    protected boolean formed;
    protected int progress, maxProgress, eut, efficiency, efficiencyIncrease;
    protected List<ItemStack> pendingItems = List.of();
    protected List<FluidOutput> pendingFluids = List.of();
    protected final Parts parts = new Parts();
    private int recheck;

    /** Люки, найденные при проверке формы. */
    static final class Parts {
        final List<BlockPos> energy = new ArrayList<>(), inputBuses = new ArrayList<>(), outputBuses = new ArrayList<>(),
                inputHatches = new ArrayList<>(), outputHatches = new ArrayList<>(), maintenance = new ArrayList<>();
        BlockPos muffler;

        void clear() {
            energy.clear();
            inputBuses.clear();
            outputBuses.clear();
            inputHatches.clear();
            outputHatches.clear();
            maintenance.clear();
            muffler = null;
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

    /** Перекрыт ли выхлоп (у машин без глушителя никогда). */
    public boolean mufflerBlocked() {
        return false;
    }

    protected HatchType hatchAt(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).getBlock() instanceof HatchBlock h ? h.type : null;
    }

    // --- работа ------------------------------------------------------------------------------------------------------
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
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
                    if (maintenance.randomDamage(sl.getRandom()::nextInt) && runningTick(sl)) {
                        if (++progress >= maxProgress) finish(sl);
                    }
                } else if (sl.getGameTime() % 20 == 0 && allowedToWork) {
                    if (!checkRecipe(sl)) efficiency = Math.max(0, efficiency - 1000);
                }
            } else {
                stop(false);
            }
        } else {
            stop(false);
        }
        boolean active = maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
        afterTick(sl, active);
    }

    /** Дополнение такта для наследника (дым над глушителем ЭДП). */
    protected void afterTick(ServerLevel sl, boolean active) {}

    /** GT onRunningTick: энергия за тик; нет энергии — остановка с выключением. */
    protected boolean runningTick(ServerLevel sl) {
        if (!drainEnergy(sl, BlastLogic.drainPerTick(eut, efficiency))) {
            stop(true);
            return false;
        }
        return true;
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
        for (BlockPos p : parts.energy) if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity) v += EnergyHatchBlockEntity.VOLTAGE;
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

    /** Выдача: предметы по одному в выходные шины (с последнего слота), жидкости — в выходные люки; лишнее пропадает (как в GT5). */
    private void finish(ServerLevel sl) {
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
        for (FluidOutput f : pendingFluids) {
            int left = f.amount();
            for (BlockPos p : parts.outputHatches) {
                if (left > 0 && sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) left -= h.fillInternal(f.fluid(), left);
            }
        }
        efficiency = Math.max(0, Math.min(efficiency + efficiencyIncrease, maintenance.maxEfficiency()));
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
        pendingItems = in.read("pending", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        pendingFluids = in.read("pendingFluids", FluidOutput.CODEC.listOf()).orElse(List.of());
    }
}
