package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.Fuels;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.EbfMenu;

/**
 * Большой дизельный двигатель (GT_MetaTileEntity_DieselEngine, D109). Форма 3×3×4: контроллер в центре передней стороны, вокруг него 8 заборных
 * корпусов (перед ними — воздух), дальше стабильные титановые корпуса, по оси — две титановые коробки передач и сзади динамо-люк. В серединах
 * сторон второго и третьего слоёв — люки; если они есть, сзади сверху обязателен глушитель. Топливо — дизельное, смазка 1 л в 72 тика.
 * Выход 2048 EU/t × КПД (до 20 % — ноль, КПД растёт на 0,15 % за тик). С кислородом 50 л/т: расход топлива вдвое больше, смазки вдвое больше,
 * КПД до 300 %, загрязнение 32 вместо 16 за тик. Выход больше напряжения динамо-люка — взрыв.
 */
public class CombustionEngineBlockEntity extends MultiblockControllerBlockEntity {
    public static final int BASE_EUT = 2048, OXYGEN_PER_TICK = 50, LUBRICANT_PERIOD = 72;

    private boolean boost;
    private int runtime;

    public CombustionEngineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.COMBUSTION_ENGINE_BE.get(), pos, state);
    }

    public boolean boosted() {
        return boost;
    }

    public long output() {
        return (long) eut * efficiency / 10000;
    }

    /** Для тестов: разгон с нуля занимает больше 10 минут. */
    public void forceEfficiency(int e) {
        efficiency = e;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Direction facing = getBlockState().getValue(MachineBlock.FACING), back = facing.getOpposite(), lateral = facing.getClockWise();
        if (!sl.getBlockState(worldPosition.relative(back, 1)).is(MultiContent.GEAR_BOX_TITANIUM.get())
                || !sl.getBlockState(worldPosition.relative(back, 2)).is(MultiContent.GEAR_BOX_TITANIUM.get())) return false;
        BlockPos topBack = worldPosition.relative(back, 2).above();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                if (!sl.getBlockState(worldPosition.relative(lateral, i).above(j).relative(facing)).isAir()) return false;
                for (int k = 0; k < 4; k++) {
                    BlockPos p = worldPosition.relative(lateral, i).above(j).relative(back, k);
                    boolean casing = sl.getBlockState(p).is(MultiContent.STABLE_TITANIUM_CASING.get());
                    if ((i == 0 || j == 0) && (k == 1 || k == 2)) {
                        if (casing) continue;
                        if (hatchAt(sl, topBack) != HatchType.MUFFLER || !addHatch(sl, p)) return false;
                    } else if (k == 0) {
                        if (!sl.getBlockState(p).is(MultiContent.ENGINE_INTAKE_CASING.get())) return false;
                    } else if (!casing) {
                        return false;
                    }
                }
            }
        }
        BlockPos dynamo = worldPosition.relative(back, 3);
        HatchType t = hatchAt(sl, dynamo);
        if (t == null || !t.dynamo()) return false;
        parts.dynamos.add(dynamo);
        return true;
    }

    private boolean addHatch(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == null) return false;
        switch (t) {
            case MAINTENANCE -> parts.maintenance.add(p);
            case INPUT_HATCH -> parts.inputHatches.add(p);
            case INPUT_BUS -> parts.inputBuses.add(p);
            case OUTPUT_HATCH -> parts.outputHatches.add(p);
            case OUTPUT_BUS -> parts.outputBuses.add(p);
            case MUFFLER -> parts.mufflers.add(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected int idleInterval() {
        return 100;
    }

    @Override
    protected int efficiencyCap() {
        return (boost ? 30000 : 10000) - (10000 - maintenance.maxEfficiency());
    }

    private boolean hasFluid(ServerLevel sl, Fluid f) {
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0 && h.tank().resource().getFluid().isSame(f)) return true;
        }
        return false;
    }

    /** GT checkRecipe: цикл в один тик. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        Fluid lubricant = GtFluids.get("lubricant").source().get(), oxygen = GtFluids.get("oxygen").source().get();
        for (BlockPos p : parts.inputHatches) {
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0) continue;
            Fluid f = h.tank().resource().getFluid();
            int value = Fuels.power("diesel", f);
            if (value <= 0) continue;
            int use = Math.max((boost ? 4096 : 2048) / value, 1);
            if (!depleteInput(sl, f, use)) continue;
            boost = depleteInput(sl, oxygen, OXYGEN_PER_TICK);
            if (!hasFluid(sl, lubricant)) {
                eut = 0;
                maxProgress = 0;
                return false;
            }
            if (runtime % LUBRICANT_PERIOD == 0) depleteInput(sl, lubricant, boost ? 2 : 1);
            runtime++;
            eut = efficiency < 2000 ? 0 : BASE_EUT;
            maxProgress = 1;
            progress = 0;
            efficiencyIncrease = 15;
            pendingItems = java.util.List.of();
            pendingFluids = java.util.List.of();
            for (BlockPos d : parts.dynamos) {
                if (sl.getBlockEntity(d) instanceof DynamoHatchBlockEntity dyn && dyn.voltage() < output()) {
                    explodeMultiblock(sl);
                    return false;
                }
            }
            setChanged();
            return true;
        }
        eut = 0;
        efficiency = 0;
        maxProgress = 0;
        return false;
    }

    /** GT onRunningTick: энергия в динамо-люк и загрязнение через глушитель. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        long out = output();
        for (BlockPos p : parts.dynamos) {
            if (out > 0 && sl.getBlockEntity(p) instanceof DynamoHatchBlockEntity d) {
                d.inject(out);
                break;
            }
        }
        if (!polluteEnvironment(sl, boost ? 32 : 16)) {
            stop(true);
            return false;
        }
        return true;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.freezer(id, inventory, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("boost", boost);
        out.putInt("engineRuntime", runtime);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        boost = in.getBooleanOr("boost", false);
        runtime = in.getIntOr("engineRuntime", 0);
    }
}
