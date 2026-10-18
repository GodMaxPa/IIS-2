package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.logic.SteamMachineLogic;
import ru.iis.machine.menu.SteamMachineMenu;
import ru.iis.machine.recipe.GtRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Паровая машина GT5-II (GT_MetaTileEntity_BasicMachine_Bronze и наследники).
 * Лицевая сторона — FACING; сторона выхода (выпуск пара, выдача предметов) — задняя, как после установки в GT5.
 * Пар принимается со всех сторон, кроме лицевой.
 */
public class SteamMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MachineBlock.TickingMachine {
    private NonNullList<ItemStack> items;
    private final SteamMachineLogic logic = new SteamMachineLogic();
    private final FluidTank steam;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private boolean inventoryChanged = true;

    public SteamMachineBlockEntity(BlockPos pos, BlockState state) {
        super(IISMachines.STEAM_MACHINE_BE.get(), pos, state);
        this.items = NonNullList.withSize(inputCount(state) + 1, ItemStack.EMPTY);
        this.steam = new FluidTank(SteamMachineLogic.MAX_STEAM_UNITS * 2, f -> f.isSame(IISFluids.STEAM.get()), false, this::markDirty);
    }

    private static int inputCount(BlockState state) {
        return state.getBlock() instanceof SteamMachineBlock b ? b.type.inputs : 1;
    }

    public SteamMachineType type() {
        return getBlockState().getBlock() instanceof SteamMachineBlock b ? b.type : SteamMachineType.MACERATOR;
    }

    public SteamMachineType.Tier tier() {
        return getBlockState().getBlock() instanceof SteamMachineBlock b ? b.tier : SteamMachineType.Tier.BRONZE;
    }

    private int outputSlot() {
        return items.size() - 1;
    }

    private Direction front() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    /** Сторона выхода: выпуск пара и выдача предметов. */
    public Direction outputSide() {
        return front().getOpposite();
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return side == front() ? null : steam;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        int liters = steam.amount();
        logic.steamUnits = liters / 2;
        boolean check = inventoryChanged || sl.getGameTime() % 20 == 0;
        inventoryChanged = false;
        logic.tick(check, new SteamMachineLogic.Env() {
            @Override
            public boolean ventClear() {
                BlockPos p = worldPosition.relative(outputSide());
                return sl.getBlockState(p).getCollisionShape(sl, p).isEmpty();
            }

            @Override
            public void vent() {
                BlockPos p = worldPosition.relative(outputSide());
                sl.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p))) {
                    e.hurtServer(sl, sl.damageSources().hotFloor(), tier().ventDamage);
                }
            }

            @Override
            public int[] tryStart() {
                return start(sl);
            }

            @Override
            public void finish() {
                ItemStack out = items.get(outputSlot());
                if (out.isEmpty()) items.set(outputSlot(), pendingOutput.copy());
                else out.grow(pendingOutput.getCount());
                pendingOutput = ItemStack.EMPTY;
                inventoryChanged = true;
            }
        });
        steam.setAmount(IISFluids.STEAM.get(), logic.steamUnits * 2 + (liters % 2));
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != logic.active) {
            sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, logic.active), 3);
        }
    }

    /** checkRecipe: найти рецепт, проверить место под первый выход, забрать входы. */
    private int @Nullable [] start(ServerLevel sl) {
        SteamMachineType type = type();
        SteamMachineType.Tier tier = tier();
        List<ItemStack> inputs = new ArrayList<>(items.subList(0, outputSlot()));
        if (type.recipeMap == null) {
            ItemStack in = inputs.get(0);
            if (in.isEmpty()) return null;
            SingleRecipeInput input = new SingleRecipeInput(in);
            Optional<RecipeHolder<SmeltingRecipe>> r = sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, sl);
            if (r.isEmpty()) return null;
            ItemStack result = r.get().value().assemble(input);
            if (!fits(result)) return null;
            in.shrink(1);
            pendingOutput = result;
            setChanged();
            // GT_MetaTileEntity_Furnace_Bronze: 4 EU/t, 256 тиков; Steel: 12 EU/t, 128 тиков
            return SteamMachineLogic.scale(tier.highPressure, 4, 128);
        }
        var match = GtRecipes.find(sl, type.recipeMap, inputs, eut -> eut <= SteamMachineLogic.MAX_RECIPE_EUT);
        if (match.isEmpty()) return null;
        var m = match.get();
        ItemStack first = m.recipe().rollOutputs(sl.getRandom()).getFirst(); // паровые машины GT5 дают только первый выход
        if (!fits(first)) return null;
        for (int s = 0; s < m.take().length; s++) {
            if (m.take()[s] > 0) items.get(s).shrink(m.take()[s]);
        }
        pendingOutput = first;
        setChanged();
        return SteamMachineLogic.scale(tier.highPressure, m.recipe().eut(), m.recipe().duration());
    }

    private boolean fits(ItemStack result) {
        if (result.isEmpty()) return true;
        ItemStack out = items.get(outputSlot());
        return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    // --- WorldlyContainer (GT5: вход с любой стороны, кроме лицевой и стороны выхода; выдача — с любой, кроме лицевой) ---
    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == front()) return new int[0];
        int[] all = new int[items.size()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < outputSlot() && side != outputSide() && side != front();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == outputSlot();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < outputSlot();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        inventoryChanged = true;
    }

    /** Изменился только пар: сохранить, но не искать рецепт заново. */
    private void markDirty() {
        super.setChanged();
    }

    // --- GUI ------------------------------------------------------------------------------------------------------
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> Math.max(0, logic.progress);
                case 1 -> logic.maxProgress;
                case 2 -> steam.amount();
                case 3 -> (logic.stuttering || (logic.needsVenting && logic.maxProgress <= 0)) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

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

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new SteamMachineMenu(id, inventory, this, data, type(), tier());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        steam.serialize(out.child("steam"));
        out.putInt("progress", logic.progress);
        out.putInt("maxProgress", logic.maxProgress);
        out.putInt("eut", logic.eut);
        out.putBoolean("needsVenting", logic.needsVenting);
        out.putBoolean("stuttering", logic.stuttering);
        if (!pendingOutput.isEmpty()) out.store("pending", ItemStack.CODEC, pendingOutput);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(inputCount(getBlockState()) + 1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        steam.deserialize(in.childOrEmpty("steam"));
        logic.progress = in.getIntOr("progress", 0);
        logic.maxProgress = in.getIntOr("maxProgress", 0);
        logic.eut = in.getIntOr("eut", 0);
        logic.needsVenting = in.getBooleanOr("needsVenting", false);
        logic.stuttering = in.getBooleanOr("stuttering", false);
        pendingOutput = in.read("pending", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    public SteamMachineLogic logic() {
        return logic;
    }
}
