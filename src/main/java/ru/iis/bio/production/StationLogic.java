package ru.iis.bio.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.IISConfig;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.item.BioItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Работа одного места Био (D70): слоты, баки, одна партия за раз. Входы списываются при запуске партии, выходы выдаются по
 * завершении; если выход некуда положить — партия ждёт (§22.5: «переполнение приостанавливает цикл»). При поломке места
 * незавершённая партия пропадает (не возвращается вместе с продуктом — без удвоения).
 */
public class StationLogic extends SimpleContainer {
    /** Почему место стоит — показывается в интерфейсе. */
    public enum Status { IDLE, RUNNING, WAITING_INPUT, NO_HEAT, NO_COLONY, NO_NUTRIENT, OUTPUT_FULL }

    /** Окружение места: мир, жар, связь с колонией. */
    public interface Host {
        @Nullable ServerLevel serverLevel();

        void changed();

        /** Взять один тик жара у очага снизу (только сосуд). */
        default boolean takeHeat() {
            return false;
        }

        /** Списать поддержание живой части у колонии (мБ N0). false — колонии нет или N0 не хватает. */
        default boolean takeUpkeep(double mb) {
            return true;
        }

        default boolean colonyConnected() {
            return true;
        }

        /** Линия колонии, если входа с линией нет (карман ядра). */
        default String defaultLineage() {
            return "";
        }
    }

