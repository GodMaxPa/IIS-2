package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.ControllerMenu;

import java.util.List;

/**
 * Основа буровых GT5-II (GT_MetaTileEntity_DrillerBase, D106). Форма 3×7×3: внизу площадка 3×3 из корпусов (контроллер —
 * в середине передней стороны, остальное — корпуса или люки), над её центром — столб из 3 корпусов, вокруг столба и на
 * нём — по 3 рамки. Из-под центра площадки вниз уходит шахтная труба: машина опускает её по блоку (блок под наконечником
 * пропадает), у бедрока работает на месте, потом поднимает трубы обратно. Трубы — в слоте контроллера (до 64, берутся и
 * из входных шин). Нужен запас энергии в люках не меньше 512 + 4 × напряжение; иначе машина выключается.
 */
public abstract class DrillerBlockEntity extends MultiblockControllerBlockEntity {
    public static final int STATE_DOWNWARD = 0, STATE_AT_BOTTOM = 1, STATE_UPWARD = 2, MAX_PIPES = 64;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    protected int workState = STATE_DOWNWARD;
    protected int yHead;

    protected DrillerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Уровень (I, II, III…) — номер блока контроллера в своём ряду. */
    protected abstract int level();

    protected abstract Block casing();

    protected abstract Block frame();

    /** GT getMinTier: меньшего напряжения люков машина не принимает. */
    protected abstract int minTier();

    /** GT checkHatches: обязательные люки. */
    protected abstract boolean checkHatches();

    /** GT setElectricityStats: eut и длительность цикла по напряжению и состоянию. */
    protected abstract void setElectricityStats(ServerLevel sl);

    /** Поднятые трубы — в слот контроллера (засыпщик) или в выходные шины. */
    protected abstract boolean allowPutPipesToController();

    public int workState() {
        return workState;
    }

    public int depth() {
        return worldPosition.getY() - yHead;
    }

    public ItemStack pipes() {
        return items.get(0);
    }

