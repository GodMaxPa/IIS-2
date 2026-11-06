package ru.iis.rail.cart;

import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.ContainerHelper;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContent;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.MachineBlock;
import ru.iis.rail.RailContent;

import java.util.UUID;

/**
 * Паровоз на твёрдом топливе Railcraft ({@code EntityLocomotiveSteamSolid} + {@code SteamBoiler}, D102).
 * Бак воды 6 000 л, пара 16 000 л. Котёл: цикл 2 тика, нагрев до 500 °C (кипит со 100), на цикл сжигается
 * (8 − 0.1 + 0.8·нагрев + 4·0.5) × 3 единиц горения (efficiencyMulitplier = 3 в сборке ИИС 1), выпаривает
 * воду по уровню нагрева (160 л пара на 1 л воды). Локомотиву нужен пар от половины бака: 160 л пара → 3 единицы хода.
 * Топливо — горючее печи из слота горения; запас пополняется из трёх слотов и из сцепленных сундучных вагонеток.
 */
public class SteamLocomotive extends Locomotive {
    public static final int SLOT_WATER_IN = 0, SLOT_WATER_OUT = 1, SLOT_BURN = 2, SLOT_STOCK = 3, SLOTS = 6;
    public static final int WATER_CAPACITY = 6000, STEAM_CAPACITY = 16000;
    static final double COLD = 20, BOILING = 100, SUPER_HEATED = 300, MAX_HEAT = 500, HEAT_STEP = 0.05;
    static final int TICKS_PER_CYCLE = 2, STEAM_PER_WATER = 160, FUEL_PER_REQUEST = 3;
    /** railcraft.cfg ИИС 1: tweaks.carts.locomotive.steam.efficiencyMulitplier = 3.0. */
    static final double EFFICIENCY = 3.0;

