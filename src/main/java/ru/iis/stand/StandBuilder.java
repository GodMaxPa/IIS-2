package ru.iis.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.iis.IIS;
import ru.iis.bio.BioContent;
import ru.iis.bio.block.*;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.item.BioItems;
import ru.iis.bio.production.Station;
import ru.iis.bio.world.ShuttleSpawner;
import ru.iis.command.SelfTest;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.UniversalTankBlockEntity;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.machine.block.BoilerBlockEntity;
import ru.iis.machine.BoilerType;
import ru.iis.machine.block.SteamMachineBlockEntity;
import ru.iis.machine.SteamMachineType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Стендовый мир-эталон (D74): на ровной площадке у точки появления строится всё, что сейчас есть в ИИС 2, — в работе и с
 * подписями на русском. Участки (игрок появляется у входа и смотрит на юг, +Z):
 * <ul>
 *   <li>Био (x 0…30): колония с подложкой и ложем, ручные места очага с загруженными партиями, LV-органы с полными буферами,
 *       сеть сосудов с насосом ядра (зажим, обратный клапан, живой бак) и LV-сеть с мышечным насосом, сундуки со всем Био.</li>
 *   <li>Жидкости (x 34…62): все трубы Кибера по материалам и размерам, работающая медная линия «резервуар → труба → резервуар»,
 *       универсальные резервуары, стена рамок с капсулами жидкостей GT.</li>
 *   <li>Кибер (x 66…): котлы и паровые машины в работе, галерея всех остальных блоков, стена руд с табличками; дальше — челнок.</li>
 * </ul>
 * Строится командой {@code /iis stand} (оператор) или при запуске сервера разработки с {@code -Diis.stand=build} (затем сервер
 * сохраняет мир и останавливается: так готовится сохранение для клиента).
 */
public final class StandBuilder {
    private final ServerLevel level;
    private final int y0;
    private final Map<String, String> ru;

    private StandBuilder(ServerLevel level, int y0) {
        this.level = level;
        this.y0 = y0;
        this.ru = loadRu();
    }

