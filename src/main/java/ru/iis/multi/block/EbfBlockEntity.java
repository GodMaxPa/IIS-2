package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.Maintenance;
import ru.iis.multi.menu.EbfMenu;
import ru.iis.pollution.Pollution;
import ru.iis.pollution.PollutionLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * Электрическая доменная печь (GT_MetaTileEntity_ElectricBlastFurnace + GT_MetaTileEntity_MultiBlockBase).
 *
 * Форма 3×3×4, контроллер — в середине нижнего слоя лицом наружу:
 *  - низ (кроме контроллера): теплоустойчивые корпуса или люки (энергия, входные шины/люки, выходные шины, обслуживание);
 *  - слои 2–3: по 8 катушек одного вида вокруг пустой середины;
 *  - верх: в центре выхлопной люк, вокруг — корпуса или выходные люки жидкости.
 */
public class EbfBlockEntity extends BaseContainerBlockEntity implements MachineBlock.TickingMachine {
    public static final int CUPRONICKEL_HEAT = 1800;

    private final Maintenance maintenance = new Maintenance();
    public boolean allowedToWork = true;
    private boolean formed, mufflerBlocked;
    private int heat;
    private int progress, maxProgress, eut, efficiency, efficiencyIncrease, pollution;
    private List<ItemStack> pendingItems = List.of();
    private List<FluidOutput> pendingFluids = List.of();
    private final Parts parts = new Parts();
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

    public EbfBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.EBF_BE.get(), pos, state);
    }

    // --- форма ----------------------------------------------------------------------------------------------------
    private HatchType hatchAt(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).getBlock() instanceof HatchBlock h ? h.type : null;
    }

    private boolean isCasing(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).is(MultiContent.HEAT_PROOF_CASING.get());
    }

    /** GT checkMachine. */
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        heat = 0;
        Direction back = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        BlockPos center = worldPosition.relative(back);
        if (!sl.getBlockState(center.above(1)).isAir() || !sl.getBlockState(center.above(2)).isAir()) return false;
        BlockPos muffler = center.above(3);
        if (hatchAt(sl, muffler) != HatchType.MUFFLER) return false;
        parts.muffler = muffler;
        if (!sl.getBlockState(center.above(2).east()).is(MultiContent.COIL_CUPRONICKEL.get())) return false;
        heat = CUPRONICKEL_HEAT;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos c = center.offset(dx, 0, dz);
                if (!sl.getBlockState(c.above(1)).is(MultiContent.COIL_CUPRONICKEL.get())) return false;
                if (!sl.getBlockState(c.above(2)).is(MultiContent.COIL_CUPRONICKEL.get())) return false;
                BlockPos top = c.above(3);
                HatchType t = hatchAt(sl, top);
                if (t == HatchType.OUTPUT_HATCH) parts.outputHatches.add(top);
                else if (!isCasing(sl, top)) return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos b = center.offset(dx, 0, dz);
                if (b.equals(worldPosition)) continue;
                HatchType t = hatchAt(sl, b);
                if (t == null) {
                    if (!isCasing(sl, b)) return false;
                    continue;
                }
                switch (t) {
                    case MAINTENANCE -> parts.maintenance.add(b);
                    case INPUT_BUS -> parts.inputBuses.add(b);
                    case INPUT_HATCH -> parts.inputHatches.add(b);
                    case ENERGY -> parts.energy.add(b);
                    case OUTPUT_BUS -> parts.outputBuses.add(b);
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
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
        if (active && parts.muffler != null && sl.getGameTime() % 10 == 0) {
            BlockPos m = parts.muffler.above();
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX() + 0.5, m.getY() + 0.2, m.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /** GT onRunningTick + polluteEnvironment. */
    private boolean runningTick(ServerLevel sl) {
        if (!drainEnergy(sl, BlastLogic.drainPerTick(eut, efficiency))) {
            stop(true);
            return false;
        }
        pollution += BlastLogic.POLLUTION_PER_TICK;
        mufflerBlocked = false;
        if (pollution >= PollutionLogic.MUFFLER_PORTION && parts.muffler != null) {
            BlockState ms = sl.getBlockState(parts.muffler);
            Direction mf = ms.getValue(HatchBlock.FACING);
            if (sl.getBlockState(parts.muffler.relative(mf)).isAir()) {
                Pollution.add(sl, parts.muffler, PollutionLogic.mufflerOutput(PollutionLogic.MUFFLER_PORTION, 1));
                pollution -= PollutionLogic.MUFFLER_PORTION;
            }
        }
        if (pollution >= PollutionLogic.MUFFLER_PORTION) {
            mufflerBlocked = true;
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

    private long maxInputVoltage(ServerLevel sl) {
        long v = 0;
        for (BlockPos p : parts.energy) if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity) v += EnergyHatchBlockEntity.VOLTAGE;
        return v;
    }

    /** GT checkRecipe. */
    private boolean checkRecipe(ServerLevel sl) {
        List<ItemStack> slots = new ArrayList<>();
        for (BlockPos p : parts.inputBuses) if (sl.getBlockEntity(p) instanceof ItemBusBlockEntity b) slots.addAll(b.stacks());
        if (slots.stream().allMatch(ItemStack::isEmpty)) return false;
        FluidHatchBlockEntity fluidHatch = null;
        FluidStack fluid = FluidStack.EMPTY;
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                fluidHatch = h;
                fluid = h.fluid();
                break;
            }
        }
        int tier = BlastLogic.machineTier(maxInputVoltage(sl));
        long vmax = BlastLogic.V[tier];
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.BLAST)) {
            GtRecipe r = h.value();
            if (r.eut() > vmax || r.temperature() > heat || !r.fluidMatches(fluid)) continue;
            int[] take = r.allocate(slots);
            if (take == null) continue;
            for (int s = 0; s < take.length; s++) if (take[s] > 0) slots.get(s).shrink(take[s]);
            for (var f : r.fluidInputs()) if (fluidHatch != null) fluidHatch.tank().drainInternal(f.amount());
            for (BlockPos p : parts.inputBuses) if (sl.getBlockEntity(p) instanceof ItemBusBlockEntity b) b.setChanged();
            efficiency = maintenance.maxEfficiency();
            efficiencyIncrease = 10000;
            BlastLogic.Run run = BlastLogic.overclock(r.eut(), r.duration(), tier, heat, r.temperature());
            eut = run.eut();
            maxProgress = run.duration();
            progress = 0;
            pendingItems = r.rollOutputs(sl.getRandom());
            pendingFluids = r.fluidOutputs();
            setChanged();
            return true;
        }
        return false;
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
    private void stop(boolean disable) {
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
                case 6 -> heat;
                case 7 -> level instanceof ServerLevel sl ? (int) maxInputVoltage(sl) : 0;
                case 8 -> allowedToWork ? 1 : 0;
                case 9 -> mufflerBlocked ? 1 : 0;
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
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new EbfMenu(id, inventory, data);
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