    private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.BOOLEAN);

    public final SimpleContainer inventory = new SimpleContainer(SLOTS);
    int water, steam;
    double heat = COLD, burnTime, partialConversions;
    int burnCycle;
    boolean burning;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_BURNING, false);
    }

    /** Дым из трубы и пар от цилиндров, пока идёт огонь (D128). */
    private void smoke() {
        if (!entityData.get(DATA_BURNING)) return;
        float head = (float) Math.atan2(-syncedHeadingX(), syncedHeadingZ());
        net.minecraft.world.phys.Vec3 stack = position().add(new net.minecraft.world.phys.Vec3(0, 1.65, 0.95).yRot(-head));
        if (tickCount % 2 == 0) level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, stack.x, stack.y, stack.z, 0, 0.08, 0);
        if (isRunning() || getDeltaMovement().horizontalDistanceSqr() > 0.01) {
            if (tickCount % 6 == 0) {
                net.minecraft.world.phys.Vec3 cyl = position().add(new net.minecraft.world.phys.Vec3(random.nextBoolean() ? 0.45 : -0.45, 0.25, 0.95).yRot(-head));
                level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, cyl.x, cyl.y, cyl.z, 0, 0.02, 0);
            }
        }
    }

    public int water() {
        return water;
    }

    public int steam() {
        return steam;
    }

    public double heat() {
        return heat;
    }

    public void addWater(int mb) {
        water = Math.min(WATER_CAPACITY, water + mb);
    }

    public void addSteam(int mb) {
        steam = Math.min(STEAM_CAPACITY, steam + mb);
    }

    public boolean burning() {
        return burning;
    }

    @Override
    protected Item getDropItem() {
        return RailContent.LOCO_STEAM.get();
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return IISMachines.BOILERS.get(BoilerType.COAL_STEEL).get().defaultBlockState().setValue(MachineBlock.ACTIVE, entityData.get(DATA_BURNING));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            smoke();
            return;
        }
        if (water <= 0) setMode(Mode.SHUTDOWN);
        if (STEAM_CAPACITY - steam >= STEAM_PER_WATER || mode() == Mode.SHUTDOWN) {
            boilerTick();
            if (!burning) steam = Math.max(0, steam - 4);   // ventSteam
        }
        if (update % 8 == 0) drainContainers();
        moveOne(SLOT_STOCK, SLOT_STOCK + 3, SLOT_BURN);
        if (update % 20 == 0) pullFuelFromTender(sl);
        entityData.set(DATA_BURNING, burning);
    }

    /** SteamBoiler#getFuelPerCycle при одном баке. */
    double fuelPerCycle() {
        double f = 8 - 8 * 0.0125;
        f += 0.8 * (heat / MAX_HEAT);
        f += 4 * (MAX_HEAT / 1000.0);
        return f * EFFICIENCY;
    }

    void boilerTick() {
        if (++burnCycle >= TICKS_PER_CYCLE) {
            burnCycle = 0;
            double need = fuelPerCycle();
            while (burnTime < need) {
                double more = mode() == Mode.SHUTDOWN ? 0 : moreFuel();
                if (more <= 0) break;
                burnTime += more;
            }
            burning = burnTime >= need;
            if (burning) burnTime -= need;
            convertSteam();
        }
        if (burning) {
            if (heat < MAX_HEAT) heat = Math.min(MAX_HEAT, heat + HEAT_STEP + (MAX_HEAT - heat) / MAX_HEAT * HEAT_STEP * 3);
        } else if (heat > COLD) {
            heat = Math.max(COLD, heat - (HEAT_STEP + heat / MAX_HEAT * HEAT_STEP * 3));
        }
    }

    private void convertSteam() {
        if (heat < BOILING) return;
        partialConversions += heat / MAX_HEAT;
        int cost = (int) partialConversions;
        if (cost <= 0) return;
        partialConversions -= cost;
        cost = Math.min(cost, water);
        if (cost <= 0) return;
        water -= cost;
        steam = Math.min(STEAM_CAPACITY, steam + cost * STEAM_PER_WATER);
    }

    /** SolidFuelProvider: время горения предмета из слота горения. */
    private double moreFuel() {
        ItemStack s = inventory.getItem(SLOT_BURN);
        int burn = s.isEmpty() ? 0 : level().fuelValues().burnDuration(s);
        if (burn <= 0) return 0;
        ItemStack rest = s.getCraftingRemainder() == null ? ItemStack.EMPTY : s.getCraftingRemainder().create();
        s.shrink(1);
        if (s.isEmpty() && !rest.isEmpty()) inventory.setItem(SLOT_BURN, rest);
        return burn;
    }

    @Override
    protected int moreGoJuice() {
        if (steam >= STEAM_CAPACITY / 2) {
            steam -= STEAM_PER_WATER;
            return FUEL_PER_REQUEST;
        }
        return 0;
    }

    /** FluidHelper#drainContainers: ведро или капсула воды из входного слота — в бак, пустая — в выходной. */
    private void drainContainers() {
        ItemStack in = inventory.getItem(SLOT_WATER_IN), out = inventory.getItem(SLOT_WATER_OUT);
        if (in.isEmpty() || WATER_CAPACITY - water < 1000) return;
        // RailcraftLocomotive#isSafeToFill: в перегретый пустой котёл воду не заливают
        if (heat >= SUPER_HEATED && water <= 0) return;
        ItemStack empty;
        if (in.is(Items.WATER_BUCKET)) empty = new ItemStack(Items.BUCKET);
        else if (in.getItem() instanceof CapsuleItem && CapsuleItem.content(in).fluidValue() == Fluids.WATER && CapsuleItem.content(in).amount() >= 1000) {
            empty = CapsuleItem.withContent(in, FluidContent.EMPTY);
        } else return;
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, empty) || out.getCount() >= out.getMaxStackSize())) return;
        water += 1000;
        in.shrink(1);
        if (out.isEmpty()) inventory.setItem(SLOT_WATER_OUT, empty);
        else out.grow(1);
    }

    private void moveOne(int from, int to, int target) {
        ItemStack t = inventory.getItem(target);
        for (int i = from; i < to; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (t.isEmpty()) {
                inventory.setItem(target, s.split(1));
                return;
            }
            if (ItemStack.isSameItemSameComponents(s, t) && t.getCount() < t.getMaxStackSize()) {
                s.shrink(1);
                t.grow(1);
                return;
            }
        }
    }

    /** CartTools.transferHelper.pullStack: топливо из сцепленной сундучной вагонетки (тендера) в пустой слот запаса. */
    private void pullFuelFromTender(ServerLevel level) {
        int free = -1;
        for (int i = SLOT_STOCK; i < SLOT_STOCK + 3; i++) if (inventory.getItem(i).isEmpty()) { free = i; break; }
        if (free < 0) return;
        for (UUID u : TrainLinks.links(this)) {
            if (!(level.getEntity(u) instanceof Container tender)) continue;
            for (int i = 0; i < tender.getContainerSize(); i++) {
                ItemStack s = tender.getItem(i);
                if (!s.isEmpty() && level.fuelValues().burnDuration(s) > 0) {
                    inventory.setItem(free, s.split(Math.min(s.getCount(), 16)));
                    tender.setChanged();
                    return;
                }
            }
        }
    }

    @Override
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> mode().ordinal();
                    case 1 -> speed().ordinal();
                    case 2 -> water;
                    case 3 -> steam;
                    case 4 -> (int) Math.round(heat);
                    case 5 -> burning ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 7;
            }
        };
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        super.destroy(level, source);
        Containers.dropContents(level, this, inventory);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("water", water);
        out.putInt("steam", steam);
        out.putDouble("heat", heat);
        out.putDouble("burnTime", burnTime);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) items.set(i, inventory.getItem(i));
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        water = in.getIntOr("water", 0);
        steam = in.getIntOr("steam", 0);
        heat = in.getDoubleOr("heat", COLD);
        burnTime = in.getDoubleOr("burnTime", 0);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        for (int i = 0; i < SLOTS; i++) inventory.setItem(i, items.get(i));
    }
}