    private record PendingFluid(String fluid, int amount) {
        static final Codec<PendingFluid> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("f").forGetter(PendingFluid::fluid), Codec.INT.fieldOf("a").forGetter(PendingFluid::amount)).apply(i, PendingFluid::new));
    }

    public final Station station;
    private final Host host;
    public final List<BioTank> tanks = new ArrayList<>();
    private String running = "";
    private int progress, total, mode, retry;
    private Status status = Status.IDLE;
    private List<ItemStack> pendingItems = new ArrayList<>();
    private List<PendingFluid> pendingFluids = new ArrayList<>();
    private double upkeep;

    public StationLogic(Station station, Host host, @Nullable BioTank extraTank) {
        super(station.slots());
        this.station = station;
        this.host = host;
        for (int c : station.inTanks) tanks.add(tank(c, false));
        for (int c : station.outTanks) tanks.add(tank(c, true));
        if (station.lv()) tanks.add(dedicated(BioFluids.N1));
        if (station.lvE > 0) tanks.add(dedicated(BioFluids.E1));
        if (extraTank != null) tanks.add(extraTank);
    }

    private BioTank dedicated(BioFluids.Def d) {
        return new BioTank.Dedicated(Station.LV_BUFFER, () -> d.source().get()) {
            @Override
            protected void changed() {
                host.changed();
            }
        };
    }

    // --- поддержание LV-органа из своих буферов (§20.5, §21.4): дробные мБ копятся, списываются целыми ------------
    private double owedN, owedE;

    /** true — буферы покрыли расход этого тика. В простое — 25% N1 и ноль E1. */
    private boolean lvUpkeep(boolean working) {
        if (!station.lv()) return true;
        BioTank n = tanks.get(station.n1Buffer());
        BioTank e = station.lvE > 0 ? tanks.get(station.e1Buffer()) : null;
        double dn = working ? station.lvN : station.lvN * 0.25, de = working ? station.lvE : 0;
        if (n.amount() + 1e-9 < owedN + dn || (e != null && e.amount() + 1e-9 < owedE + de)) return false;
        owedN += dn;
        owedE += de;
        if (owedN >= 1) owedN -= n.drain(n.fluid(), (int) owedN, false);
        if (e != null && owedE >= 1) owedE -= e.drain(e.fluid(), (int) owedE, false);
        return true;
    }

    private BioTank tank(int capacity, boolean output) {
        return new BioTank(capacity, output) {
            @Override
            protected void changed() {
                host.changed();
            }
        };
    }

    // --- контейнер ----------------------------------------------------------------------------------------------
    public boolean isInput(int slot) {
        return slot < station.inputs;
    }

    public boolean isOutput(int slot) {
        return slot >= station.inputs && slot < station.inputs + station.outputs;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (isOutput(slot)) return false;
        if (slot == station.toolIndex()) return isTool(stack);
        return true;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        host.changed();
    }

    public static boolean isTool(ItemStack stack) {
        return stack.is(BioItems.KNIFE.get()) || stack.is(BioItems.MANDREL.get()) || stack.is(BioItems.FILTER_PAD.get())
                || stack.is(BioItems.FINE_FILTER_PAD.get());
    }

    public int mode() {
        return mode;
    }

    public void cycleMode() {
        if (station.modes <= 1 || !running.isEmpty()) return;
        mode = (mode + 1) % station.modes;
        host.changed();
    }

    public Status status() {
        return status;
    }

    public boolean busy() {
        return !running.isEmpty();
    }

    // --- такт ---------------------------------------------------------------------------------------------------
    public void tick() {
        if (!running.isEmpty()) {
            if (progress < total) {
                if (station.colony && !host.colonyConnected()) {
                    status = Status.NO_COLONY;
                    return;
                }
                if (needsHeat() && !host.takeHeat()) {
                    status = Status.NO_HEAT;
                    return;
                }
                if (upkeep > 0 && !host.takeUpkeep(upkeep)) {
                    status = Status.NO_NUTRIENT;
                    return;
                }
                if (!lvUpkeep(true)) {
                    status = Status.NO_NUTRIENT;
                    return;
                }
                progress++;
                status = Status.RUNNING;
                if (progress % 20 == 0) host.changed();
                return;
            }
            if (deliver()) {
                running = "";
                progress = total = 0;
                upkeep = 0;
                status = Status.IDLE;
                host.changed();
            } else {
                status = Status.OUTPUT_FULL;
            }
            return;
        }
        if (!lvUpkeep(false)) {
            status = Status.NO_NUTRIENT;
            return;
        }
        if (--retry > 0) return;
        retry = 10;
        if (station.colony && !host.colonyConnected()) {
            status = Status.NO_COLONY;
            return;
        }
        BioRecipe r = find();
        if (r != null) start(r);
        else status = anyInput() ? Status.WAITING_INPUT : Status.IDLE;
    }

    private boolean heat;

    private boolean needsHeat() {
        return heat;
    }

    private boolean anyInput() {
        for (int i = 0; i < station.inputs; i++) if (!getItem(i).isEmpty()) return true;
        return false;
    }

    // --- подбор рецепта ---------------------------------------------------------------------------------------------
    public static boolean matches(ItemStack stack, String key) {
        if (stack.isEmpty()) return false;
        if ("food".equals(key)) {
            FoodProperties f = stack.get(DataComponents.FOOD);
            return f != null && f.nutrition() > 0;
        }
        if (key.startsWith("#")) {
            return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1))));
        }
        Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        return stack.is(it);
    }

    /** Сколько каждого входного слота займёт рецепт; null — не хватает. */
    private int @Nullable [] allocate(BioRecipe r) {
        int[] take = new int[station.inputs];
        for (BioRecipe.In in : r.items()) {
            int need = in.count();
            for (int s = 0; s < station.inputs && need > 0; s++) {
                ItemStack st = getItem(s);
                if (!matches(st, in.key())) continue;
                int free = st.getCount() - take[s];
                int n = Math.min(free, need);
                take[s] += n;
                need -= n;
            }
            if (need > 0) return null;
        }
        return take;
    }

    private @Nullable BioTank tankWith(Fluid f, int amount) {
        for (BioTank t : tanks) if (!t.dedicated() && t.fluid() == f && t.amount() >= amount) return t;
        return null;
    }

    private boolean toolReady(BioRecipe r) {
        if (r.tool().isEmpty()) return true;
        if (!station.toolSlot) return false;
        return matches(getItem(station.toolIndex()), r.tool());
    }

    private @Nullable BioRecipe find() {
        for (BioRecipe r : BioRecipe.forStation(station.id, mode)) {
            if (!toolReady(r) || allocate(r) == null) continue;
            boolean fluidsOk = true;
            for (BioRecipe.FluidIn f : r.fluids()) {
                Fluid fl = BioFluids.byKey(f.fluid());
                if (fl == null || tankWith(fl, f.amount()) == null) fluidsOk = false;
            }
            if (fluidsOk && outputsFit(r)) return r;
        }
        return null;
    }

    /** Есть ли место под выход (проверка до запуска, чтобы не списать входы впустую). */
    private boolean outputsFit(BioRecipe r) {
        for (BioRecipe.FluidOut f : r.outFluids()) {
            Fluid fl = BioFluids.byKey(f.fluid());
            int amount = f.perNutrition() > 0 ? f.perNutrition() * 20 : f.amount();
            if (fl == null || freeOutput(fl) < Math.min(amount, 1)) return false;
        }
        List<ItemStack> copy = new ArrayList<>();
        for (int s = station.inputs; s < station.inputs + station.outputs; s++) copy.add(getItem(s).copy());
        for (BioRecipe.Out o : r.outItems()) {
            ItemStack add = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(o.item())), o.count());
            if (!merge(copy, add)) return false;
        }
        return true;
    }

    private int freeOutput(Fluid f) {
        int free = 0;
        for (BioTank t : tanks) if (t.isOutput()) free = Math.max(free, t.space(f));
        return free;
    }

    private static boolean merge(List<ItemStack> slots, ItemStack add) {
        for (ItemStack s : slots) {
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize()) {
                s.grow(add.getCount());
                return true;
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, add.copy());
                return true;
            }
        }
        return false;
    }

    private void start(BioRecipe r) {
        int[] take = allocate(r);
        if (take == null) return;
        // посев/туша несут линию и добавки — их берём до списания
        String line = "";
        int nutrition = 0;
        List<ItemStack> extras = new ArrayList<>();
        for (int s = 0; s < station.inputs; s++) {
            if (take[s] == 0) continue;
            ItemStack st = getItem(s);
            String l = st.get(BioItems.LINEAGE.get());
            if (l != null && line.isEmpty()) line = l;
            FoodProperties f = st.get(DataComponents.FOOD);
            if (f != null) nutrition += f.nutrition() * take[s];
            List<ItemStack> ex = st.get(BioItems.CARCASS_EXTRAS.get());
            if (ex != null) for (int k = 0; k < take[s]; k++) for (ItemStack e : ex) extras.add(e.copy());
        }
        for (int s = 0; s < station.inputs; s++) if (take[s] > 0) getItem(s).shrink(take[s]);
        for (BioRecipe.FluidIn f : r.fluids()) {
            Fluid fl = BioFluids.byKey(f.fluid());
            BioTank t = tankWith(fl, f.amount());
            if (t != null) t.drain(fl, f.amount(), false);
        }
        if (!r.tool().isEmpty()) {
            ItemStack tool = getItem(station.toolIndex());
            if (host.serverLevel() != null) tool.hurtAndBreak(1, host.serverLevel(), null, it -> {});
        }
        if (r.lineage() && line.isEmpty()) line = host.defaultLineage();
        pendingItems = new ArrayList<>();
        for (BioRecipe.Out o : r.outItems()) {
            ItemStack out = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(o.item())), o.count());
            if (r.lineage() && !line.isEmpty()) out.set(BioItems.LINEAGE.get(), line);
            pendingItems.add(out);
        }
        pendingItems.addAll(extras);
        pendingFluids = new ArrayList<>();
        for (BioRecipe.FluidOut f : r.outFluids()) {
            int amount = f.perNutrition() > 0 ? f.perNutrition() * nutrition : f.amount();
            if (amount > 0) pendingFluids.add(new PendingFluid(f.fluid(), amount));
        }
        running = r.id();
        heat = r.heat();
        upkeep = r.upkeep();
        total = Math.max(1, scaled(r.ticks()));
        progress = 0;
        status = Status.RUNNING;
        setChanged();
    }

    /** Профиль TEST-RECIPES (§22.10): время изготовления ÷10, не меньше секунды; количества те же. */
    private static int scaled(int ticks) {
        return IISConfig.bioTestRecipes() ? Math.max(20, ticks / 10) : ticks;
    }

    /** Выдать продукт; false — некуда (ждём). Частично выданное не повторяется. */
    private boolean deliver() {
        List<PendingFluid> leftFluids = new ArrayList<>();
        for (PendingFluid p : pendingFluids) {
            Fluid fl = BioFluids.byKey(p.fluid());
            int left = p.amount();
            if (fl != null) {
                // сначала бак с той же жидкостью, затем выделенный под неё (N0 колонии), затем любой пустой
                for (int pass = 0; pass < 3 && left > 0; pass++) {
                    for (BioTank t : tanks) {
                        if (!t.isOutput() || left <= 0) continue;
                        boolean ok = switch (pass) {
                            case 0 -> t.amount() > 0 && t.fluid() == fl;
                            case 1 -> t.dedicatedTo(fl);
                            default -> t.amount() == 0 && !t.dedicated();
                        };
                        if (ok) left -= t.fill(fl, left, false);
                    }
                }
            }
            if (left > 0) leftFluids.add(new PendingFluid(p.fluid(), left));
        }
        pendingFluids = leftFluids;
        List<ItemStack> leftItems = new ArrayList<>();
        for (ItemStack p : pendingItems) {
            ItemStack rest = p.copy();
            for (int s = station.inputs; s < station.inputs + station.outputs && !rest.isEmpty(); s++) {
                ItemStack cur = getItem(s);
                if (cur.isEmpty()) {
                    setItem(s, rest.copy());
                    rest = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(cur, rest)) {
                    int n = Math.min(rest.getCount(), cur.getMaxStackSize() - cur.getCount());
                    cur.grow(n);
                    rest.shrink(n);
                }
            }
            if (!rest.isEmpty()) leftItems.add(rest);
        }
        pendingItems = leftItems;
        host.changed();
        return pendingFluids.isEmpty() && pendingItems.isEmpty();
    }

    // --- данные для интерфейса ------------------------------------------------------------------------------------
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i == 0) return total > 0 ? progress * 1000 / total : 0;
            if (i == 1) return status.ordinal();
            if (i == 2) return mode;
            int t = (i - 3) / 2;
            if (t >= tanks.size()) return 0;
            BioTank tank = tanks.get(t);
            if ((i - 3) % 2 == 0) return tank.fluid() == Fluids.EMPTY ? -1 : BuiltInRegistries.FLUID.getId(tank.fluid());
            return tank.amount();
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 3 + 2 * tanks.size();
        }
    };

    public static int dataCount(Station s) {
        return 3 + 2 * s.tanks();
    }

    // --- сохранение ---------------------------------------------------------------------------------------------
    public void save(ValueOutput out) {
        ContainerHelper.saveAllItems(out.child("slots"), getItems());
        for (int i = 0; i < tanks.size(); i++) {
            if (station == Station.POCKET && i == station.inTanks.length + station.outTanks.length) break; // бак колонии сохраняет ядро
            tanks.get(i).save(out.child("tank" + i));
        }
        out.putDouble("owedN", owedN);
        out.putDouble("owedE", owedE);
        out.putString("running", running);
        out.putInt("progress", progress);
        out.putInt("total", total);
        out.putInt("mode", mode);
        out.putBoolean("heat", heat);
        out.putDouble("upkeep", upkeep);
        out.store("pendingItems", ItemStack.CODEC.listOf(), pendingItems);
        out.store("pendingFluids", PendingFluid.CODEC.listOf(), pendingFluids);
    }

    public void load(ValueInput in) {
        getItems().clear();
        ContainerHelper.loadAllItems(in.childOrEmpty("slots"), getItems());
        for (int i = 0; i < tanks.size(); i++) {
            if (station == Station.POCKET && i == station.inTanks.length + station.outTanks.length) break;
            tanks.get(i).load(in.childOrEmpty("tank" + i));
        }
        owedN = in.getDoubleOr("owedN", 0);
        owedE = in.getDoubleOr("owedE", 0);
        running = in.getStringOr("running", "");
        progress = in.getIntOr("progress", 0);
        total = in.getIntOr("total", 0);
        mode = Math.max(0, Math.min(station.modes - 1, in.getIntOr("mode", 0)));
        heat = in.getBooleanOr("heat", false);
        upkeep = in.getDoubleOr("upkeep", 0);
        pendingItems = new ArrayList<>(in.read("pendingItems", ItemStack.CODEC.listOf()).orElse(List.of()));
        pendingFluids = new ArrayList<>(in.read("pendingFluids", PendingFluid.CODEC.listOf()).orElse(List.of()));
    }

    /** Что выпадает при поломке: содержимое слотов (баки и незавершённая партия пропадают). */
    public List<ItemStack> drops() {
        List<ItemStack> r = new ArrayList<>();
        for (ItemStack s : getItems()) if (!s.isEmpty()) r.add(s.copy());
        return r;
    }

    public static Fluid fluidBySync(int idx) {
        if (idx < 0) return Fluids.EMPTY;
        Fluid f = BuiltInRegistries.FLUID.byId(idx);
        return f == null ? Fluids.EMPTY : f;
    }
}
