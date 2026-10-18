package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.core.item.MaterialItems;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.logic.BoilerLogic;
import ru.iis.machine.menu.BoilerMenu;

import java.util.Optional;

/**
 * Малый паровой котёл GT5-II. Слоты как в GT: 0 — ёмкость с водой, 1 — пустая ёмкость, 2 — топливо, 3 — зола/побочный продукт.
 * Вода — только жидкость «вода» (трубой или ведром); пар отдаётся соседям (кроме лицевой стороны и низа; у солнечного — только спереди).
 */
public class BoilerBlockEntity extends BaseContainerBlockEntity implements MachineBlock.TickingMachine {
    public static final int SLOT_WATER_IN = 0, SLOT_WATER_OUT = 1, SLOT_FUEL = 2, SLOT_ASH = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private final BoilerLogic logic;
    private final FluidTank water;
    private final FluidTank steam;
    private final @Nullable FluidTank lava;
    private final ResourceHandler<FluidResource> handler;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(IISMachines.BOILER_BE.get(), pos, state);
        BoilerType type = state.getBlock() instanceof BoilerBlock b ? b.type : BoilerType.COAL_BRONZE;
        this.logic = new BoilerLogic(type.kind);
        int cap = type.kind.capacity;
        this.water = new FluidTank(cap, f -> f.isSame(Fluids.WATER), false, this::setChanged);
        this.steam = new FluidTank(cap, f -> false, true, this::setChanged);
        // GT5 лавовый котёл: принимает лаву из труб порциями до 50, пока запас энергии меньше 50
        this.lava = type == BoilerType.LAVA_STEEL ? new FluidTank(50, f -> f.isSame(Fluids.LAVA), false, this::setChanged) : null;
        this.handler = lava != null ? new CombinedResourceHandler<>(water, steam, lava) : new CombinedResourceHandler<>(water, steam);
    }

    public BoilerType type() {
        return getBlockState().getBlock() instanceof BoilerBlock b ? b.type : BoilerType.COAL_BRONZE;
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return handler;
    }

    /** Ведро воды правым кликом (GT: в бак сколько влезет, ведро опустошается в любом случае). */
    public boolean fillWaterFromBucket() {
        water.setAmount(Fluids.WATER, Math.min(water.capacity(), water.amount() + 1000));
        return true;
    }

    @Override
    public void serverTick() {
        Level level = this.level;
        if (level == null) return;
        long time = level.getGameTime();
        handleWaterContainers();
        if (lava != null && logic.energy < 50 && lava.amount() > 0) {
            logic.energy += lava.drainInternal(50);
        }
        logic.water = water.amount();
        logic.steam = steam.amount();
        BoilerLogic.Result r = logic.tick(time, env(level));
        if (r == BoilerLogic.Result.EXPLODE) {
            explode(level);
            return;
        }
        if (r == BoilerLogic.Result.OVERFLOW) {
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        water.setAmount(Fluids.WATER, logic.water);
        steam.setAmount(IISFluids.STEAM.get(), logic.steam);
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != logic.active) {
            level.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, logic.active), 3);
        }
        setChanged();
    }

    private void explode(Level level) {
        // GT5 doExplosion(2048): сила 6, блок котла пропадает; IIS ExplosionItemDrops=false — содержимое не выпадает
        items.clear();
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 6.0f, Level.ExplosionInteraction.BLOCK);
    }

    private void handleWaterContainers() {
        ItemStack in = items.get(SLOT_WATER_IN);
        if (in.is(Items.WATER_BUCKET) && water.capacity() - water.amount() >= 1000) {
            ItemStack out = items.get(SLOT_WATER_OUT);
            if (out.isEmpty() || (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize())) {
                water.setAmount(Fluids.WATER, water.amount() + 1000);
                in.shrink(1);
                if (out.isEmpty()) items.set(SLOT_WATER_OUT, new ItemStack(Items.BUCKET));
                else out.grow(1);
            }
        }
    }

    private BoilerLogic.Env env(Level level) {
        BoilerType type = type();
        return new BoilerLogic.Env() {
            @Override
            public void pushSteam() {
                Direction front = getBlockState().getValue(MachineBlock.FACING);
                if (type == BoilerType.SOLAR_BRONZE) {
                    push(level, front);
                } else {
                    for (Direction d : Direction.values()) {
                        if (d != front && d != Direction.DOWN) push(level, d);
                    }
                }
            }

            @Override
            public int refuel() {
                return switch (type) {
                    case SOLAR_BRONZE -> BoilerLogic.solarEnergy(level.canSeeSky(worldPosition.above()), level.isRaining(),
                            level.isThundering(), level.getSkyDarken() >= 4, level.isBrightOutside());
                    case LAVA_STEEL -> refuelLava();
                    default -> refuelSolid(level);
                };
            }

            @Override
            public int steamPerWater() {
                return type == BoilerType.LAVA_STEEL ? BoilerLogic.lavaSteam(lavaEfficiency()) : 150;
            }

            @Override
            public boolean consumesWater() {
                return type != BoilerType.LAVA_STEEL || level.getRandom().nextInt(100) < Math.round(lavaEfficiency() * 100.0);
            }

            @Override
            public void onHeatStep() {
                // GT5: в среднем одна грязная каменная пыль на ведро лавы
                if (type == BoilerType.LAVA_STEEL && level.getRandom().nextInt(333) == 0) {
                    MaterialItems.find("dust/stone").ifPresent(it -> {
                        ItemStack ash = items.get(SLOT_ASH);
                        if (ash.is(it) && ash.getCount() < ash.getMaxStackSize()) ash.grow(1);
                        else items.set(SLOT_ASH, new ItemStack(it));
                    });
                }
            }
        };
    }

    private double lavaEfficiency() {
        ItemStack s = items.get(SLOT_ASH);
        return BoilerLogic.lavaEfficiency(s.getCount(), s.isEmpty() ? 64 : s.getMaxStackSize());
    }

    private void push(Level level, Direction d) {
        if (logic.steam <= 0) return;
        ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(d), d.getOpposite());
        if (target == null) return;
        int amount = Math.max(1, logic.steam / 2);
        try (Transaction tx = Transaction.openRoot()) {
            int moved = target.insert(FluidResource.of(IISFluids.STEAM.get()), amount, tx);
            if (moved > 0) {
                logic.drainSteam(moved);
                tx.commit();
            }
        }
    }

    private int refuelLava() {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.is(Items.LAVA_BUCKET)) {
            items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
            return 1000;
        }
        return 0;
    }

    /** GT_MetaTileEntity_Boiler_Bronze/Steel: уголь и древесный уголь 160, кокс 640, бурый уголь 120; зола с шансом. */
    private int refuelSolid(Level level) {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) return 0;
        ItemStack ash = items.get(SLOT_ASH);
        if (!ash.isEmpty() && ash.getCount() >= ash.getMaxStackSize()) return 0; // слот золы забит — топливо не берётся
        Fuel f = fuelOf(fuel);
        if (f == null) return 0;
        fuel.shrink(1);
        if (level.getRandom().nextInt(f.ashChance) == 0) {
            Optional<Item> byproduct = MaterialItems.find(f.darkAsh ? "tiny_dust/dark_ash" : "tiny_dust/ash");
            byproduct.ifPresent(b -> {
                if (ash.isEmpty()) items.set(SLOT_ASH, new ItemStack(b));
                else if (ash.is(b)) ash.grow(1);
                else MaterialItems.find("tiny_dust/ash").ifPresent(light -> items.set(SLOT_ASH, new ItemStack(light, ash.getCount() + 1)));
            });
        }
        setChanged();
        return f.energy;
    }

    private record Fuel(int energy, int ashChance, boolean darkAsh) {}

    private static @Nullable Fuel fuelOf(ItemStack s) {
        for (String form : new String[]{"gem/coal", "dust/coal", "impure_dust/coal", "crushed/coal"}) {
            if (is(s, form)) return new Fuel(160, 3, true);
        }
        for (String form : new String[]{"gem/charcoal", "dust/charcoal", "impure_dust/charcoal", "crushed/charcoal"}) {
            if (is(s, form)) return new Fuel(160, 3, false);
        }
        for (String form : new String[]{"gem/lignite", "dust/lignite", "impure_dust/lignite", "crushed/lignite"}) {
            if (is(s, form)) return new Fuel(120, 8, true);
        }
        return null;
    }

    private static boolean is(ItemStack s, String form) {
        return MaterialItems.find(form).map(s::is).orElse(false);
    }

    public static boolean isFuel(BoilerType type, ItemStack s) {
        return type == BoilerType.LAVA_STEEL ? s.is(Items.LAVA_BUCKET) : type != BoilerType.SOLAR_BRONZE && fuelOf(s) != null;
    }

    // --- GUI ------------------------------------------------------------------------------------------------------
    /** Данные для GUI: температура, вода, пар, энергия топлива. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> logic.temperature;
                case 1 -> water.amount();
                case 2 -> steam.amount();
                case 3 -> Math.min(logic.energy, Short.MAX_VALUE);
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
        return 4;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BoilerMenu(id, inventory, this, data, type());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        water.serialize(out.child("water"));
        steam.serialize(out.child("steam"));
        if (lava != null) lava.serialize(out.child("lava"));
        out.putInt("temperature", logic.temperature);
        out.putInt("lossTimer", logic.lossTimer);
        out.putInt("energy", logic.energy);
        out.putBoolean("hadNoWater", logic.hadNoWater);
        out.putInt("runTime", logic.runTime);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        water.deserialize(in.childOrEmpty("water"));
        steam.deserialize(in.childOrEmpty("steam"));
        if (lava != null) lava.deserialize(in.childOrEmpty("lava"));
        logic.temperature = in.getIntOr("temperature", 20);
        logic.lossTimer = in.getIntOr("lossTimer", 0);
        logic.energy = in.getIntOr("energy", 0);
        logic.hadNoWater = in.getBooleanOr("hadNoWater", false);
        logic.runTime = in.getIntOr("runTime", 0);
    }

    /** Для проверок (/iis selftest, GameTest). */
    public BoilerLogic logic() {
        return logic;
    }
}