    /** Построить у (0, y0, 0); y0 — уровень, на котором стоят блоки (над полом). */
    public static void build(ServerLevel level, int y0) {
        StandBuilder b = new StandBuilder(level, y0);
        // площадка всегда загружена: машины и сосуды работают, даже когда игрок в другом конце стенда
        for (int cx = -1; cx <= 10; cx++) for (int cz = -1; cz <= 4; cz++) level.setChunkForced(cx, cz, true);
        b.ground();
        b.bio();
        b.fluids();
        b.cyber();
        b.shuttle();
        b.titles();
        StandData.mark(level);
        MinecraftServer server = level.getServer();
        for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule respawn_radius 0",
                "time set noon", "weather clear", "setworldspawn 30 " + y0 + " -8 0")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
        }
        IIS.LOGGER.info("[IIS] stand built at y={}", y0);
    }

    // --- общее -------------------------------------------------------------------------------------------------------
    private BlockPos at(int x, int dy, int z) {
        return new BlockPos(x, y0 + dy, z);
    }

    private void set(BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }

    private <T extends BlockEntity> T be(BlockPos p, Class<T> c) {
        BlockEntity b = level.getBlockEntity(p);
        return c.isInstance(b) ? c.cast(b) : null;
    }

    private void ground() {
        for (int x = -4; x <= 160; x++)
            for (int z = -14; z <= 72; z++) {
                boolean lamp = Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4;
                set(new BlockPos(x, y0 - 1, z), (lamp ? Blocks.SEA_LANTERN : (x < 32 ? Blocks.POLISHED_DEEPSLATE : x < 64 ? Blocks.SMOOTH_STONE
                        : Blocks.POLISHED_ANDESITE)).defaultBlockState());
                for (int dy = 0; dy < 14; dy++) set(new BlockPos(x, y0 + dy, z), Blocks.AIR.defaultBlockState());
            }
    }

    private static Map<String, String> loadRu() {
        Map<String, String> m = new HashMap<>();
        try (InputStream in = StandBuilder.class.getResourceAsStream("/assets/iis/lang/ru_ru.json")) {
            if (in == null) return m;
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            IIS.LOGGER.warn("[IIS] stand: no ru_ru.json", e);
        }
        return m;
    }

    private String nameOf(Identifier id) {
        String n = ru.get("block." + id.getNamespace() + "." + id.getPath());
        if (n == null) n = ru.get("item." + id.getNamespace() + "." + id.getPath());
        return n == null ? id.getPath() : n;
    }

    private String nameOf(Block b) {
        return nameOf(BuiltInRegistries.BLOCK.getKey(b));
    }

    /** Разбить подпись на строки таблички (до 4 по ~15 знаков). */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + w.length() > 15) {
                lines.add(cur.toString());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines.size() > 4 ? lines.subList(0, 4) : lines;
    }

    private void writeSign(BlockPos p, String text) {
        if (!(level.getBlockEntity(p) instanceof SignBlockEntity s)) return;
        SignText t = new SignText();
        List<String> lines = wrap(text);
        for (int i = 0; i < lines.size(); i++) t.setMessage(i, Component.literal(lines.get(i)));
        s.setText(t, true);
        s.setWaxed(true);
        s.setChanged();
    }

    /** Табличка на полу перед блоком (читается с севера). */
    private void sign(BlockPos p, String text) {
        set(p, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
        writeSign(p, text);
    }

    /** Табличка на северной грани блока {@code on}. */
    private void wallSign(BlockPos on, String text) {
        BlockPos p = on.north();
        set(p, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        writeSign(p, text);
    }

    private void frame(BlockPos wall, ItemStack stack) {
        ItemFrame f = new ItemFrame(level, wall.north(), Direction.NORTH);
        f.setItem(stack, false);
        f.setInvulnerable(true);
        level.addFreshEntity(f);
    }

    private void chest(BlockPos p, List<ItemStack> items, String label) {
        set(p, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity c = be(p, ChestBlockEntity.class);
        if (c != null) for (int i = 0; i < Math.min(27, items.size()); i++) c.setItem(i, items.get(i));
        sign(p.north(), label);
    }

    private static int insert(net.neoforged.neoforge.transfer.ResourceHandler<FluidResource> h, Fluid f, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.insert(FluidResource.of(f), amount, tx);
            tx.commit();
            return n;
        }
    }

    private BlockState withProps(BlockState s, Map<Direction, BooleanProperty> props, Direction... dirs) {
        for (Direction d : dirs) s = s.setValue(props.get(d), true);
        return s;
    }

    // --- Био ---------------------------------------------------------------------------------------------------------
    private ColonyCoreBlockEntity core(BlockPos p) {
        set(p, BioContent.CORE.get().defaultBlockState());
        ColonyCoreBlockEntity c = be(p, ColonyCoreBlockEntity.class);
        if (c != null) c.colony().setNutrition(1000);
        return c;
    }

    private StationBlockEntity station(BlockPos p, Block block) {
        set(p, block.defaultBlockState());
        sign(p.north(), nameOf(block));
        return be(p, StationBlockEntity.class);
    }

    private static ItemStack s(net.neoforged.neoforge.registries.DeferredItem<? extends Item> i, int n) {
        return new ItemStack(i.get(), n);
    }

    private void fillLvBuffers(StationBlockEntity st) {
        Station s = st.logic.station;
        st.logic.tanks.get(s.n1Buffer()).fill(BioFluids.N1.source().get(), Station.LV_BUFFER, false);
        if (s.lvE > 0) st.logic.tanks.get(s.e1Buffer()).fill(BioFluids.E1.source().get(), Station.LV_BUFFER, false);
    }

    private VesselBlockEntity vessel(BlockPos p, Block block, Direction... connect) {
        set(p, withProps(block.defaultBlockState(), VesselBlock.PROPS, connect));
        return be(p, VesselBlockEntity.class);
    }

    private void bio() {
        // колония: ядро, подложка и ложе вплотную, пасть
        BlockPos c = at(8, 0, 6);
        ColonyCoreBlockEntity core = core(c);
        if (core != null) core.seedTissue(6);
        sign(c.north().north(), "Ядро колонии (ПКМ — карман)");
        StationBlockEntity sub = station(c.west(), BioContent.SUBSTRATE.get());
        if (sub != null) {
            sub.logic.setItem(0, s(BioItems.GROWTH_SUBSTRATE, 4));
            sub.logic.setItem(1, s(BioItems.PRIMITIVE_CULTURE, 4));
            sub.logic.tanks.get(0).fill(BioFluids.PRIMARY.source().get(), 1000, false);
        }
        StationBlockEntity bed = station(c.east(), BioContent.BED.get());
        if (bed != null) {
            bed.logic.setItem(0, s(BioItems.GROWTH_SUBSTRATE, 8));
            bed.logic.setItem(1, s(BioItems.BASE_SEED, 8));
            bed.logic.tanks.get(0).fill(BioFluids.G0.source().get(), 1000, false);
        }
        set(c.east().east(), BioContent.MAW.get().defaultBlockState());
        sign(c.east().east().north(), nameOf(BioContent.MAW.get()));

        // ручные места очага: каждое с загруженной партией
        int z = 14;
        StationBlockEntity b = station(at(2, 0, z), BioContent.BUTCHER.get());
        if (b != null) {
            b.logic.setItem(0, s(BioItems.CARCASS_MEDIUM, 4));
            b.logic.setItem(Station.BUTCHER.toolIndex(), s(BioItems.KNIFE, 1));
        }
        StationBlockEntity m = station(at(5, 0, z), BioContent.MORTAR.get());
        if (m != null) m.logic.setItem(0, new ItemStack(Items.BEEF, 16));
        BlockPos hearth = at(8, 0, z);
        set(hearth, BioContent.HEARTH.get().defaultBlockState());
        HearthBlockEntity hb = be(hearth, HearthBlockEntity.class);
        if (hb != null) hb.setItem(0, new ItemStack(Items.COAL, 32));
        sign(hearth.north(), "Очаг и варочный сосуд");
        set(hearth.above(), BioContent.POT.get().defaultBlockState());
        StationBlockEntity pot = be(hearth.above(), StationBlockEntity.class);
        if (pot != null) {
            pot.logic.setItem(0, s(BioItems.CONNECTIVE_FIBER, 16));
            pot.logic.tanks.get(0).fill(Fluids.WATER, 2000, false);
        }
        StationBlockEntity mix = station(at(11, 0, z), BioContent.MIXER.get());
        if (mix != null) {
            mix.logic.tanks.get(0).fill(BioFluids.N0.source().get(), 1000, false);
            mix.logic.tanks.get(1).fill(BioFluids.HYDROLYSATE.source().get(), 1000, false);
            mix.logic.tanks.get(2).fill(BioFluids.BINDER.source().get(), 1000, false);
            mix.logic.tanks.get(3).fill(BioFluids.SUSPENSION.source().get(), 1000, false);
        }
        StationBlockEntity press = station(at(14, 0, z), BioContent.PRESS.get());
        if (press != null) {
            press.logic.setItem(0, s(BioItems.PLANT_MASS, 16));
            press.logic.tanks.get(0).fill(Fluids.WATER, 1000, false);
        }
        StationBlockEntity filter = station(at(17, 0, z), BioContent.FILTER.get());
        if (filter != null) {
            filter.logic.setItem(Station.FILTER.toolIndex(), s(BioItems.FILTER_PAD, 1));
            filter.logic.tanks.get(0).fill(Fluids.WATER, 1000, false);
        }

        // LV-органы с полными буферами N1/E1 и загруженной партией
        z = 20;
        StationBlockEntity nest = station(at(2, 0, z), BioContent.NEST.get());
        if (nest != null) {
            fillLvBuffers(nest);
            nest.logic.setItem(0, s(BioItems.MUSCLE_BUNDLE, 4));
            nest.logic.setItem(1, s(BioItems.SINEW_CORD, 4));
            nest.logic.setItem(2, s(BioItems.BONE_RIB, 4));
            nest.logic.tanks.get(0).fill(BioFluids.G1.source().get(), 1000, false);
        }
        StationBlockEntity gland = station(at(6, 0, z), BioContent.GLAND.get());
        if (gland != null) {
            fillLvBuffers(gland);
            gland.logic.tanks.get(0).fill(BioFluids.N1.source().get(), 1000, false);
            gland.logic.tanks.get(1).fill(BioFluids.CONCENTRATE.source().get(), 1000, false);
            gland.logic.tanks.get(2).fill(BioFluids.EMULSION.source().get(), 1000, false);
        }
        StationBlockEntity mf = station(at(10, 0, z), BioContent.MEMBRANE_FILTER.get());
        if (mf != null) {
            fillLvBuffers(mf);
            mf.logic.setItem(Station.MEMBRANE_FILTER.toolIndex(), s(BioItems.FINE_FILTER_PAD, 1));
            mf.logic.tanks.get(0).fill(Fluids.WATER, 1000, false);
        }

        // ULV-сеть: второе ядро качает воду из резервуара по капиллярам через зажим и обратный клапан в живой бак
        z = 28;
        BlockPos c2 = at(2, 0, z);
        core(c2);
        sign(c2.north(), "Ядро — насос ULV");
        Block cap = BioContent.VESSELS.get("capillary_bundle").get();
        for (int x = 3; x <= 6; x++) vessel(at(x, 0, z), cap, Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity src = tank(at(4, 1, z), "universal_tank_steel", Fluids.WATER, 128000);
        vessel(at(4, 0, z), cap, Direction.WEST, Direction.EAST, Direction.UP);
        set(at(7, 0, z), withProps(BioContent.CLAMP.get().defaultBlockState(), VesselBlock.PROPS, Direction.WEST, Direction.EAST));
        sign(at(7, 0, z - 1), "Зажим: ПКМ");
        set(at(8, 0, z), withProps(BioContent.CHECK_VALVE.get().defaultBlockState().setValue(CheckValveBlock.FACING, Direction.EAST), VesselBlock.PROPS,
                Direction.WEST, Direction.EAST));
        sign(at(8, 0, z - 1), "Обратный клапан");
        for (int x = 9; x <= 10; x++) vessel(at(x, 0, z), cap, Direction.WEST, Direction.EAST);
        set(at(11, 0, z), BioContent.LIVING_TANK.get().defaultBlockState());
        sign(at(11, 0, z - 1), "Живой бак (приём)");
        sign(at(5, 0, z - 1), "Капиллярный пучок");

        // LV-сеть: резервуар → мышечный насос → волокнистые сосуды → обратный клапан → живой бак
        z = 34;
        tank(at(2, 0, z), "universal_tank_steel", Fluids.WATER, 128000);
        set(at(3, 0, z), BioContent.MUSCLE_PUMP.get().defaultBlockState().setValue(MusclePumpBlock.FACING, Direction.EAST));
        MusclePumpBlockEntity pump = be(at(3, 0, z), MusclePumpBlockEntity.class);
        if (pump != null) {
            pump.n1().fill(BioFluids.N1.source().get(), 250, false);
            pump.e1().fill(BioFluids.E1.source().get(), 250, false);
        }
        sign(at(3, 0, z - 1), "Мышечный насос LV");
        Block narrow = BioContent.VESSELS.get("narrow_fibrous_vessel").get(), working = BioContent.VESSELS.get("working_fibrous_vessel").get();
        for (int x = 4; x <= 6; x++) vessel(at(x, 0, z), narrow, Direction.WEST, Direction.EAST);
        sign(at(5, 0, z - 1), "Узкий волокнистый");
        vessel(at(7, 0, z), working, Direction.WEST, Direction.EAST);
        sign(at(7, 0, z - 1), "Рабочий волокнистый");
        set(at(8, 0, z), withProps(BioContent.CHECK_VALVE.get().defaultBlockState().setValue(CheckValveBlock.FACING, Direction.EAST), VesselBlock.PROPS,
                Direction.WEST, Direction.EAST));
        set(at(9, 0, z), BioContent.LIVING_TANK.get().defaultBlockState());
        sign(at(9, 0, z - 1), "Живой бак");
        // ядро-кормилец рядом (не вплотную к сети): поддержание LV-сосудов идёт от N0 колонии
        core(at(12, 0, z + 2));
        sign(at(12, 0, z + 1), "Ядро: питание стенок");

        // сундуки: всё Био
        List<ItemStack> all = new ArrayList<>();
        BioItems.TAB.forEach(i -> all.add(new ItemStack(i.get(), i.get().getDefaultMaxStackSize() > 1 ? 16 : 1)));
        List<ItemStack> blocks = new ArrayList<>();
        BioContent.STATION_ITEMS.forEach(i -> blocks.add(new ItemStack(i.get(), 4)));
        BioContent.VESSEL_ITEMS.forEach(i -> blocks.add(new ItemStack(i.get(), 16)));
        blocks.add(new ItemStack(BioContent.CORE_ITEM.get()));
        blocks.add(new ItemStack(BioContent.MAW_ITEM.get()));
        chest(at(20, 0, 14), all.subList(0, Math.min(27, all.size())), "Предметы Био 1");
        if (all.size() > 27) chest(at(22, 0, 14), all.subList(27, all.size()), "Предметы Био 2");
        chest(at(24, 0, 14), blocks, "Блоки Био");
        // жидкости Био — в капсулах
        List<ItemStack> caps = new ArrayList<>();
        for (BioFluids.Def d : BioFluids.ALL.values()) caps.add(CapsuleItem.withContent(new ItemStack(FluidContainers.UNIVERSAL_CAPSULE.get()),
                FluidContent.of(d.source().get(), CapsuleItem.CAPACITY)));
        chest(at(26, 0, 14), caps, "Жидкости Био");
    }

    private UniversalTankBlockEntity tank(BlockPos p, String id, Fluid f, int amount) {
        set(p, FluidContainers.TANKS.get(id).get().defaultBlockState());
        UniversalTankBlockEntity t = be(p, UniversalTankBlockEntity.class);
        if (t != null && f != Fluids.EMPTY) insert(t.tank, f, amount);
        return t;
    }

    // --- жидкости ----------------------------------------------------------------------------------------------------
    private void fluids() {
        // трубы: ряды — материалы, столбцы — размеры
        String[] mats = {"wood", "copper", "bronze", "steel", "stainless_steel", "titanium", "tungsten_steel", "plastic", "polytetrafluoroethylene"};
        String[] sizes = {"tiny", "small", "medium", "large", "huge"};
        for (int r = 0; r < mats.length; r++) {
            for (int c = 0; c < sizes.length; c++) {
                DeferredBlock<PipeBlock> pb = IISMachines.PIPES.get("pipe_" + sizes[c] + "_" + mats[r]);
                if (pb == null) continue;
                BlockPos p = at(36 + 4 * c, 0, 2 + 3 * r);
                set(p, withProps(pb.get().defaultBlockState(), PipeBlock.PROPS, Direction.EAST, Direction.WEST));
                sign(p.north(), nameOf(pb.get()));
            }
        }
        // работающая линия: резервуар над трубой → 6 медных труб → резервуар
        int z = 32;
        tank(at(36, 1, z), "universal_tank_steel", Fluids.WATER, 128000);
        for (int x = 36; x <= 41; x++) {
            BlockState ps = withProps(IISMachines.PIPES.get("pipe_small_copper").get().defaultBlockState(), PipeBlock.PROPS, Direction.EAST, Direction.WEST);
            if (x == 36) ps = ps.setValue(PipeBlock.PROPS.get(Direction.UP), true).setValue(PipeBlock.PROPS.get(Direction.WEST), false);
            set(at(x, 0, z), ps);
        }
        tank(at(42, 0, z), "universal_tank_bronze", Fluids.EMPTY, 0);
        sign(at(36, 0, z - 1), "Резервуар → медные трубы");
        sign(at(42, 0, z - 1), "→ резервуар (ПКМ)");
        tank(at(46, 0, z), "universal_tank_bronze", Fluids.LAVA, 16000);
        sign(at(46, 0, z - 1), "Бронзовый резервуар: лава");
        tank(at(49, 0, z), "universal_tank_steel", GtFluids.get("molten_iron") != null ? GtFluids.get("molten_iron").source().get() : Fluids.WATER, 64000);
        sign(at(49, 0, z - 1), "Стальной резервуар: расплав железа");

        // стена рамок: капсулы жидкостей
        int wz = 40;
        for (int x = 34; x <= 62; x++) for (int dy = 0; dy < 6; dy++) set(at(x, dy, wz), Blocks.WHITE_CONCRETE.defaultBlockState());
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(new ItemStack(FluidContainers.CAPSULE.get()));
        stacks.add(new ItemStack(FluidContainers.UNIVERSAL_CAPSULE.get()));
        ItemStack empty = new ItemStack(FluidContainers.CAPSULE.get());
        stacks.add(CapsuleItem.withContent(empty, FluidContent.of(Fluids.WATER, 1000)));
        stacks.add(CapsuleItem.withContent(empty, FluidContent.of(Fluids.LAVA, 1000)));
        String[] pick = {"molten_iron", "molten_copper", "molten_tin", "molten_bronze", "molten_steel", "molten_gold", "molten_silver", "molten_lead",
                "molten_aluminium", "molten_glass", "plasma_helium", "plasma_nitrogen", "plasma_oxygen", "plasma_iron", "oxygen", "hydrogen", "nitrogen",
                "chlorine", "fluorine", "helium", "methane", "air", "oil", "lubricant", "creosote", "seedoil", "glue", "ethanol", "sulfuricacid",
                "mercury", "honey", "milk", "biomass", "fishoil", "nitroglycerin", "rocket_fuel", "liquid_air", "ice", "molten_rubber", "molten_plastic"};
        for (String id : pick) {
            GtFluids.Def d = GtFluids.get(id);
            if (d != null) stacks.add(CapsuleItem.withContent(empty, FluidContent.of(d.source().get(), 1000)));
        }
        for (GtFluids.Def d : GtFluids.all().values()) {
            if (stacks.size() >= 29 * 4) break;
            ItemStack st = CapsuleItem.withContent(empty, FluidContent.of(d.source().get(), 1000));
            if (stacks.stream().noneMatch(x -> ItemStack.isSameItemSameComponents(x, st))) stacks.add(st);
        }
        for (int i = 0; i < stacks.size(); i++) frame(at(34 + i % 29, 4 - i / 29, wz), stacks.get(i));
        sign(at(48, 0, wz - 2), "Капсулы: наведите на рамку");
    }

    // --- Кибер -------------------------------------------------------------------------------------------------------
    private void cyber() {
        // пар: горячий котёл → паровой дробитель над ним, руда в работе
        BlockPos boiler = at(68, 0, 2);
        set(boiler, IISMachines.BOILERS.get(BoilerType.COAL_BRONZE).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BoilerBlockEntity bb = be(boiler, BoilerBlockEntity.class);
        if (bb != null) {
            for (int i = 0; i < 16; i++) bb.fillWaterFromBucket();
            bb.logic().temperature = 500;
            bb.setItem(BoilerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 64));
        }
        BlockPos mac = boiler.above();
        set(mac, IISMachines.STEAM_MACHINES.get(SteamMachineType.MACERATOR.blockId(SteamMachineType.Tier.BRONZE)).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        SteamMachineBlockEntity mb = be(mac, SteamMachineBlockEntity.class);
        if (mb != null) mb.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite"), 64));
        sign(boiler.north(), "Котёл → паровой дробитель");

        // LV III (D76): формы отвердителя, программная схема, хром, марганец, нержавейка
        List<ItemStack> lv3 = new ArrayList<>();
        for (String id : List.of("shape_empty", "mold_ingot", "mold_plate", "mold_nugget", "mold_gear", "mold_block", "mold_ball", "programmed_circuit")) {
            lv3.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, id))));
        }
        List<ItemStack> metals = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            String p = id.getPath();
            if (id.getNamespace().equals(IIS.MODID) && (p.endsWith("_chrome") || p.endsWith("_manganese") || p.contains("stainless_steel") || p.startsWith("wire_gold")
                    || p.startsWith("cable_gold"))) {
                metals.add(new ItemStack(BuiltInRegistries.ITEM.getValue(id), 16));
            }
        }
        metals.sort(Comparator.comparing(st -> BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()));
        chest(at(72, 0, 2), lv3, "LV III: формы и схема");
        chest(at(74, 0, 2), metals.subList(0, Math.min(27, metals.size())), "Хром, марганец, нержавейка");
        if (metals.size() > 27) chest(at(76, 0, 2), metals.subList(27, Math.min(54, metals.size())), "Нержавейка, золото");

        // галерея: все остальные блоки Кибера, ряды по 10
        Set<Identifier> skip = new HashSet<>();
        BioContent.BLOCKS.getEntries().forEach(h -> skip.add(h.getId()));
        FluidContainers.BLOCKS.getEntries().forEach(h -> skip.add(h.getId()));
        List<Identifier> ores = new ArrayList<>(), rest = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals(IIS.MODID) || skip.contains(id) || id.getPath().startsWith("pipe_")) continue;
            Block b = BuiltInRegistries.BLOCK.getValue(id);
            if (b instanceof LiquidBlock) continue;
            (id.getPath().contains("ore") ? ores : rest).add(id);
        }
        Collections.sort(rest);
        Collections.sort(ores);
        for (int i = 0; i < rest.size(); i++) {
            BlockPos p = at(66 + 3 * (i % 12), 0, 8 + 3 * (i / 12));
            Block b = BuiltInRegistries.BLOCK.getValue(rest.get(i));
            BlockState st = b.defaultBlockState();
            if (st.hasProperty(MachineBlock.FACING)) st = st.setValue(MachineBlock.FACING, Direction.NORTH);
            set(p, st);
            sign(p.north(), nameOf(rest.get(i)));
        }
        // стена руд с табличками
        int wz = 8 + 3 * ((rest.size() + 11) / 12) + 4;
        for (int i = 0; i < ores.size(); i++) {
            BlockPos p = at(66 + i % 48, i / 48, wz);
            set(p, BuiltInRegistries.BLOCK.getValue(ores.get(i)).defaultBlockState());
            wallSign(p, nameOf(ores.get(i)));
        }
    }

    private void shuttle() {
        ShuttleSpawner.build(level, 129, 17, y0, 0, false, 125.0, 20.0, 1234L);
        sign(at(124, 0, 16), "Аварийный челнок Био");
    }

    private void titles() {
        sign(at(14, 0, 2), "БИО: колония, места, LV, сосуды");
        sign(at(46, 0, -2), "ЖИДКОСТИ: трубы, резервуары, капсулы");
        sign(at(76, 0, -2), "КИБЕР: пар, блоки, руды");
        sign(at(30, 0, -6), "Стенд ИИС 2 — эталон");
    }
}
