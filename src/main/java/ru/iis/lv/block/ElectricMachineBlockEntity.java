package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.menu.ElectricMachineMenu;
import ru.iis.machine.Overclock;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.logic.ElectricMachineLogic;
import ru.iis.machine.recipe.GtRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Одноблочная электрическая машина LV (GT_MetaTileEntity_BasicMachine_GT_Recipe).
 * Энергия — с любой стороны, кроме лицевой; предметы выдаются со всех сторон, кроме лицевой;
 * сторона выхода — задняя (как у паровых машин). Жидкость (вода) заливается со всех сторон, кроме лицевой.
 * Машина даёт все выходы рецепта, сколько у неё выходных слотов (LV дробитель — 1 слот, т. е. только основной выход).
 */
public class ElectricMachineBlockEntity extends ElectricBlockEntity {
    public static final long VOLTAGE = VoltageTier.LV.voltage();

    private final ElectricMachineLogic logic = new ElectricMachineLogic(VOLTAGE);
    private final FluidTank tank;
    private List<ItemStack> pending = List.of();
    private boolean inventoryChanged = true;

    public ElectricMachineBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.ELECTRIC_MACHINE_BE.get(), pos, state, slotCount(state), VOLTAGE * 64, VOLTAGE);
        LvMachineType t = type(state);
        this.tank = new FluidTank(Math.max(1, t.tank), f -> t.tank > 0 && t.recipeMap != null
                && level instanceof ServerLevel sl && GtRecipes.acceptsFluid(sl, t.recipeMap, f), true, this::markDirty);
    }

    private static LvMachineType type(BlockState state) {
        return state.getBlock() instanceof ElectricMachineBlock b ? b.type : LvMachineType.MACERATOR;
    }

    private static int slotCount(BlockState state) {
        LvMachineType t = type(state);
        return t.inputs + t.outputs + 1;
    }

    /** Слот батарейки (GT: питание машины от батарейки, 32 EU/t). */
    public int batterySlot() {
        return items.size() - 1;
    }

    public LvMachineType type() {
        return type(getBlockState());
    }

    public Direction outputSide() {
        return front().getOpposite();
    }

    public ElectricMachineLogic logic() {
        return logic;
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return side != front();
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return type().tank > 0 && side != front() ? tank : null;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    protected void work(ServerLevel sl) {
        ItemStack bat = items.get(batterySlot());
        if (bat.getItem() instanceof ru.iis.lv.item.BatteryItem b && energy.stored < energy.capacity) {
            energy.stored += b.discharge(bat, Math.min(VOLTAGE, energy.capacity - energy.stored));
        }
        boolean check = inventoryChanged || sl.getGameTime() % 20 == 0;
        inventoryChanged = false;
        logic.tick(check, energy, new ElectricMachineLogic.Env() {
            @Override
            public int[] tryStart() {
                return start(sl);
            }

            @Override
            public void finish() {
                LvMachineType t = type();
                for (int i = 0; i < pending.size() && i < t.outputs; i++) {
                    ItemStack o = pending.get(i);
                    if (o.isEmpty()) continue;
                    int slot = t.inputs + i;
                    ItemStack cur = items.get(slot);
                    if (cur.isEmpty()) items.set(slot, o.copy());
                    else cur.grow(o.getCount());
                }
                pending = List.of();
                inventoryChanged = true;
                setChanged();
            }
        });
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != logic.active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, logic.active), 3);
    }

    private int @Nullable [] start(ServerLevel sl) {
        LvMachineType t = type();
        List<ItemStack> inputs = new ArrayList<>(items.subList(0, t.inputs));
        if (t.recipeMap == null) {
            ItemStack in = inputs.get(0);
            if (in.isEmpty()) return null;
            SingleRecipeInput input = new SingleRecipeInput(in);
            Optional<RecipeHolder<SmeltingRecipe>> r = sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, sl);
            if (r.isEmpty()) return null;
            ItemStack result = r.get().value().assemble(input);
            if (!fits(0, result)) return null;
            in.shrink(1);
            pending = List.of(result);
            setChanged();
            // GT_Recipe_Map_Furnace: 4 EU/t, 128 тиков
            Overclock.Result oc = Overclock.basicMachine(4, 128, VoltageTier.LV, 1);
            return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
        }
        FluidStack fluid = tank.amount() > 0 ? new FluidStack(tank.resource().getFluid(), tank.amount()) : FluidStack.EMPTY;
        var match = GtRecipes.find(sl, t.recipeMap, inputs, fluid, eut -> eut <= VOLTAGE);
        if (match.isEmpty()) return null;
        var m = match.get();
        var outs = m.recipe().outputs();
        for (int i = 0; i < outs.size() && i < t.outputs; i++) {
            if (!fits(i, outs.get(i).create())) return null;
        }
        for (int s = 0; s < m.take().length; s++) {
            if (m.take()[s] > 0) items.get(s).shrink(m.take()[s]);
        }
        for (var f : m.recipe().fluidInputs()) tank.drainInternal(f.amount());
        pending = m.recipe().rollOutputs(sl.getRandom());
        setChanged();
        Overclock.Result oc = Overclock.basicMachine(Math.max(1, m.recipe().eut()), m.recipe().duration(), VoltageTier.LV, 1);
        return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
    }

    private boolean fits(int outIndex, ItemStack result) {
        if (result.isEmpty()) return true;
        ItemStack out = items.get(type().inputs + outIndex);
        return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    // --- предметы снаружи ----------------------------------------------------------------------------------------
    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == front()) return new int[0];
        int[] all = new int[items.size() - 1];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < type().inputs && side != outputSide() && side != front();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= type().inputs && slot < batterySlot();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < type().inputs || (slot == batterySlot() && stack.getItem() instanceof ru.iis.lv.item.BatteryItem);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        inventoryChanged = true;
    }

    private void markDirty() {
        super.setChanged();
        inventoryChanged = true;
    }

    // --- GUI ------------------------------------------------------------------------------------------------------
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> logic.maxProgress > 0 ? (int) (Math.max(0, logic.progress) * 1000L / logic.maxProgress) : 0;
                case 1 -> logic.maxProgress > 0 ? 1 : 0;
                case 2 -> (int) Math.min(Short.MAX_VALUE, energy.stored);
                case 3 -> (int) Math.min(Short.MAX_VALUE, energy.capacity);
                case 4 -> logic.stuttering ? 1 : 0;
                case 5 -> tank.amount();
                case 6 -> tank.amount() > 0 ? BuiltInRegistries.FLUID.getId(tank.resource().getFluid()) : -1;
                case 7 -> logic.eut;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 8;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ElectricMachineMenu(id, inventory, this, data, type());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
        out.putInt("progress", logic.progress);
        out.putInt("maxProgress", logic.maxProgress);
        out.putInt("eut", logic.eut);
        out.putBoolean("stuttering", logic.stuttering);
        if (!pending.isEmpty()) out.store("pending", ItemStack.OPTIONAL_CODEC.listOf(), pending);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        items = NonNullList.withSize(slotCount(getBlockState()), ItemStack.EMPTY);
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
        logic.progress = in.getIntOr("progress", 0);
        logic.maxProgress = in.getIntOr("maxProgress", 0);
        logic.eut = in.getIntOr("eut", 0);
        logic.stuttering = in.getBooleanOr("stuttering", false);
        pending = in.read("pending", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
    }
}
