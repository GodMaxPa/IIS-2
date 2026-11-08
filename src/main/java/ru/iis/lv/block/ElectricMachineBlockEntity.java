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
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.menu.ElectricMachineMenu;
import ru.iis.machine.Overclock;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.recipe.FluidOutput;
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
    /** GT V[mTier]: 32 у LV, 128 у MV (D81). */
    private final long voltage;
    private final ElectricMachineLogic logic;
    private final FluidTank tank;
    /** GT mOutputFluid: выходной бак той же ёмкости, снаружи только слив (LV III, D76). */
    private final FluidTank outTank;
    private final net.neoforged.neoforge.transfer.CombinedResourceHandler<FluidResource> fluids;
    private final ResourceHandler<FluidResource> drainOnly;
    private @Nullable Direction itemOut, fluidOut;
    private List<ItemStack> pending = List.of();
    private FluidStack pendingFluid = FluidStack.EMPTY;
    private boolean inventoryChanged = true;
    /** GT mItemTransfer / mFluidTransfer: кнопки автовыгрузки (8,63) жидкость и (26,63) предметы. */
    public boolean itemTransfer = false, fluidTransfer = false;
    /** GT isAllowedToWork: выключается контроллером-покрытием или киянкой. */
    public boolean allowedToWork = true;

    public ElectricMachineBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.ELECTRIC_MACHINE_BE.get(), pos, state, slotCount(state), type(state).voltage() * 64, type(state).voltage());
        LvMachineType t = type(state);
        this.voltage = t.voltage();
        this.logic = new ElectricMachineLogic(voltage);
        this.tank = new FluidTank(Math.max(1, t.tank), f -> t.id.equals("replicator") || t.id.equals("organic_replicator") ? f == ru.iis.fluid.GtFluids.get("uumatter").source().get() : t.tank > 0 && t.recipeMap != null
                && level instanceof ServerLevel sl && GtRecipes.acceptsFluid(sl, t.recipeMap, f), true, this::markDirty);
        this.outTank = new FluidTank(Math.max(1, t.tank), f -> false, true, this::markDirty);
        this.fluids = new net.neoforged.neoforge.transfer.CombinedResourceHandler<>(tank, outTank);
        this.drainOnly = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(fluids) {
            @Override
            public int insert(int index, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return 0;
            }

            @Override
            public int insert(FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return 0;
            }
        };
    }

    private static LvMachineType type(BlockState state) {
        return state.getBlock() instanceof ElectricMachineBlock b ? b.type : LvMachineType.MACERATOR;
    }

    private static int slotCount(BlockState state) {
        LvMachineType t = type(state);
        return t.inputs + t.outputs + 2;
    }

    /** Слот батарейки (GT: питание машины от батарейки, 32 EU/t). */
    public int batterySlot() {
        return items.size() - 2;
    }

    /** GT getSpecialSlot, в окне (125,63): флешка или сфера данных. Флешка, положенная в старых версиях в слот батарейки, тоже читается. */
    public int specialSlot() {
        return items.get(items.size() - 1).isEmpty() && (items.get(batterySlot()).getItem() instanceof ru.iis.lv.item.DataStickItem || items.get(batterySlot()).getItem() == LvContent.DATA_ORB.get()) ? batterySlot() : items.size() - 1;
    }

    public LvMachineType type() {
        return type(getBlockState());
    }

    /** GT getFrontFacing: выход предметов; после установки — задняя сторона (GT_MetaTileEntity_BasicMachine.doDisplayThings). */
    public Direction outputSide() {
        return itemOut != null && itemOut != front() ? itemOut : front().getOpposite();
    }

    /** GT getFluidFacing: выход жидкостей (Ctrl + ключ), по умолчанию тоже задняя сторона. */
    public Direction fluidOutputSide() {
        return fluidOut != null && fluidOut != front() ? fluidOut : front().getOpposite();
    }

    /** Ключ (D101): true — сторона выхода изменилась; на лицевую сторону выход не ставится. */
    public boolean setOutputSide(Direction side, boolean fluid) {
        if (side == front() || side == (fluid ? fluidOutputSide() : outputSide())) return false;
        if (fluid) fluidOut = side;
        else itemOut = side;
        coversChanged();
        return true;
    }

    /** GT setMainFacing: выход, оказавшийся на новой лицевой стороне, уходит на заднюю. */
    void mainFacingChanged() {
        if (itemOut == front()) itemOut = null;
        if (fluidOut == front()) fluidOut = null;
        coversChanged();
    }

    public ElectricMachineLogic logic() {
        return logic;
    }

    public FluidTank tank() {
        return tank;
    }

    public FluidTank outTank() {
        return outTank;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return side != front();
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        if (type().tank <= 0 || side == front()) return null;
        // GT isLiquidInput: со стороны выхода жидкостей не заливается
        return side != null && side == fluidOutputSide() ? drainOnly : fluids;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    protected void work(ServerLevel sl) {
        logic.noMinimum = type().id.equals("seismic_prospector");
        ItemStack bat = items.get(batterySlot());
        if (bat.getItem() instanceof ru.iis.lv.item.BatteryItem b && b.isBattery() && energy.stored < energy.capacity) {
            energy.stored += b.discharge(bat, Math.min(voltage, energy.capacity - energy.stored));
        }
        boolean check = inventoryChanged || sl.getGameTime() % 20 == 0;
        inventoryChanged = false;
        boolean[] succeeded = {false};
        logic.tick(check && allowedToWork, energy, new ElectricMachineLogic.Env() {
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
                if (!pendingFluid.isEmpty()) {
                    outTank.setAmount(pendingFluid.getFluid(), outTank.amount() + pendingFluid.getAmount());
                    pendingFluid = FluidStack.EMPTY;
                }
                inventoryChanged = true;
                succeeded[0] = true;
                setChanged();
            }
        });
        if (itemTransfer && (check || succeeded[0])) autoOutput(sl);
        if (fluidTransfer && outTank.amount() > 0 && (check || succeeded[0])) autoOutputFluid(sl);
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != logic.active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, logic.active), 3);
    }

    /**
     * GT BasicMachine.doesAutoOutput: выходные слоты выгружаются в инвентарь со стороны выхода (задняя сторона),
     * 1 EU за предмет; нужна энергия не меньше 128 EU.
     */
    private void autoOutput(ServerLevel sl) {
        if (energy.stored < 128) return;
        Direction side = outputSide();
        ResourceHandler<ItemResource> target = sl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (target == null) return;
        LvMachineType t = type();
        for (int s = t.inputs; s < t.inputs + t.outputs; s++) {
            ItemStack st = items.get(s);
            if (st.isEmpty()) continue;
            int moved;
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(target, ItemResource.of(st), st.getCount(), tx);
                tx.commit();
            }
            if (moved > 0) {
                st.shrink(moved);
                energy.stored = Math.max(0, energy.stored - moved);
                super.setChanged();
            }
        }
    }

    /** GT mFluidTransfer: выходная жидкость сливается в бак со стороны выхода жидкостей, не больше 1000 × (int) 1.5^mTier л за раз (LV 1000, MV 2000). */
    private void autoOutputFluid(ServerLevel sl) {
        Direction side = fluidOutputSide();
        ResourceHandler<FluidResource> target = sl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (target == null) return;
        FluidResource r = outTank.resource();
        int moved;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            moved = target.insert(r, Math.min(outTank.amount(), ElectricMachineLogic.fluidOutputPerOperation(type().tier)), tx);
            tx.commit();
        }
        if (moved > 0) outTank.drainInternal(moved);
    }

    // --- покрытия ----------------------------------------------------------------------------------------------------
    @Override
    public @Nullable ResourceHandler<FluidResource> rawFluids(@Nullable Direction side) {
        return type().tank > 0 ? fluids : null;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public boolean allowedToWork() {
        return allowedToWork;
    }

    @Override
    public void setAllowedToWork(boolean allowed) {
        if (allowedToWork != allowed) {
            allowedToWork = allowed;
            setChanged();
        }
    }

    @Override
    public int progress() {
        return Math.max(0, logic.progress);
    }

    @Override
    public int maxProgress() {
        return logic.maxProgress;
    }

    /** Кнопки GUI: 0 — автовыгрузка жидкости, 1 — автовыгрузка предметов. */
    public void toggleTransfer(int button) {
        if (button == 0) fluidTransfer = !fluidTransfer;
        else if (button == 1) itemTransfer = !itemTransfer;
        setChanged();
    }

    private int @Nullable [] start(ServerLevel sl) {
        LvMachineType t = type();
        List<ItemStack> inputs = new ArrayList<>(items.subList(0, t.inputs));
        if (t.id.equals("scanner")) return scan(sl, t);
        if (t.id.equals("seismic_prospector")) return prospect(sl, t);
        if (t.id.equals("replicator")) return replicate(sl, t);
        if (t.id.equals("organic_replicator")) return replicateSeeds(sl, t);
        if (t.id.equals("recycler")) {
            // GT_Recipe_Map_Recycler: всё, кроме самого утиля, в утиль с шансом 12,5 %, 45 тиков, 1 EU/t (D119)
            ItemStack in = inputs.get(0);
            ItemStack scrap = new ItemStack(ru.iis.gear.GearContent.SCRAP.get());
            if (in.isEmpty() || in.is(scrap.getItem()) || !fits(0, scrap)) return null;
            in.shrink(1);
            pending = sl.getRandom().nextInt(10000) < 1250 ? List.of(scrap) : List.of();
            setChanged();
            Overclock.Result oc = Overclock.basicMachine(1, 45, t.tier, 1);
            return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
        }
        if (t.recipeMap == null) {
            ItemStack in = inputs.get(0);
            if (in.isEmpty()) return null;
            SingleRecipeInput input = new SingleRecipeInput(in);
            Optional<RecipeHolder<SmeltingRecipe>> r = sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, sl);
            if (r.isEmpty()) return null;
            ItemStack result = r.get().value().assemble(input);
            boolean microwave = t.id.equals("microwave");
            if (microwave) {
                // GT_Recipe_Map_Microwave: сперва вход, потом результат
                for (ItemStack s : new ItemStack[]{in, result}) {
                    Microwave.Hazard h = Microwave.hazard(sl, s);
                    if (h == Microwave.Hazard.EXPLODE) {
                        doExplosion(voltage * 4);
                        return null;
                    }
                    if (h == Microwave.Hazard.BURN) {
                        setOnFirePublic(sl);
                        return null;
                    }
                }
            }
            if (!fits(0, result)) return null;
            in.shrink(1);
            pending = List.of(result);
            setChanged();
            // GT_Recipe_Map_Furnace: 4 EU/t, 128 тиков; GT_Recipe_Map_Microwave: 4 EU/t, 32 тика
            Overclock.Result oc = Overclock.basicMachine(4, microwave ? 32 : 128, t.tier, 1);
            return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
        }
        FluidStack fluid = tank.amount() > 0 ? new FluidStack(tank.resource().getFluid(), tank.amount()) : FluidStack.EMPTY;
        var match = GtRecipes.find(sl, t.recipeMap, inputs, fluid, eut -> eut <= voltage);
        if (match.isEmpty()) return null;
        var m = match.get();
        // GT: рецепт чистой комнаты без неё (или при нулевой чистоте) не начинается
        int clean = m.recipe().cleanroom() ? ru.iis.multi.block.CleanroomBlockEntity.cleanlinessAt(sl, worldPosition) : 10000;
        if (clean <= 0) return null;
        var outs = m.recipe().outputs();
        for (int i = 0; i < outs.size() && i < t.outputs; i++) {
            if (!fits(i, outs.get(i).create())) return null;
        }
        for (var fo : m.recipe().fluidOutputs()) {
            if (outTank.amount() > 0 && outTank.resource().getFluid() != fo.fluid()) return null;
            if (outTank.amount() + fo.amount() > outTank.capacity()) return null;
        }
        for (int s = 0; s < m.take().length; s++) {
            if (m.take()[s] > 0) items.get(s).shrink(m.take()[s]);
        }
        for (var f : m.recipe().fluidInputs()) tank.drainInternal(f.amount());
        pending = m.recipe().rollOutputs(sl.getRandom());
        if (m.recipe().cleanroom()) pending = ru.iis.multi.block.CleanroomBlockEntity.spoil(pending, clean, sl.getRandom());
        pendingFluid = m.recipe().fluidOutputs().isEmpty() ? FluidStack.EMPTY : m.recipe().fluidOutputs().getFirst().create();
        setChanged();
        Overclock.Result oc = Overclock.basicMachine(Math.max(1, m.recipe().eut()), m.recipe().duration(), t.tier, 1);
        return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
    }

    /**
     * D139: GT_MetaTileEntity_OrganicReplicator#checkRecipe: мешочек с анализом 4 остаётся на входе, копия выходит с шансом min((уровень + 5) × 10, 100) %;
     * UU-материи round((уровень культуры × 4 + свойства) × 2), V × 15/16 EU/t, (уровень культуры + 1) × 500 / 2^(уровень машины − 1) тиков; выход должен быть пуст.
     */
    private int @Nullable [] replicateSeeds(ServerLevel sl, LvMachineType t) {
        ItemStack in = items.get(0);
        ru.iis.crop.CropSeedItem.Seed s = ru.iis.crop.CropSeedItem.seed(in);
        if (s == null || s.scan() < 4 || s.card() == null || !items.get(t.inputs).isEmpty()) return null;
        int uum = Math.round((s.card().tier() * 4 + s.gain() + s.growth() + s.resistance()) * 2f);
        if (uum > tank.amount()) return null;
        tank.drainInternal(uum);
        int efficiency = Math.min((t.tier.tier() + 5) * 10, 100);
        pending = sl.getRandom().nextInt(100) > efficiency ? List.of() : List.of(in.copyWithCount(1));
        setChanged();
        return new int[]{(int) (voltage * 15 / 16), Math.max(1, (s.card().tier() + 1) * 500 >> (t.tier.tier() - 1))};
    }

    /** D122: GT_MetaTileEntity_Replicator#checkRecipe: сфера с элементом не тратится, UU-материи уходит по массе, пыль на выходе. */
    private int @Nullable [] replicate(ServerLevel sl, LvMachineType t) {
        ItemStack orb = items.get(t.inputs + t.outputs);
        ru.iis.lv.item.DataOrbItem.ElementScan e = orb.get(LvContent.ELEMENT_SCAN.get());
        if (e == null || tank.amount() < e.mass()) return null;
        ItemStack dust = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(e.dust()));
        if (!fits(0, dust)) return null;
        tank.drainInternal(e.mass());
        pending = List.of(dust);
        setChanged();
        return new int[]{(int) voltage, Math.max(1, e.mass() * 512 >> (t.tier.tier() - 1))};
    }

    /** D122: GT_MetaTileEntity_SeismicProspector и _AdvSeismicProspector#checkRecipe: ТНТ на входе и флешка в особом слоте; разведка сразу, флешка выходит по окончании. */
    private int @Nullable [] prospect(ServerLevel sl, LvMachineType t) {
        ItemStack in = items.get(0), stick = items.get(specialSlot());
        boolean adv = t.tier != ru.iis.energy.VoltageTier.LV;
        int tnt = ru.iis.lv.item.Prospection.explosives(in, adv);
        if (tnt == 0 || in.getCount() < tnt || !(stick.getItem() instanceof ru.iis.lv.item.DataStickItem) || stick.getCount() != 1 || !items.get(t.inputs).isEmpty()) return null;
        in.shrink(tnt);
        ItemStack written = new ItemStack(stick.getItem());
        written.set(LvContent.PROSPECTION.get(), adv ? ru.iis.lv.item.Prospection.advanced(sl, worldPosition) : ru.iis.lv.item.Prospection.basic(sl, worldPosition));
        stick.shrink(1);
        pending = List.of(written);
        setChanged();
        return new int[]{0, adv ? ru.iis.lv.item.Prospection.ADVANCED_TICKS : ru.iis.lv.item.Prospection.BASIC_TICKS};
    }

    /** GT_MetaTileEntity_Scanner#checkRecipe, ветка сборочной линии: предмет и пустая флешка → флешка с чертежом, 30 EU/t с разгоном, mResearchTime тиков. */
    private int @Nullable [] scan(ServerLevel sl, LvMachineType t) {
        ItemStack in = items.get(0), stick = items.get(specialSlot());
        // D122: GT_MetaTileEntity_Scanner:173-182: сырые данные разведки: 1000 / 2^(уровень − 1) тиков, 32 × 4^(уровень − 1) EU/t, особый слот пуст
        // D122: :103-122: сфера в особом слоте: копия сферы (512 тиков) или скан элемента (масса × 8192 тиков)
        if (stick.getItem() == LvContent.DATA_ORB.get() && !in.isEmpty() && items.get(t.inputs).isEmpty()) {
            int up = t.tier.tier() - 1;
            ru.iis.lv.item.DataOrbItem.ElementScan e = ru.iis.lv.item.DataOrbItem.scanOf(in);
            if (in.getItem() == LvContent.DATA_ORB.get()) {
                pending = List.of(in.copyWithCount(1));
                in.shrink(1);
                setChanged();
                return new int[]{32 << (2 * up), Math.max(1, 512 >> up)};
            }
            if (e != null) {
                ItemStack done = new ItemStack(LvContent.DATA_ORB.get());
                done.set(LvContent.ELEMENT_SCAN.get(), e);
                in.shrink(1);
                stick.shrink(1);
                pending = List.of(done);
                setChanged();
                return new int[]{32 << (2 * up), Math.max(1, e.mass() * 8192 >> up)};
            }
        }
        ru.iis.lv.item.Prospection raw = in.get(LvContent.PROSPECTION.get());
        if (raw != null && !raw.analyzed() && stick.isEmpty() && items.get(t.inputs).isEmpty()) {
            ItemStack done = in.copyWithCount(1);
            done.set(LvContent.PROSPECTION.get(), raw.withAnalyzed());
            in.shrink(1);
            pending = List.of(done);
            setChanged();
            int up = t.tier.tier() - 1;
            return new int[]{32 << (2 * up), Math.max(1, 1000 >> up)};
        }
        if (in.isEmpty() || !(stick.getItem() instanceof ru.iis.lv.item.DataStickItem) || !items.get(t.inputs).isEmpty()) return null;
        for (var h : GtRecipes.all(sl.recipeAccess().recipeMap(), ru.iis.machine.recipe.GtRecipeMap.ASSEMBLY_LINE)) {
            if (h.value().keep().isEmpty() || !h.value().keep().getFirst().test(in)) continue;
            Overclock.Result oc = Overclock.basicMachine(30, h.value().temperature(), t.tier, 1);
            pending = List.of(ru.iis.lv.item.DataStickItem.written(h));
            in.shrink(1);
            stick.shrink(1);
            setChanged();
            return new int[]{(int) oc.euPerTick(), oc.durationTicks()};
        }
        return null;
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
                case 8 -> itemTransfer ? 1 : 0;
                case 9 -> fluidTransfer ? 1 : 0;
                case 10 -> outTank.amount();
                case 11 -> outTank.amount() > 0 ? BuiltInRegistries.FLUID.getId(outTank.resource().getFluid()) : -1;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 12;
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
        outTank.serialize(out.child("outTank"));
        if (!pendingFluid.isEmpty()) out.store("pendingFluid", FluidOutput.CODEC, new FluidOutput(pendingFluid.getFluid(), pendingFluid.getAmount()));
        out.putInt("progress", logic.progress);
        out.putInt("maxProgress", logic.maxProgress);
        out.putInt("eut", logic.eut);
        out.putBoolean("stuttering", logic.stuttering);
        out.putBoolean("itemTransfer", itemTransfer);
        out.putBoolean("fluidTransfer", fluidTransfer);
        out.putBoolean("allowedToWork", allowedToWork);
        out.putInt("itemOut", itemOut == null ? -1 : itemOut.get3DDataValue());
        out.putInt("fluidOut", fluidOut == null ? -1 : fluidOut.get3DDataValue());
        if (!pending.isEmpty()) out.store("pending", ItemStack.OPTIONAL_CODEC.listOf(), pending);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        items = NonNullList.withSize(slotCount(getBlockState()), ItemStack.EMPTY);
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
        outTank.deserialize(in.childOrEmpty("outTank"));
        pendingFluid = in.read("pendingFluid", FluidOutput.CODEC).map(FluidOutput::create).orElse(FluidStack.EMPTY);
        logic.progress = in.getIntOr("progress", 0);
        logic.maxProgress = in.getIntOr("maxProgress", 0);
        logic.eut = in.getIntOr("eut", 0);
        logic.stuttering = in.getBooleanOr("stuttering", false);
        itemTransfer = in.getBooleanOr("itemTransfer", false);
        fluidTransfer = in.getBooleanOr("fluidTransfer", false);
        allowedToWork = in.getBooleanOr("allowedToWork", true);
        int io = in.getIntOr("itemOut", -1), fo = in.getIntOr("fluidOut", -1);
        itemOut = io < 0 ? null : Direction.from3DDataValue(io);
        fluidOut = fo < 0 ? null : Direction.from3DDataValue(fo);
        pending = in.read("pending", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
    }
}