    protected BlockPos pipeColumn() {
        return worldPosition.relative(getBlockState().getValue(MachineBlock.FACING).getOpposite());
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos c = pipeColumn();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if (p.equals(worldPosition) || sl.getBlockState(p).is(casing()) || addHatch(sl, p)) continue;
                return false;
            }
        }
        if (!checkHatches() || BlastLogic.tier(maxInputVoltage(sl)) < minTier()) return false;
        for (int y = 1; y < 4; y++) {
            BlockPos p = c.above(y);
            if (!sl.getBlockState(p).is(casing()) || !sl.getBlockState(c.above(y + 3)).is(frame())) return false;
            for (Direction d : Direction.Plane.HORIZONTAL) if (!sl.getBlockState(p.relative(d)).is(frame())) return false;
        }
        return true;
    }

    /** GT addMaintenance/Input/Output/EnergyInputToMachineList. */
    private boolean addHatch(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == null) return false;
        switch (t) {
            case MAINTENANCE -> parts.maintenance.add(p);
            case INPUT_BUS -> parts.inputBuses.add(p);
            case OUTPUT_BUS -> parts.outputBuses.add(p);
            case INPUT_HATCH -> parts.inputHatches.add(p);
            case OUTPUT_HATCH -> parts.outputHatches.add(p);
            default -> {
                if (t.energy() && !t.dynamo()) parts.energy.add(p);
                else return false;
            }
        }
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected int idleInterval() {
        return 100;
    }

    /** Ступень напряжения люков (не меньше 1), как GT max(1, getTier(getMaxInputVoltage)). */
    protected int voltageTier(ServerLevel sl) {
        return Math.max(1, BlastLogic.tier(maxInputVoltage(sl)));
    }

    /** Начать цикл: eut и время уже заданы {@link #setElectricityStats}. */
    protected void setCycle(int eu, int ticks) {
        eut = eu;
        maxProgress = Math.max(1, ticks);
        progress = 0;
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        pendingItems = List.of();
        pendingFluids = List.of();
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        setElectricityStats(sl);
        int oldYHead = yHead;
        checkPipesAndSetYHead(sl);
        if (!isEnergyEnough(sl)) {
            stop(true);
            return false;
        }
        putMiningPipesFromInputs(sl);
        boolean ok = switch (workState) {
            case STATE_DOWNWARD -> workingDownward(sl, oldYHead);
            case STATE_AT_BOTTOM -> workingAtBottom(sl, oldYHead);
            case STATE_UPWARD -> workingUpward(sl, oldYHead);
            default -> false;
        };
        if (!ok) maxProgress = 0;
        setChanged();
        return ok && maxProgress > 0;
    }

    private boolean isEnergyEnough(ServerLevel sl) {
        long required = 512 + maxInputVoltage(sl) * 4;
        for (BlockPos p : parts.energy) {
            if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity e) {
                required -= e.energy.stored;
                if (required <= 0) return true;
            }
        }
        return false;
    }

    private void putMiningPipesFromInputs(ServerLevel sl) {
        ItemStack pipes = pipes();
        if (pipes.getCount() >= MAX_PIPES || (!pipes.isEmpty() && !pipes.is(MultiContent.MINING_PIPE.asItem()))) return;
        for (ItemStack s : inputSlots(sl)) {
            if (!s.is(MultiContent.MINING_PIPE.asItem())) continue;
            int have = pipes().isEmpty() ? 0 : pipes().getCount(), move = Math.min(s.getCount(), MAX_PIPES - have);
            if (move <= 0) break;
            if (have == 0) items.set(0, new ItemStack(MultiContent.MINING_PIPE.get(), move));
            else pipes().grow(move);
            s.shrink(move);
        }
        inputBusesChanged(sl);
    }

    private boolean is(ServerLevel sl, BlockPos p, Block b) {
        return sl.getBlockState(p).is(b);
    }

    /** GT checkPipesAndSetYHead: найти наконечник под площадкой (последнюю трубу сделать наконечником). */
    private void checkPipesAndSetYHead(ServerLevel sl) {
        BlockPos c = pipeColumn();
        int yDrill = worldPosition.getY();
        yHead = yDrill - 1;
        while (is(sl, c.atY(yHead), MultiContent.MINING_PIPE.get())) yHead--;
        if (is(sl, c.atY(yHead), MultiContent.MINING_PIPE_TIP.get()) || ++yHead == yDrill) return;
        sl.setBlock(c.atY(yHead), MultiContent.MINING_PIPE_TIP.get().defaultBlockState(), 3);
    }

    protected boolean hasPipes() {
        return pipes().is(MultiContent.MINING_PIPE.asItem()) && !pipes().isEmpty();
    }

    /** GT tryLowerPipe: 0 — опущена, 1 — дальше нельзя (бедрок, низ мира), 2 — нет труб. */
    protected int tryLowerPipe(ServerLevel sl, boolean simulate) {
        if (!hasPipes()) return 2;
        BlockPos c = pipeColumn(), below = c.atY(yHead - 1);
        if (yHead - 1 < sl.getMinY() || sl.getBlockState(below).getDestroySpeed(sl, below) < 0) return 1;
        if (!simulate) {
            sl.setBlock(below, MultiContent.MINING_PIPE_TIP.get().defaultBlockState(), 3);
            if (yHead != worldPosition.getY()) sl.setBlock(c.atY(yHead), MultiContent.MINING_PIPE.get().defaultBlockState(), 3);
            pipes().shrink(1);
        }
        return 0;
    }

    protected boolean reachingVoidOrBedrock(ServerLevel sl) {
        return yHead - 1 < sl.getMinY() || is(sl, pipeColumn().atY(yHead - 1), Blocks.BEDROCK);
    }

    /** GT tryPickPipe: забрать верхнюю… нижнюю трубу (наконечник поднимается на блок). */
    protected boolean tryPickPipe(ServerLevel sl) {
        if (yHead == worldPosition.getY()) return false;
        ItemStack pipe = new ItemStack(MultiContent.MINING_PIPE.get());
        if (allowPutPipesToController()) {
            if (pipes().isEmpty()) items.set(0, pipe);
            else if (pipes().is(pipe.getItem()) && pipes().getCount() < MAX_PIPES) pipes().grow(1);
            else pendingItems = List.of(pipe);
        } else {
            outputItem(sl, pipe);
        }
        BlockPos c = pipeColumn();
        if (is(sl, c.atY(yHead + 1), MultiContent.MINING_PIPE.get())) sl.setBlock(c.atY(yHead + 1), MultiContent.MINING_PIPE_TIP.get().defaultBlockState(), 3);
        sl.setBlock(c.atY(yHead), Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    /** GT addOutput(ItemStack): сразу в выходные шины; не поместилось — пропало. */
    protected void outputItem(ServerLevel sl, ItemStack stack) {
        ItemStack s = stack.copy();
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

    /** Выдать трубы из входных шин и из контроллера (нефтяная и рудная буровые перед подъёмом); true — было что выдать. */
    protected boolean returnStoredPipes(ServerLevel sl) {
        for (ItemStack s : inputSlots(sl)) {
            if (!s.is(MultiContent.MINING_PIPE.asItem())) continue;
            outputItem(sl, s);
            s.setCount(0);
            inputBusesChanged(sl);
            return true;
        }
        if (!pipes().isEmpty()) {
            outputItem(sl, pipes());
            items.set(0, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    protected boolean workingDownward(ServerLevel sl, int oldYHead) {
        switch (tryLowerPipe(sl, false)) {
            case 2 -> {
                maxProgress = 0;
                return false;
            }
            case 1 -> workState = STATE_AT_BOTTOM;
            default -> {}
        }
        return true;
    }

    protected boolean workingAtBottom(ServerLevel sl, int oldYHead) {
        workState = tryLowerPipe(sl, true) == 0 ? STATE_DOWNWARD : STATE_UPWARD;
        return true;
    }

    protected boolean workingUpward(ServerLevel sl, int oldYHead) {
        if (tryPickPipe(sl)) return true;
        workState = STATE_DOWNWARD;
        stop(true);
        return false;
    }

    // --- GUI и сохранение ---------------------------------------------------------------------------------------------
    public final ContainerData drillData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 10 -> depth();
                case 11 -> workState;
                case 12 -> pipes().getCount();
                default -> data.get(i);
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return ControllerMenu.DRILL_DATA;
        }
    };

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.drill(id, inventory, this, drillData);
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
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(MultiContent.MINING_PIPE.asItem());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("workState", workState);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        workState = in.getIntOr("workState", STATE_DOWNWARD);
    }
}
