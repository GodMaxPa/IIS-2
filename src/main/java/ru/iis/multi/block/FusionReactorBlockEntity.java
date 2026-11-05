package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import ru.iis.lv.LvContent;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.ControllerMenu;

import java.util.List;
import java.util.Set;

/**
 * Термоядерный реактор Mark I, II и III (GT_MetaTileEntity_FusionComputer, _FusionComputer1, 2, 3; вторые два — D121): больше запас, больше отдача люков,
 * катушки и корпуса свои, люки от ZPM и UV; рецепты с дешёвым зажиганием на них идут быстрее (GT overclock: EU/t и скорость ×2 и ×4).
 * Особенности GT: нет обслуживания и поломок; запас контроллера 160003000 EU × люки / 16, каждый люк отдаёт в него до 2048 EU за тик;
 * зажигание разом снимает со запаса mSpecialValue (в рецепте — temp), дальше EU/t рецепта; следующая порция без перерыва зажигания не платит;
 * простаивающий реактор ищет рецепт раз в 100 тиков.
 * Отличия: центр кольца за спиной контроллера, а не перед лицом (иначе лицо смотрит внутрь кольца); входы тратятся, только если запаса хватает на зажигание: в GT они сгорали впустую.
 */
public class FusionReactorBlockEntity extends RecipeMultiblockBlockEntity {
    public static final long STORE_MARK1 = 160003000L, STORE_MARK2 = 320006000L, STORE_MARK3 = 640010000L, HATCH_INPUT = 2048;
    public static final int RING = 5, IDLE_CHECK = 100;

    private long stored;
    private int recheck;

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.FUSION_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.FUSION;
    }

    public long stored() {
        return stored;
    }

    public void setStored(long eu) {
        stored = eu;
    }

    /** 1, 2 или 3: по блоку контроллера. */
    public int mark() {
        Block b = getBlockState().getBlock();
        return b == MultiContent.FUSION_COMPUTER_UV.get() ? 3 : b == MultiContent.FUSION_COMPUTER_ZPM.get() ? 2 : 1;
    }

    /** GT tier(): уровень люков. */
    private int hatchTier() {
        return 5 + mark();
    }

    /** GT tierOverclock(): 1, 2, 4. */
    private int tierOverclock() {
        return 1 << (mark() - 1);
    }

    /** GT maxEUStore. */
    public long capacity() {
        long store = switch (mark()) {
            case 3 -> STORE_MARK3;
            case 2 -> STORE_MARK2;
            default -> STORE_MARK1;
        };
        return store * Math.min(16, parts.energy.size()) / 16;
    }

    /** GT overclock(mStartEnergy). */
    public int overclock(long start) {
        return switch (mark()) {
            case 3 -> start < 160000000L ? 4 : start < 320000000L ? 2 : 1;
            case 2 -> start < 160000000L ? 2 : 1;
            default -> 1;
        };
    }

    /** На экране в поле нагрева: запас, миллионы EU (данные окна — 16 бит). */
    @Override
    public int heat() {
        return (int) (stored / 1000000L);
    }

    /** На экране в поле напряжения: вместимость, миллионы EU. */
    @Override
    protected long maxInputVoltage(ServerLevel sl) {
        return capacity() / 1000000L;
    }

    /** Центр кольца. */
    public BlockPos center() {
        return worldPosition.relative(getBlockState().getValue(MachineBlock.FACING).getOpposite(), RING);
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos c = center();
        int mark = mark();
        Block casing = mark == 3 ? MultiContent.CASING_FUSION_2.get() : mark == 2 ? MultiContent.CASING_FUSION.get() : LvContent.CASING_LUV.get();
        Block coil = mark == 1 ? MultiContent.COIL_SUPERCONDUCTOR.get() : MultiContent.FUSION_COIL.get();
        for (int[] d : FusionShape.COILS) if (!sl.getBlockState(c.offset(d[0], d[1], d[2])).is(coil)) return false;
        for (int[] d : FusionShape.CASINGS) {
            BlockPos p = c.offset(d[0], d[1], d[2]);
            if (!p.equals(worldPosition) && !sl.getBlockState(p).is(casing)) return false;
        }
        if (!part(sl, c, FusionShape.ENERGY, Set.of(), parts.energy, casing)) return false;
        if (!part(sl, c, FusionShape.OUTPUT, Set.of(HatchType.OUTPUT_HATCH), parts.outputHatches, casing)) return false;
        if (!part(sl, c, FusionShape.INPUT, Set.of(HatchType.INPUT_HATCH), parts.inputHatches, casing)) return false;
        return !parts.energy.isEmpty() && !parts.outputHatches.isEmpty() && parts.inputHatches.size() >= 2;
    }

    /** GT addIf…: корпус или люк нужного вида уровня реактора и выше; пустой набор — энергетический люк. */
    private boolean part(ServerLevel sl, BlockPos c, int[][] cells, Set<HatchType> kinds, List<BlockPos> into, Block casing) {
        for (int[] d : cells) {
            BlockPos p = c.offset(d[0], d[1], d[2]);
            BlockState s = sl.getBlockState(p);
            if (s.is(casing)) continue;
            if (!(s.getBlock() instanceof HatchBlock h)) return false;
            boolean match = kinds.isEmpty() ? h.type.energy() && !h.type.dynamo() : kinds.contains(h.type.kind());
            if (!match || h.type.tier < hatchTier()) return false;
            into.add(p);
        }
        return true;
    }

    /** GT onPostTick целиком свой: без обслуживания, с запасом в контроллере. */
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (--recheck <= 0) {
            recheck = 20;
            formed = checkStructure(sl);
        }
        if (formed) {
            long in = HATCH_INPUT * tierOverclock();
            for (BlockPos p : parts.energy) {
                if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity e && stored + in < capacity() && e.energy.stored >= in) {
                    e.energy.stored -= in;
                    stored += in;
                }
            }
            if (maxProgress > 0) {
                stored -= eut;
                if (stored <= 0) {
                    stored = 0;
                    stop(true);
                } else if (++progress >= maxProgress) {
                    finish(sl);
                    if (allowedToWork) start(sl, false);
                }
            } else if (allowedToWork && sl.getGameTime() % IDLE_CHECK == 0) {
                checkRecipe(sl);
            }
        } else if (maxProgress > 0) {
            stop(false);
        }
        boolean active = maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        return start(sl, true);
    }

    /** ignite: реактор простаивал и должен заплатить за зажигание; иначе плазма ещё горит (GT вызывает checkRecipe без списания). */
    private boolean start(ServerLevel sl, boolean ignite) {
        List<FluidStack> fluids = storedFluids(sl);
        if (fluids.size() < 2) return false;
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.FUSION)) {
            GtRecipe r = h.value();
            int[] pick = matchFluids(r, fluids);
            if (pick == null || r.temperature() > capacity() || (ignite && r.temperature() > stored)) continue;
            drainFluids(sl, r, pick);
            if (ignite) stored -= r.temperature();
            int oc = overclock(r.temperature());
            eut = r.eut() * oc;
            maxProgress = Math.max(1, r.duration() / oc);
            progress = 0;
            efficiency = 10000;
            efficiencyIncrease = 10000;
            pendingItems = List.of();
            pendingFluids = r.fluidOutputs();
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.fusion(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("FusionStore", stored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stored = in.getLongOr("FusionStore", 0L);
    }
}
