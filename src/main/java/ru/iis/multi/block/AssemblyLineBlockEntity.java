package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.item.DataStickItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.ControllerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сборочная линия (GT_MetaTileEntity_AssemblyLine, D110): ширина 5–16, высота 4, глубина 3. Рецепт берётся с флешки (в контроллере или люках данных);
 * каждая входная шина держит свой ингредиент, по порядку слева направо, каждый люк — свою жидкость, выходная шина ровно одна.
 */
public class AssemblyLineBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int MIN_WIDTH = 5, MAX_WIDTH = 16;
    private static final Set<HatchType> BOTTOM = Set.of(HatchType.MAINTENANCE, HatchType.INPUT_BUS, HatchType.OUTPUT_BUS, HatchType.INPUT_HATCH, HatchType.OUTPUT_HATCH);
    private static final Set<HatchType> TOP = Set.of(HatchType.ENERGY);

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<BlockPos> dataHatches = new ArrayList<>();

    public AssemblyLineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.ASSEMBLY_LINE_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.ASSEMBLY_LINE;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    /** GT validateControllerLayerBlocks: решётка или люк данных. */
    private boolean controllerLayer(ServerLevel sl, BlockPos p) {
        if (is(sl, p, MultiContent.GRATE_CASING.get())) return true;
        if (sl.getBlockState(p).getBlock() instanceof HatchBlock h && h.type.kind() == HatchType.DATA_ACCESS) {
            dataHatches.add(p.immutable());
            return true;
        }
        return false;
    }

    /** GT getFrontRelativeOffsetBackBlock: вправо (глядя на контроллер), назад, вверх. */
    private static BlockPos at(BlockPos origin, Direction right, Direction back, int r, int b, int up) {
        return origin.relative(right, r).relative(back, b).above(up);
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        dataHatches.clear();
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        Direction back = front.getOpposite(), right = front.getCounterClockWise();
        BlockPos o = worldPosition;
        int left = 0;
        while (left + 1 < MAX_WIDTH && controllerLayer(sl, at(o, right, back, -(left + 1), 0, 0))) left++;
        int rightN = 0;
        while (left + rightN + 1 < MAX_WIDTH && controllerLayer(sl, at(o, right, back, rightN + 1, 0, 0))) rightN++;
        if (left + rightN + 1 < MIN_WIDTH) return false;
        for (int i = -left; i <= rightN; i++) {
            if (!is(sl, at(o, right, back, i, 1, 0), MultiContent.ASSEMBLER_CASING.get())) return false;
            if (!controllerLayer(sl, at(o, right, back, i, 2, 0))) return false;
            BlockPos top = at(o, right, back, i, 1, 1);
            if (!is(sl, top, MultiContent.SOLID_STEEL_CASING.get()) && !addPart(sl, top, TOP)) return false;
            if (!is(sl, at(o, right, back, i, 0, -1), MultiContent.REINFORCED_GLASS.get()) || !is(sl, at(o, right, back, i, 2, -1), MultiContent.REINFORCED_GLASS.get())) return false;
            if (!is(sl, at(o, right, back, i, 1, -1), MultiContent.GEAR_BOX_TUNGSTEN_STEEL.get())) return false;
            for (int b = 0; b < 3; b++) {
                BlockPos p = at(o, right, back, i, b, -2);
                if (!is(sl, p, MultiContent.SOLID_STEEL_CASING.get()) && !addPart(sl, p, BOTTOM)) return false;
            }
        }
        return basicParts() && parts.outputBuses.size() == 1;
    }

    // --- рецепт -------------------------------------------------------------------------------------------------------
    /** GT getDataItems(2): флешки в контроллере и люках данных. */
    private List<ItemStack> dataSticks(ServerLevel sl) {
        List<ItemStack> l = new ArrayList<>();
        if (items.get(0).getItem() instanceof DataStickItem) l.add(items.get(0));
        for (BlockPos p : dataHatches) {
            if (sl.getBlockEntity(p) instanceof ItemBusBlockEntity b) {
                for (ItemStack s : b.stacks()) if (s.getItem() instanceof DataStickItem) l.add(s);
            }
        }
        return l;
    }

    /** GT mInputBusses.get(i).getStackInSlot(0): первая стопка шины. */
    private @Nullable ItemStack busStack(ServerLevel sl, int i) {
        if (i >= parts.inputBuses.size() || !(sl.getBlockEntity(parts.inputBuses.get(i)) instanceof ItemBusBlockEntity b)) return null;
        for (ItemStack s : b.stacks()) if (!s.isEmpty()) return s;
        return null;
    }

    private @Nullable FluidHatchBlockEntity hatch(ServerLevel sl, int i) {
        return i < parts.inputHatches.size() && sl.getBlockEntity(parts.inputHatches.get(i)) instanceof FluidHatchBlockEntity h ? h : null;
    }

    private boolean matches(ServerLevel sl, GtRecipe r) {
        for (int i = 0; i < r.inputs().size(); i++) {
            SizedIngredient s = r.inputs().get(i);
            ItemStack in = busStack(sl, i);
            if (in == null || !s.ingredient().test(in) || in.getCount() < s.count()) return false;
        }
        for (int i = 0; i < r.fluidInputs().size(); i++) {
            SizedFluidIngredient f = r.fluidInputs().get(i);
            FluidHatchBlockEntity h = hatch(sl, i);
            if (h == null || h.tank().amount() < f.amount() || !f.ingredient().test(new FluidStack(h.tank().resource().getFluid(), h.tank().amount()))) return false;
        }
        return true;
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        int tier = BlastLogic.machineTier(maxInputVoltage(sl));
        for (ItemStack stick : dataSticks(sl)) {
            GtRecipe r = DataStickItem.recipe(sl, stick);
            if (r == null || r.eut() > BlastLogic.V[tier] || !matches(sl, r)) continue;
            for (int i = 0; i < r.inputs().size(); i++) busStack(sl, i).shrink(r.inputs().get(i).count());
            inputBusesChanged(sl);
            for (int i = 0; i < r.fluidInputs().size(); i++) hatch(sl, i).tank().drainInternal(r.fluidInputs().get(i).amount());
            start(sl, r, tier);
            return true;
        }
        return false;
    }

    // --- контейнер ----------------------------------------------------------------------------------------------------
    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.assemblyLine(id, inventory, this, data);
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
        return stack.getItem() instanceof DataStickItem;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
    }
}
