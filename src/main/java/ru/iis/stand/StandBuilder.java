package ru.iis.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
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
import ru.iis.cover.CoverKind;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.UniversalTankBlockEntity;
import ru.iis.gear.GearContent;
import ru.iis.lv.GeneratorType;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.BatteryBufferBlockEntity;
import ru.iis.lv.block.CableBlock;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.block.GeneratorBlockEntity;
import ru.iis.lv.block.TransformerBlock;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.BoilerType;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.BoilerBlockEntity;
import ru.iis.machine.block.ItemPipeBlock;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.reactor.NuclearReactorBlockEntity;
import ru.iis.reactor.ReactorContent;
import ru.iis.space.RocketEntity;
import ru.iis.space.RocketItem;
import ru.iis.space.SpaceContent;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Стендовый мир-эталон (D74, переделан в D124): вдоль главной аллеи стоят залы по темам, в каждом блоки разложены таблицами «вид × уровень» с подписями на русском.
 * Первый зал — рабочая сеть: солнце, трансформаторы, замкнутые производственные круги на предметных и жидкостных трубах, печь с настоящей структурой; всё идёт само и не кончается.
 * Что не попало ни в один зал, собирается в зале «Прочее», поэтому показано всё содержимое мода.
 * Строится командой {@code /iis stand} (оператор, ровное место) или сервером разработки с {@code -Diis.stand=build}: затем он сохраняет мир и останавливается.
 */
public final class StandBuilder {
    /** Откуда начинаются залы по z; аллея севернее. */
    private static final int HALL_Z = 8, AVENUE_Z0 = -3, AVENUE_Z1 = 4, SPAWN_X = 17, SPAWN_Z = 0;
    private static final String[] TIERS = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv"};
    private static final String[] TIER_NAMES = {"LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV"};
    private static final String[] TEXT_COLOR = {"gray", "gold", "yellow", "aqua", "blue", "light_purple", "red", "dark_aqua", "white"};
    private static final Block[] TIER_FLOOR = {Blocks.LIGHT_GRAY_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.RED_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.WHITE_CONCRETE};

    private final ServerLevel level;
    private final int y0;
    private final Map<String, String> names;
    /** Начало текущего зала: все at() от него. */
    private int ox, oz;
    private int cursor;
    private boolean mirrored = true;
    private int hallMinX, hallMaxX, hallMaxZ;
    private final Set<Block> shown = new HashSet<>();
    private final Set<Long> forced = new HashSet<>();
    private final Set<Long> tinted = new HashSet<>();
    private final List<Hall> halls = new ArrayList<>();
    private final List<View> views = new ArrayList<>();
    private final List<BlockPos> controllers = new ArrayList<>();
    /** Зал под стенд строится с запасом: чанки рабочей сети и Био остаются загруженными. */
    private final Set<Long> keepForced = new HashSet<>();
    private StandNetworkInfo network;
    private BlockPos bioOrigin;

    private record Hall(String title, int x, int z) {}

    /** Точка съёмки: для проверки вида клиентом. */
    public record View(String name, double x, double y, double z, float yaw, float pitch) {}

    /** Что проверять в работающей сети. */
    public record StandNetworkInfo(List<BlockPos> machines, BlockPos ebf, BlockPos ebfInput, BlockPos ebfOutput, BlockPos overflow) {}

    private StandBuilder(ServerLevel level, int y0) {
        this.level = level;
        this.y0 = y0;
        this.names = loadRu();
    }

    /** Результат постройки: что оставить загруженным и что проверять. */
    public record Result(Set<Long> allForced, Set<Long> keep, List<View> views, List<BlockPos> controllers, StandNetworkInfo network, BlockPos bioOrigin, int shown, int leftovers) {}

    /** Построить; y0 — уровень, на котором стоят блоки (над полом). */
    public static Result build(ServerLevel level, int y0) {
        StandBuilder b = new StandBuilder(level, y0);
        b.networkHall();
        b.machinesHall();
        b.energyHall();
        b.automationHall();
        b.wiresHall();
        b.pipesHall();
        b.hatchesHall();
        b.multisHall();
        b.bigHall();
        b.partsHall();
        b.gearHall();
        b.oresHall();
        b.spaceHall();
        b.railHall();
        b.bioHall();
        int left = b.leftoversHall();
        b.avenue();
        StandData.mark(level);
        MinecraftServer server = level.getServer();
        for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule respawn_radius 0",
                "time set noon", "weather clear", "setworldspawn " + SPAWN_X + " " + b.y0 + " " + SPAWN_Z + " 180 0")) {
            b.command(c);
        }
        IIS.LOGGER.info("[IIS] stand built at y={}: {} blocks shown, {} in the leftovers hall, {} halls", y0, b.shown.size(), left, b.halls.size());
        return new Result(b.forced, b.keepForced, b.views, b.controllers, b.network, b.bioOrigin, b.shown.size(), left);
    }

    private void command(String c) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c); // ошибки команды всё равно идут в журнал
    }

    // --- общее -------------------------------------------------------------------------------------------------------
    /** Местный x растёт на запад: зритель смотрит на юг, поэтому таблицы и круги читаются слева направо. Био строится как есть. */
    private BlockPos at(int x, int dy, int z) {
        return new BlockPos(mirrored ? ox - x : ox + x, y0 + dy, oz + z);
    }

    private void track(BlockPos p) {
        hallMinX = Math.min(hallMinX, p.getX());
        hallMaxX = Math.max(hallMaxX, p.getX());
        hallMaxZ = Math.max(hallMaxZ, p.getZ());
        long key = ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4);
        if (forced.add(key)) level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
    }

    private void set(BlockPos p, BlockState s) {
        track(p);
        level.setBlock(p, s, 2);
        shown.add(s.getBlock());
    }

    private <T extends BlockEntity> T be(BlockPos p, Class<T> c) {
        BlockEntity b = level.getBlockEntity(p);
        return c.isInstance(b) ? c.cast(b) : null;
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
        String n = names.get("block." + id.getNamespace() + "." + id.getPath());
        if (n == null) n = names.get("item." + id.getNamespace() + "." + id.getPath());
        return n == null ? id.getPath() : n;
    }

    private String nameOf(Block b) {
        return nameOf(BuiltInRegistries.BLOCK.getKey(b));
    }

    private static Block block(String id) {
        Identifier key = Identifier.fromNamespaceAndPath(IIS.MODID, id);
        return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key) : null;
    }

    private static ItemStack stack(String id, int n) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath(IIS.MODID, id);
        return BuiltInRegistries.ITEM.containsKey(key) ? new ItemStack(BuiltInRegistries.ITEM.getValue(key), n) : ItemStack.EMPTY;
    }

    /** Разбить подпись на строки таблички: до 4 по ~15 знаков, длинное слово режется. */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            if (w.length() > 15) w = w.substring(0, 14) + ".";
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

    /** SignText неизменяем: каждый вызов возвращает новый объект. Белый светящийся текст на тёмном дереве читается и в тени. */
    private void writeSign(BlockPos p, String text, String runCommand) {
        if (!(level.getBlockEntity(p) instanceof SignBlockEntity s)) return;
        SignText t = new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true);
        List<String> lines = wrap(text);
        for (int i = 0; i < lines.size(); i++) {
            Component c = Component.literal(lines.get(i));
            if (runCommand != null) c = Component.literal(lines.get(i)).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(runCommand)));
            t = t.setMessage(i, c);
        }
        s.setText(t, true);
        s.setWaxed(true);
        s.setChanged();
    }

    /** Табличка на полу (читается с севера). */
    private void sign(BlockPos p, String text) {
        set(p, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
        writeSign(p, text, null);
    }

    /** Табличка на северной грани блока {@code on}. */
    private void wallSign(BlockPos on, String text) {
        BlockPos p = on.north();
        set(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        writeSign(p, text, null);
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

    /** Плавающая надпись: не перегружает площадку, видна издали, всегда повёрнута к зрителю. */
    private void label(double x, double y, double z, String text, String color, double scale, boolean bold) {
        String esc = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String s = String.format(Locale.ROOT, "%.2ff", scale);
        command(String.format(Locale.ROOT, "summon minecraft:text_display %.2f %.2f %.2f {text:{text:\"%s\",color:\"%s\",bold:%s},billboard:\"vertical\",alignment:\"center\",line_width:280,shadow:1b,"
                + "background:1140850688,view_range:1.5f,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%s,%s,%s]}}",
                x, y, z, esc, color, bold ? "1b" : "0b", s, s, s));
    }

    private void labelAt(int x, double dy, int z, String text, String color, double scale) {
        label((mirrored ? ox - x : ox + x) + 0.5, y0 + dy, oz + z + 0.5, text, color, scale, false);
    }

    /** Полосы на полу: чанк и пол зала их не закрашивают. */
    private void paint(int x, int z, Block b) {
        BlockPos p = at(x, -1, z);
        track(p);
        tinted.add(p.asLong());
        level.setBlock(p, b.defaultBlockState(), 2);
    }

    private void paintLine(int x1, int z1, int x2, int z2, Block b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) paint(x, z, b);
    }

    /** Блок для показа: лицом к зрителю на севере, провода и трубы с отростками, чтобы было видно сечение. */
    private BlockState display(Block b) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(MachineBlock.FACING)) s = s.setValue(MachineBlock.FACING, Direction.NORTH);
        if (s.hasProperty(HatchBlock.FACING)) s = s.setValue(HatchBlock.FACING, Direction.NORTH);
        if (b instanceof CableBlock) s = withProps(s, CableBlock.PROPS, Direction.EAST, Direction.WEST);
        if (b instanceof PipeBlock) s = withProps(s, PipeBlock.PROPS, Direction.EAST, Direction.WEST);
        if (b instanceof ItemPipeBlock) s = withProps(s, ItemPipeBlock.PROPS, Direction.EAST, Direction.WEST);
        return s;
    }

    // --- залы и таблицы ----------------------------------------------------------------------------------------------
    private void beginHall(String title, String about) {
        ox = cursor;
        oz = HALL_Z;
        hallMinX = ox;
        hallMaxX = ox;
        hallMaxZ = oz;
        currentTitle = (halls.size() + 1) + ". " + title;
        currentAbout = about;
    }

    private String currentTitle = "", currentAbout = "";

    /** Закрыть зал: пол с лампами, бордюр, заголовок над входом, стрелка к началу. */
    private void endHall(Block floor) {
        int x0 = hallMinX - 2, x1 = hallMaxX + 2, z0 = oz - 3, z1 = hallMaxZ + 3;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos p = new BlockPos(x, y0 - 1, z);
                track(p);
                if (tinted.contains(p.asLong())) continue;
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean lamp = !edge && Math.floorMod(x - x0, 8) == 4 && Math.floorMod(z - z0, 8) == 4;
                level.setBlock(p, (edge ? Blocks.QUARTZ_BLOCK : lamp ? Blocks.SEA_LANTERN : floor).defaultBlockState(), 2);
            }
        }
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}}) {
            for (int dy = 0; dy < 4; dy++) level.setBlock(new BlockPos(c[0], y0 + dy, c[1]), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(c[0], y0 + 4, c[1]), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        double cx = (x0 + x1) / 2.0;
        halls.add(new Hall(currentTitle, (x0 + x1) / 2, z0));
        label(cx, y0 + 6.6, z0 - 0.5, currentTitle, "gold", 3.2, true);
        label(cx, y0 + 4.2, z0 - 0.5, currentAbout, "white", 1.0, false);
        BlockPos back = new BlockPos(x1 - 1, y0, z0);
        track(back);
        level.setBlock(back, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 2);
        writeSign(back, "К началу (ПКМ)", "/tp @s " + SPAWN_X + " " + y0 + " " + SPAWN_Z + " 180 0");
        views.add(new View(currentTitle, cx, y0 + 12, z0 - 7, 0, 32));
        views.add(new View(currentTitle + " 2", cx, y0 + 3, z0 + 3, 0, 18));
        cursor = x0 - 7;
    }

    /** Строка таблицы: подпись и блоки по столбцам (null — пропуск). */
    private record Row(String label, List<Block> cells) {}

    /**
     * Таблица: подписи строк слева, цветной столбец под каждым видом, заголовки столбцов висят над началом и повторяются каждые 12 строк.
     * Длинная таблица переносится вправо. Возвращает x за таблицей.
     */
    private int table(int x, int z, List<String> cols, List<String> colColors, Block[] colFloor, List<Row> rows, int maxRows, int stepX) {
        int width = 1 + cols.size() * stepX + 2, cur = x;
        for (int i = 0; i < rows.size(); i++) {
            int r = i % maxRows;
            if (i > 0 && r == 0) cur += width + 2;
            if (r % 12 == 0) {
                for (int c = 0; c < cols.size(); c++) {
                    labelAt(cur + 2 + c * stepX, 2.0, z + r * 2 - 2, cols.get(c), colColors == null ? "white" : colColors.get(c), 1.0);
                }
            }
            Row row = rows.get(i);
            sign(at(cur, 0, z + r * 2), row.label());
            for (int c = 0; c < row.cells().size(); c++) {
                Block b = row.cells().get(c);
                if (colFloor != null) {
                    paint(cur + 2 + c * stepX, z + r * 2, colFloor[c]);
                    paint(cur + 2 + c * stepX, z + r * 2 - 1, colFloor[c]);
                }
                if (b != null) set(at(cur + 2 + c * stepX, 0, z + r * 2), display(b));
            }
        }
        return cur + width;
    }

    private int tierTable(int x, int z, List<Row> rows, int maxRows) {
        return table(x, z, List.of(TIER_NAMES), List.of(TEXT_COLOR).subList(0, 8), Arrays.copyOf(TIER_FLOOR, 8), rows, maxRows, 2);
    }

    /** Подпись строки: имя базового уровня без слов «базовый», «продвинутый» и уровня. */
    private String rowName(Block b) {
        String n = nameOf(b);
        for (String w : List.of("Базовый ", "Базовая ", "Базовое ", "Basic ", "Продвинутый ", "Продвинутая ", "Продвинутое ", "Advanced ")) if (n.startsWith(w)) n = n.substring(w.length());
        n = n.replaceAll(" \\((LV|MV|HV|EV|IV|LuV|ZPM|UV)\\)$", "").replaceAll(" (LV|MV|HV|EV|IV|LuV|ZPM|UV)$", "").replaceAll("^(LV|MV|HV|EV|IV|LuV|ZPM|UV) ", "");
        return n.isEmpty() ? nameOf(b) : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private Row tierRow(String prefix, String label) {
        List<Block> cells = new ArrayList<>();
        Block first = null;
        for (String t : TIERS) {
            Block b = block(prefix + "_" + t);
            cells.add(b);
            if (first == null) first = b;
        }
        return new Row(label != null ? label : first == null ? prefix : rowName(first), cells);
    }

    /** Экспонат: блок с табличкой перед ним, шагом step; ряд переносится по width. */
    private int[] gallery(int x, int z, List<Block> blocks, int perRow, int stepX, int stepZ) {
        int lastZ = z;
        for (int i = 0; i < blocks.size(); i++) {
            int cx = x + stepX * (i % perRow), cz = z + stepZ * (i / perRow);
            set(at(cx, 0, cz), display(blocks.get(i)));
            sign(at(cx, 0, cz - 1), nameOf(blocks.get(i)));
            lastZ = cz;
        }
        return new int[]{x + stepX * perRow, lastZ + stepZ};
    }

    /** Предмет в рамке на тумбе с подписью. */
    private void itemCell(int x, int z, ItemStack stack, String label) {
        if (stack.isEmpty()) return;
        BlockPos base = at(x, 0, z);
        set(base, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        set(base.above(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        frame(base.above(), stack);
        wallSign(base, label);
    }

    private String itemName(ItemStack s) {
        return nameOf(BuiltInRegistries.ITEM.getKey(s.getItem()));
    }

    private int items(int x, int z, List<String> ids, int perRow) {
        List<ItemStack> l = new ArrayList<>();
        for (String id : ids) {
            ItemStack s = stack(id, 1);
            if (!s.isEmpty()) l.add(s);
        }
        int lastZ = z;
        for (int i = 0; i < l.size(); i++) {
            int cz = z + 3 * (i / perRow);
            itemCell(x + 2 * (i % perRow), cz, l.get(i), itemName(l.get(i)));
            lastZ = cz;
        }
        return lastZ + 3;
    }

    // --- 1. Рабочая сеть ---------------------------------------------------------------------------------------------
    private BlockPos cable(int x, int z, String id) {
        BlockPos p = at(x, 0, z);
        BlockState s = LvContent.CABLES.get(id).get().defaultBlockState();
        for (Direction d : Direction.values()) s = s.setValue(CableBlock.PROPS.get(d), CableBlock.canConnect(level.getBlockState(p.relative(d)), d.getOpposite()));
        set(p, s);
        return p;
    }

    private void itemPipe(int x, int dy, int z, String id, Direction... sides) {
        set(at(x, dy, z), withProps(IISMachines.ITEM_PIPES.get(id).get().defaultBlockState(), ItemPipeBlock.PROPS, sides));
    }

    private void fluidPipe(int x, int dy, int z, String id, Direction... sides) {
        set(at(x, dy, z), withProps(IISMachines.PIPES.get(id).get().defaultBlockState(), PipeBlock.PROPS, sides));
    }

    private ElectricMachineBlockEntity machine(int x, int z, LvMachineType t, Direction facing) {
        BlockPos p = at(x, 0, z);
        set(p, LvContent.MACHINES.get(t).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        ElectricMachineBlockEntity m = be(p, ElectricMachineBlockEntity.class);
        m.energy.stored = m.energy.capacity;
        return m;
    }

    private BlockState hatch(HatchType t, Direction facing) {
        return MultiContent.HATCHES.get(t).get().defaultBlockState().setValue(HatchBlock.FACING, facing);
    }

    private void networkHall() {
        cursor = 0;
        beginHall("Рабочая сеть", "Всё здесь работает прямо сейчас: круги замкнуты, энергия от солнца. Подойдите к любой машине и откройте её");
        List<BlockPos> working = new ArrayList<>();
        String HV = "cable_gold_4x", MV = "cable_copper_4x", LV = "cable_tin_4x", ITEM = "item_pipe_medium_brass", FLUID = "pipe_small_stainless_steel";
        Block yellow = Blocks.YELLOW_CONCRETE, orange = Blocks.ORANGE_CONCRETE, gray = Blocks.LIGHT_GRAY_CONCRETE, green = Blocks.LIME_CONCRETE, blue = Blocks.LIGHT_BLUE_CONCRETE;
        labelAt(20, 1.0, -1, "Пол показывает, что идёт по линии:  жёлтый — HV 512 В   оранжевый — MV 128 В   серый — LV 32 В   зелёный — предметы   голубой — жидкости", "white", 0.8);

        // солнечная станция: три буфера HV с панелями на крышке, батареи внутри
        BlockPos[] panels = {at(2, 0, 6), at(3, 0, 5), at(3, 0, 7)};
        Direction[] fronts = {Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (int i = 0; i < 3; i++) {
            set(panels[i], LvContent.BATTERY_BUFFERS_HV.get(1).get().defaultBlockState().setValue(MachineBlock.FACING, fronts[i]));
            BatteryBufferBlockEntity b = be(panels[i], BatteryBufferBlockEntity.class);
            b.covers().attach(Direction.UP, CoverKind.SOLAR_HV);
            for (int k = 0; k < 4; k++) {
                ItemStack bat = new ItemStack(LvContent.BATTERY_LITHIUM_HV.get());
                BatteryItem.setCharge(bat, ((BatteryItem) bat.getItem()).capacity);
                b.setItem(k, bat);
            }
            b.energy.stored = b.energy.capacity;
            working.add(panels[i]);
        }
        for (int x = 3; x <= 6; x++) cable(x, 6, HV);
        paintLine(3, 6, 6, 6, yellow);
        sign(at(1, 0, 4), "1. Панели HV на буферах");
        labelAt(3, 3.0, 6, "1. Солнечная станция\nТри панели HV по 512 EU/t на аккумуляторных буферах. В этом мире всегда полдень", "yellow", 0.8);

        // трансформатор HV → MV
        set(at(7, 0, 6), LvContent.TRANSFORMER_MV.get().defaultBlockState().setValue(TransformerBlock.FACING, Direction.EAST));
        sign(at(7, 0, 5), "2. Трансформатор HV → MV");
        for (int x = 8; x <= 23; x++) cable(x, 6, MV);
        paintLine(8, 6, 23, 6, orange);
        labelAt(7, 3.0, 8, "2. Трансформатор\nЛицо принимает 512 В, остальные стороны отдают 128 В", "gold", 0.8);

        // MV-круг: экстрактор плавит слитки, расплав идёт по трубе, отвердитель льёт слитки обратно
        ElectricMachineBlockEntity extractor = machine(10, 5, LvMachineType.FLUID_EXTRACTOR_MV, Direction.NORTH);
        extractor.fluidTransfer = true;
        extractor.setOutputSide(Direction.WEST, true);
        extractor.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        ElectricMachineBlockEntity solidifier = machine(14, 5, LvMachineType.FLUID_SOLIDIFIER_MV, Direction.NORTH);
        solidifier.itemTransfer = true;
        solidifier.setOutputSide(Direction.WEST, false);
        solidifier.setItem(0, stack("mold_ingot", 1));
        for (int x = 11; x <= 13; x++) fluidPipe(x, 0, 5, FLUID, Direction.WEST, Direction.EAST);
        paintLine(11, 5, 13, 5, blue);
        itemPipe(15, 0, 5, ITEM, Direction.EAST, Direction.UP);
        itemPipe(15, 1, 5, ITEM, Direction.DOWN, Direction.EAST);
        for (int x = 14; x >= 10; x--) itemPipe(x, 1, 5, ITEM, Direction.WEST, Direction.EAST);
        itemPipe(9, 1, 5, ITEM, Direction.WEST, Direction.DOWN);
        itemPipe(9, 0, 5, ITEM, Direction.UP, Direction.WEST);
        paintLine(9, 4, 15, 4, green);
        sign(at(10, 0, 3), "3. Экстрактор: слиток в расплав");
        sign(at(14, 0, 3), "4. Отвердитель: расплав в слиток");
        sign(at(12, 0, 3), "Труба: расплав железа");
        labelAt(12, 3.6, 5, "3–4. Круг на MV\nСлиток → расплав по жидкостной трубе → форма → слиток по предметной трубе над машинами и снова в экстрактор", "gold", 0.8);
        working.add(at(10, 0, 5));
        working.add(at(14, 0, 5));

        // настоящая печь: структура, люк MV, автолюк обслуживания, конвейер на выходной шине, дробилка и обратно
        int cx = 16, cz = 9;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            set(at(cx + dx, 0, cz + dz), MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
            if (dx != 0 || dz != 0) {
                set(at(cx + dx, 1, cz + dz), MultiContent.COIL_CUPRONICKEL.get().defaultBlockState());
                set(at(cx + dx, 2, cz + dz), MultiContent.COIL_CUPRONICKEL.get().defaultBlockState());
            }
            set(at(cx + dx, 3, cz + dz), MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
        }
        set(at(cx, 3, cz), hatch(HatchType.MUFFLER, Direction.UP));
        set(at(cx - 1, 0, cz - 1), hatch(HatchType.ENERGY_MV, Direction.NORTH));
        cable(cx - 1, cz - 2, MV);
        set(at(cx + 1, 0, cz - 1), hatch(HatchType.INPUT_BUS, Direction.NORTH));
        set(at(cx - 1, 0, cz), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
        set(at(cx + 1, 0, cz + 1), hatch(HatchType.MAINTENANCE_AUTO, Direction.SOUTH));
        set(at(cx, 0, cz - 1), MultiContent.EBF.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        EbfBlockEntity ebf = be(at(cx, 0, cz - 1), EbfBlockEntity.class);
        ebf.maintenance().setMask(63);
        be(at(cx + 1, 0, cz - 1), ItemBusBlockEntity.class).setItem(0, stack("dust_aluminium", 16));
        be(at(cx - 1, 0, cz), ItemBusBlockEntity.class).covers().attach(Direction.EAST, CoverKind.CONVEYOR, 2);
        itemPipe(14, 0, 9, ITEM, Direction.WEST, Direction.EAST);
        ElectricMachineBlockEntity grinder = machine(13, 9, LvMachineType.MACERATOR_MV, Direction.NORTH);
        grinder.itemTransfer = true;
        grinder.setOutputSide(Direction.SOUTH, false);
        for (int z = 7; z <= 9; z++) cable(12, z, MV);
        paintLine(12, 7, 12, 9, orange);
        paint(cx - 1, cz - 2, orange);
        itemPipe(13, 0, 10, ITEM, Direction.NORTH, Direction.SOUTH);
        itemPipe(13, 0, 11, ITEM, Direction.NORTH, Direction.WEST);
        for (int x = 14; x <= 17; x++) itemPipe(x, 0, 11, ITEM, Direction.WEST, Direction.EAST);
        itemPipe(18, 0, 11, ITEM, Direction.EAST, Direction.NORTH);
        for (int z = 10; z >= 8; z--) itemPipe(18, 0, z, ITEM, Direction.NORTH, Direction.SOUTH);
        itemPipe(18, 0, 7, ITEM, Direction.SOUTH, Direction.EAST);
        itemPipe(17, 0, 7, ITEM, Direction.WEST, Direction.SOUTH);
        paintLine(13, 10, 13, 11, green);
        paintLine(14, 11, 18, 11, green);
        paintLine(18, 7, 18, 10, green);
        paint(17, 7, green);
        paint(14, 9, green);
        sign(at(11, 0, 9), "5. Дробитель MV");
        sign(at(16, 0, 12), "Труба: пыль в печь");
        labelAt(16, 5.4, cz, "5–6. Настоящая структура\nЭлектрическая печь плавит алюминиевую пыль. Конвейер на выходной шине отдаёт слиток в трубу, дробитель мелет его, пыль возвращается в печь", "gold", 0.8);
        controllers.add(at(cx, 0, cz - 1));
        working.add(at(13, 0, 9));

        // трансформатор MV → LV
        set(at(24, 0, 6), LvContent.TRANSFORMER.get().defaultBlockState().setValue(TransformerBlock.FACING, Direction.EAST));
        sign(at(24, 0, 5), "7. Трансформатор MV → LV");
        for (int x = 25; x <= 38; x++) cable(x, 6, LV);
        paintLine(25, 6, 38, 6, gray);

        // LV-круг: дробитель и печь, ограничительная ветка в сундук-перелив
        ElectricMachineBlockEntity mac = machine(27, 5, LvMachineType.MACERATOR, Direction.NORTH);
        mac.itemTransfer = true;
        mac.setOutputSide(Direction.WEST, false);
        mac.setItem(0, new ItemStack(Items.IRON_INGOT, 24));
        ElectricMachineBlockEntity furnace = machine(31, 5, LvMachineType.ELECTRIC_FURNACE, Direction.NORTH);
        furnace.itemTransfer = true;
        furnace.setOutputSide(Direction.WEST, false);
        for (int x = 28; x <= 30; x++) itemPipe(x, 0, 5, ITEM, Direction.WEST, Direction.EAST);
        paintLine(28, 5, 30, 5, green);
        itemPipe(32, 0, 5, ITEM, Direction.EAST, Direction.UP);
        itemPipe(32, 1, 5, ITEM, Direction.DOWN, Direction.EAST);
        for (int x = 31; x >= 27; x--) {
            if (x == 29) itemPipe(x, 1, 5, ITEM, Direction.WEST, Direction.EAST, Direction.NORTH);
            else itemPipe(x, 1, 5, ITEM, Direction.WEST, Direction.EAST);
        }
        itemPipe(26, 1, 5, ITEM, Direction.WEST, Direction.DOWN);
        itemPipe(26, 0, 5, ITEM, Direction.UP, Direction.WEST);
        itemPipe(29, 1, 4, "item_pipe_restrictive_medium_brass", Direction.SOUTH, Direction.DOWN);
        chest(at(29, 0, 4), List.of(), "Сундук-перелив");
        paintLine(26, 4, 32, 4, green);
        sign(at(27, 0, 3), "8. Дробитель LV");
        sign(at(31, 0, 3), "9. Электропечь LV");
        labelAt(29, 3.6, 5, "8–9. Круг на LV\nСлиток → дробитель → пыль по трубе → печь → слиток над машинами обратно. Ограничительная труба ведёт в сундук: туда идёт только то, что круг не принял", "gray", 0.8);
        working.add(at(27, 0, 5));
        working.add(at(31, 0, 5));

        // генераторы на той же линии LV: дизель с баком и котёл с турбиной
        BlockPos diesel = at(35, 0, 5);
        set(diesel, LvContent.GENERATORS.get(GeneratorType.DIESEL).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
        tank(at(35, 1, 5), "universal_tank_steel", GtFluids.get("biodiesel").source().get(), 128000);
        sign(at(35, 0, 3), "10. Дизель и бак биодизеля");
        BlockPos turbine = at(37, 0, 5);
        set(turbine, LvContent.GENERATORS.get(GeneratorType.STEAM_TURBINE).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
        BlockPos boiler = at(38, 0, 5);
        set(boiler, IISMachines.BOILERS.get(BoilerType.COAL_BRONZE).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BoilerBlockEntity bb = be(boiler, BoilerBlockEntity.class);
        bb.logic().temperature = 500;
        bb.setItem(BoilerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 64));
        tank(at(38, 1, 5), "universal_tank_steel", Fluids.WATER, 128000);
        set(at(39, 0, 5), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.EAST));
        set(at(39, 1, 5), Blocks.CHEST.defaultBlockState());
        ChestBlockEntity coal = be(at(39, 1, 5), ChestBlockEntity.class);
        for (int i = 0; i < 27; i++) coal.setItem(i, new ItemStack(Items.COAL, 64));
        sign(at(38, 0, 3), "11. Котёл и паровая турбина");
        labelAt(37, 3.6, 5, "10–11. Генераторы на линии LV\nДизель пьёт из бака сверху. Котёл греет воду из бака, пар идёт в турбину рядом, уголь падает из сундука через воронку", "gray", 0.8);
        working.add(diesel);
        working.add(turbine);
        working.add(boiler);

        // водопровод: бак над трубой, медные трубы, бак
        int wz = 10;
        tank(at(27, 1, wz), "universal_tank_steel", Fluids.WATER, 128000);
        for (int x = 27; x <= 32; x++) {
            if (x == 27) fluidPipe(x, 0, wz, "pipe_small_copper", Direction.UP, Direction.WEST);
            else fluidPipe(x, 0, wz, "pipe_small_copper", Direction.WEST, Direction.EAST);
        }
        tank(at(33, 0, wz), "universal_tank_bronze", Fluids.EMPTY, 0);
        paintLine(27, wz, 32, wz, blue);
        sign(at(30, 0, wz - 1), "Водопровод: бак → трубы → бак");

        network = new StandNetworkInfo(working, at(cx, 0, cz - 1), at(cx + 1, 0, cz - 1), at(cx - 1, 0, cz), at(29, 0, 4));
        for (int cxx = (hallMinX - 2) >> 4; cxx <= (hallMaxX + 2) >> 4; cxx++) for (int czz = (oz - 3) >> 4; czz <= (hallMaxZ + 3) >> 4; czz++) keepForced.add(ChunkPos.pack(cxx, czz));
        endHall(Blocks.GRAY_CONCRETE);
        views.add(new View("Сеть станция", ox - 8, y0 + 5, oz + 1, 0, 25));
        views.add(new View("Сеть круг MV", ox - 12, y0 + 4, oz + 1, 0, 20));
        views.add(new View("Сеть круг LV", ox - 30, y0 + 4, oz + 0, 0, 22));
        views.add(new View("Сеть печь", ox - 15, y0 + 6, oz + 3, 0, 30));
    }

    // --- 2. Машины ----------------------------------------------------------------------------------------------------
    private void machinesHall() {
        beginHall("Электрические машины", "Каждая строка — машина, каждый столбец — уровень напряжения: LV 32 В … UV 524288 В. Цвет пола под столбцом — цвет уровня");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (LvMachineType t : LvMachineType.values()) ids.add(t.id);
        List<Row> rows = new ArrayList<>();
        for (String id : ids) rows.add(tierRow(id, null));
        int x = tierTable(0, 4, rows, 20);
        // пар
        List<Row> steam = new ArrayList<>();
        List<Block> bronze = new ArrayList<>(), steel = new ArrayList<>();
        for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
            if (!key.getNamespace().equals(IIS.MODID) || !key.getPath().startsWith("steam_")) continue;
            if (key.getPath().endsWith("_bronze")) bronze.add(BuiltInRegistries.BLOCK.getValue(key));
        }
        bronze.sort(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).getPath()));
        for (Block b : bronze) {
            String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
            Block s = block(p.replace("_bronze", "_steel"));
            steam.add(new Row(rowName(b), Arrays.asList(b, s)));
        }
        labelAt(x + 3, 3.4, 0, "Эпоха пара", "gold", 1.6);
        x = table(x + 3, 4, List.of("Бронза", "Сталь"), List.of("gold", "gray"), new Block[]{Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA}, steam, 20, 2);
        List<Block> boilers = new ArrayList<>();
        IISMachines.BOILERS.values().forEach(d -> boilers.add(d.get()));
        boilers.add(IISMachines.BRICKED_BLAST_FURNACE.get());
        gallery(x + 3, 6, boilers, 2, 3, 3);
        endHall(Blocks.LIGHT_GRAY_CONCRETE);
    }

    // --- 3. Энергия ---------------------------------------------------------------------------------------------------
    private void energyHall() {
        beginHall("Энергия", "Генераторы, трансформаторы, буферы, зарядники, хранилища и панели");
        List<Row> gens = new ArrayList<>();
        for (String[] g : new String[][]{{"steam_turbine", "Паровая турбина"}, {"diesel_generator", "Дизельный генератор"}, {"gas_turbine", "Газовая турбина"}, {"naquadah_reactor", "Наквадовый реактор"}, {"plasma_generator", "Плазменный генератор"}}) gens.add(tierRow(g[0], g[1]));
        List<Row> rows = new ArrayList<>(gens);
        rows.add(tierRow("transformer", "Трансформатор"));
        rows.add(tierRow("adjustable_transformer", "Настраиваемый трансформатор"));
        for (int k = 1; k <= 4; k++) rows.add(tierRow("battery_buffer_" + k + "x" + k, "Буфер " + k + "×" + k));
        rows.add(tierRow("battery_charger", "Зарядник"));
        int x = tierTable(0, 4, rows, 16);
        // панели и хранилища
        List<Block> others = new ArrayList<>();
        others.add(ReactorContent.SOLAR_PANEL_BLOCK.get());
        for (String id : List.of("batbox", "mfe", "mfsu", "cesu", "energy_storage_mv", "energy_storage_ev")) if (block(id) != null) others.add(block(id));
        GearContent.BLOCKS.getEntries().forEach(h -> {
            if (!others.contains(h.get())) others.add(h.get());
        });
        for (int k = 1; k <= 4; k++) if (block("battery_buffer_" + k + "x" + k + "_max") != null) others.add(block("battery_buffer_" + k + "x" + k + "_max"));
        others.add(LvContent.TRANSFORMER_ULV.get());
        gallery(x + 3, 6, others, 4, 3, 4);
        int z = 6 + 4 * ((others.size() + 3) / 4);
        labelAt(x + 8, 3.0, z - 1, "Солнечные панели: покрытия на любую машину", "yellow", 1.0);
        items(x + 3, z + 1, ReactorContent.SOLAR_PANELS.stream().map(d -> BuiltInRegistries.ITEM.getKey(d.get()).getPath()).toList(), 5);
        endHall(Blocks.YELLOW_TERRACOTTA);
    }

    // --- 4. Автоматика ------------------------------------------------------------------------------------------------
    private void automationHall() {
        beginHall("Автоматика и хранение", "Буферы, фильтры, регуляторы, распределители, хранилища, буры и помпы");
        List<Row> rows = new ArrayList<>();
        for (String[] g : new String[][]{{"chest_buffer", "Сундук-буфер"}, {"super_buffer", "Сверхбуфер"}, {"item_filter", "Фильтр предметов"}, {"type_filter", "Фильтр по типу"}, {"regulator", "Регулятор"}, {"item_distributor", "Распределитель"},
                {"monster_repellent", "Отпугиватель"}, {"quantum_chest", "Квантовый сундук"}, {"quantum_tank", "Квантовый бак"}, {"miner", "Автобур"}, {"pump", "Помпа"}}) rows.add(tierRow(g[0], g[1]));
        int x = tierTable(0, 4, rows, 16);
        List<Block> tanks = new ArrayList<>();
        FluidContainers.TANKS.values().forEach(d -> tanks.add(d.get()));
        gallery(x + 3, 6, tanks, 3, 3, 3);
        endHall(Blocks.CYAN_TERRACOTTA);
    }

    // --- 5. Провода ---------------------------------------------------------------------------------------------------
    private void wiresHall() {
        beginHall("Провода и кабели", "Строка — материал, столбец — сечение. Кабель в изоляции не бьёт током, у него меньше потери");
        LinkedHashSet<String> mats = new LinkedHashSet<>();
        for (var s : ru.iis.lv.CableSpecs.ALL) if (s.id().endsWith("_1x")) mats.add(s.id().replaceFirst("^(wire|cable)_", "").replace("_1x", ""));
        String[] sizes = {"1x", "2x", "4x", "8x", "12x", "16x"};
        List<Row> wires = new ArrayList<>(), cables = new ArrayList<>();
        for (String m : mats) {
            List<Block> w = new ArrayList<>(), c = new ArrayList<>();
            for (String s : sizes) {
                w.add(block("wire_" + m + "_" + s));
                c.add(block("cable_" + m + "_" + s));
            }
            String lab = rowName(w.get(0) != null ? w.get(0) : c.get(0));
            wires.add(new Row(lab, w));
            cables.add(new Row(lab, c));
        }
        labelAt(8, 3.4, 0, "Провода", "gold", 1.6);
        int x = table(0, 4, List.of(sizes), null, null, wires, 24, 2);
        labelAt(x + 10, 3.4, 0, "Кабели", "gold", 1.6);
        table(x + 3, 4, List.of(sizes), null, null, cables, 24, 2);
        endHall(Blocks.BROWN_TERRACOTTA);
    }

    // --- 6. Трубы -----------------------------------------------------------------------------------------------------
    private void pipesHall() {
        beginHall("Трубы", "Жидкостные и предметные. Предметы идут сразу к ближайшему инвентарю, ограничительная труба — последней");
        String[] sizes = {"tiny", "small", "medium", "large", "huge"};
        List<Row> fluids = new ArrayList<>();
        for (String m : List.of("wood", "copper", "bronze", "steel", "stainless_steel", "titanium", "tungsten_steel", "plastic", "polytetrafluoroethylene", "high_pressure")) {
            List<Block> c = new ArrayList<>();
            Block first = null;
            for (String s : sizes) {
                Block b = block("pipe_" + s + "_" + m);
                c.add(b);
                if (first == null) first = b;
            }
            fluids.add(new Row(first == null ? m : rowName(first), c));
        }
        labelAt(6, 3.4, 0, "Жидкостные трубы", "aqua", 1.6);
        int x = table(0, 4, List.of("Крошечная", "Малая", "Обычная", "Большая", "Огромная"), null, null, fluids, 24, 3);
        List<Row> items = new ArrayList<>();
        for (Object[] m : ItemPipeBlock.MATERIALS) {
            List<Block> c = new ArrayList<>();
            for (String s : List.of("medium", "large", "huge")) c.add(block("item_pipe_" + s + "_" + m[0]));
            for (String s : List.of("medium", "large", "huge")) c.add(block("item_pipe_restrictive_" + s + "_" + m[0]));
            items.add(new Row(rowName(c.get(0)), c));
        }
        labelAt(x + 12, 3.4, 0, "Предметные трубы", "green", 1.6);
        x = table(x + 3, 4, List.of("Обычная", "Большая", "Огромная", "Огранич.", "Огр. большая", "Огр. огромная"), null, null, items, 24, 3);
        // капсулы: что вообще течёт
        int wz = 30;
        for (int xx = 0; xx <= 29; xx++) for (int dy = 0; dy < 5; dy++) set(at(xx, dy, wz), Blocks.WHITE_CONCRETE.defaultBlockState());
        ItemStack empty = new ItemStack(FluidContainers.CAPSULE.get());
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(new ItemStack(FluidContainers.CAPSULE.get()));
        stacks.add(new ItemStack(FluidContainers.UNIVERSAL_CAPSULE.get()));
        stacks.add(CapsuleItem.withContent(empty, FluidContent.of(Fluids.WATER, 1000)));
        stacks.add(CapsuleItem.withContent(empty, FluidContent.of(Fluids.LAVA, 1000)));
        for (GtFluids.Def d : GtFluids.all().values()) {
            if (stacks.size() >= 29 * 4) break;
            stacks.add(CapsuleItem.withContent(empty, FluidContent.of(d.source().get(), 1000)));
        }
        for (int i = 0; i < stacks.size(); i++) frame(at(i % 29, 4 - i / 29, wz), stacks.get(i));
        labelAt(14, 6.0, wz - 1, "Жидкости в капсулах: наведите на рамку", "aqua", 1.0);
        endHall(Blocks.LIGHT_BLUE_TERRACOTTA);
    }

    // --- 7. Люки и корпуса --------------------------------------------------------------------------------------------
    private void hatchesHall() {
        beginHall("Люки, корпуса и катушки", "Из них собираются многоблочные машины: шины, люки жидкостей и энергии, глушители");
        String[] tiers = {"ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "max"};
        String[] names = {"ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "MAX"};
        Block[] floor = new Block[10];
        floor[0] = Blocks.GRAY_CONCRETE;
        System.arraycopy(TIER_FLOOR, 0, floor, 1, 9);
        List<String> cols = new ArrayList<>(List.of("dark_gray"));
        cols.addAll(List.of(TEXT_COLOR));
        List<Row> rows = new ArrayList<>();
        for (String[] g : new String[][]{{"hull", "Корпус машины"}, {"casing", "Обшивка"}, {"hatch_energy", "Вход энергии"}, {"hatch_dynamo", "Выход энергии"}, {"hatch_input_bus", "Входная шина"}, {"hatch_output_bus", "Выходная шина"},
                {"hatch_input", "Входной люк"}, {"hatch_output", "Выходной люк"}, {"hatch_muffler", "Глушитель"}}) {
            List<Block> c = new ArrayList<>();
            for (String t : tiers) c.add(block(g[0] + "_" + t));
            rows.add(new Row(g[1], c));
        }
        int x = table(0, 4, List.of(names), cols, floor, rows, 12, 2);
        List<Block> rest = new ArrayList<>();
        for (Identifier key : new TreeSet<>(BuiltInRegistries.BLOCK.keySet())) {
            if (!key.getNamespace().equals(IIS.MODID)) continue;
            String p = key.getPath();
            if (p.startsWith("coil_") || p.startsWith("frame_") || p.endsWith("_casing") || p.startsWith("casing_fusion") || p.startsWith("turbine_casing") || p.equals("hatch_maintenance") || p.startsWith("hatch_maintenance_") || p.startsWith("hatch_data_access") || p.equals("fusion_coil")) rest.add(BuiltInRegistries.BLOCK.getValue(key));
        }
        gallery(x + 3, 6, rest, 8, 3, 3);
        endHall(Blocks.WHITE_TERRACOTTA);
    }

    // --- 8. Многоблочные ----------------------------------------------------------------------------------------------
    private int tx, tz;

    /** Координаты из тестовых построек: там x 4, y 1, z 2 — середина основания. */
    private BlockPos t(int x, int y, int z) {
        return new BlockPos(ox - tx + x - 4, y0 + y - 1, oz + tz + z - 2);
    }

    private void controller(BlockPos p, Block b, String title, String about) {
        set(p, b.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(p) instanceof MultiblockControllerBlockEntity e) e.maintenance().setMask(63);
        controllers.add(p);
        label(p.getX() + 0.5, y0 + 5.2, p.getZ() - 1.5, title + "\n" + about, "white", 0.7, false);
    }

    private void box(BlockPos c, int minDx, int maxDx, int minDy, int maxDy, int minDz, int maxDz, Block b) {
        for (int dx = minDx; dx <= maxDx; dx++) for (int dy = minDy; dy <= maxDy; dy++) for (int dz = minDz; dz <= maxDz; dz++) set(c.offset(dx, dy, dz), b.defaultBlockState());
    }

    private void multisHall() {
        beginHall("Многоблочные машины", "Собраны как в игре: структура замкнута, контроллер показывает, что форма верна");
        int col = 11, row = 12;
        // печи и горн
        tx = 0;
        tz = 2;
        {
            BlockPos center = t(4, 1, 2);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = center.offset(dx, 0, dz);
                set(c, MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
                if (dx != 0 || dz != 0) {
                    set(c.above(1), MultiContent.COIL_NAQUADAH_ALLOY.get().defaultBlockState());
                    set(c.above(2), MultiContent.COIL_NAQUADAH_ALLOY.get().defaultBlockState());
                }
                set(c.above(3), MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
            }
            set(center.above(3), hatch(HatchType.MUFFLER, Direction.UP));
            set(center, hatch(HatchType.ENERGY_ZPM, Direction.DOWN));
            set(center.east(), hatch(HatchType.INPUT_BUS, Direction.EAST));
            set(center.south(), hatch(HatchType.OUTPUT_BUS, Direction.SOUTH));
            set(center.south().east(), hatch(HatchType.MAINTENANCE, Direction.EAST));
            controller(center.north(), MultiContent.EBF.get(), "Электрическая печь", "Катушка из сплава наквады: 9001 K");
        }
        tx = col;
        {
            BlockPos c0 = t(4, 1, 2);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos b = c0.offset(dx, 0, dz);
                set(b, MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
                if (dx != 0 || dz != 0) set(b.above(), MultiContent.COIL_HSSG.get().defaultBlockState());
                set(b.above(2), MultiContent.HEAT_PROOF_CASING.get().defaultBlockState());
            }
            set(c0, hatch(HatchType.ENERGY, Direction.DOWN));
            set(c0.west(), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c0.east(), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c0.south(), hatch(HatchType.MAINTENANCE, Direction.SOUTH));
            set(c0.above(2), hatch(HatchType.MUFFLER, Direction.UP));
            controller(c0.north(), MultiContent.MULTI_SMELTER.get(), "Мультиплавильня", "Плавит пачками, катушка задаёт размер пачки");
        }
        tx = col * 2;
        {
            BlockPos c = t(4, 2, 2);
            box(c, -1, 1, -1, 1, -1, 1, MultiContent.SOLID_STEEL_CASING.get());
            set(c, Blocks.AIR.defaultBlockState());
            set(c.below(), hatch(HatchType.ENERGY, Direction.DOWN));
            set(c.west(), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c.east(), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c.above(), hatch(HatchType.MAINTENANCE, Direction.UP));
            set(c.south(), hatch(HatchType.MUFFLER, Direction.SOUTH));
            controller(c.north(), MultiContent.IMPLOSION_COMPRESSOR.get(), "Имплозионный компрессор", "Берёт ТНТ, динамит или промышленный ТНТ");
        }
        tx = col * 3;
        {
            BlockPos c = t(4, 2, 2);
            box(c, -1, 1, -1, 1, -1, 1, MultiContent.FROST_PROOF_CASING.get());
            set(c, Blocks.AIR.defaultBlockState());
            set(c.below(), hatch(HatchType.ENERGY, Direction.DOWN));
            set(c.west(), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c.east(), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c.above(), hatch(HatchType.MAINTENANCE, Direction.UP));
            controller(c.north(), MultiContent.VACUUM_FREEZER.get(), "Вакуумный морозильник", "Остужает горячие слитки");
        }
        tx = col * 4;
        {
            BlockPos c = t(4, 2, 2);
            box(c, -1, 1, -1, 1, -1, 1, MultiContent.CHEMICALLY_INERT_CASING.get());
            set(c, MultiContent.PTFE_PIPE_CASING.get().defaultBlockState());
            set(c.above(), MultiContent.COIL_CUPRONICKEL.get().defaultBlockState());
            set(c.below(), hatch(HatchType.ENERGY_MV, Direction.DOWN));
            set(c.west(), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c.east(), hatch(HatchType.OUTPUT_HATCH, Direction.EAST));
            set(c.south(), hatch(HatchType.MAINTENANCE, Direction.SOUTH));
            controller(c.north(), MultiContent.LARGE_CHEMICAL_REACTOR.get(), "Большой химреактор", "Рецепты без потери времени");
        }
        // второй ряд
        tz = 2 + row;
        tx = 0;
        {
            BlockPos c = t(4, 1, 2);
            Block casing = MultiContent.CLEAN_STAINLESS_CASING.get();
            for (int y = 0; y < 3; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(c.offset(dx, y, dz), (y == 1 && dx == 0 && dz == 0 ? Blocks.AIR : casing).defaultBlockState());
            set(c.west(), hatch(HatchType.INPUT_HATCH, Direction.WEST));
            set(c.east(), hatch(HatchType.ENERGY_MV, Direction.EAST));
            set(c.south(), hatch(HatchType.MAINTENANCE, Direction.SOUTH));
            set(c.above().west(), hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
            controller(c.north(), MultiContent.DISTILLATION_TOWER.get(), "Ректификационная колонна", "Каждый этаж — своя фракция");
        }
        tx = col;
        {
            BlockPos ctrl = t(4, 2, 1);
            Direction back = Direction.SOUTH, side = Direction.WEST;
            for (int depth = 0; depth < 3; depth++) for (int y = -1; y <= 1; y++) for (int w = -2; w <= 2; w++) {
                BlockPos p = ctrl.relative(back, depth).above(y).relative(side, w);
                if (depth == 1 && y == 0 && Math.abs(w) <= 1) continue;
                set(p, (Math.abs(w) == 1 ? MultiContent.COIL_CUPRONICKEL.get() : MultiContent.CLEAN_STAINLESS_CASING.get()).defaultBlockState());
            }
            set(ctrl.relative(back).relative(side, -2), hatch(HatchType.INPUT_HATCH, Direction.EAST));
            set(ctrl.relative(back).relative(side, 2), hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
            set(ctrl.relative(back, 2), hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
            set(ctrl.relative(back).below(), hatch(HatchType.ENERGY_MV, Direction.DOWN));
            set(ctrl.relative(back).above(), hatch(HatchType.MAINTENANCE, Direction.UP));
            controller(ctrl, MultiContent.OIL_CRACKER.get(), "Установка крекинга", "Водород или пар расщепляют топливо");
        }
        tx = col * 2;
        turbine(MultiContent.LARGE_STEAM_TURBINE.get(), MultiContent.TURBINE_CASING.get(), HatchType.DYNAMO_MV, "Большая паровая турбина", "Ротор — в контроллере", false);
        tx = col * 3;
        turbine(MultiContent.LARGE_GAS_TURBINE.get(), MultiContent.TURBINE_CASING_STAINLESS.get(), HatchType.DYNAMO_MV, "Большая газовая турбина", "Жжёт газ, кислород разгоняет", true);
        tx = col * 4;
        turbine(MultiContent.LARGE_PLASMA_TURBINE.get(), MultiContent.TURBINE_CASING_TUNGSTENSTEEL.get(), HatchType.DYNAMO_LUV, "Большой плазменный генератор", "Плазма термояда, остывшее в выходной люк", false);
        // третий ряд
        tz = 2 + row * 2;
        tx = 0;
        {
            for (int x = 3; x <= 5; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 3; z++) set(t(x, y, z), MultiContent.STABLE_TITANIUM_CASING.get().defaultBlockState());
            set(t(4, 1, 2), hatch(HatchType.INPUT_HATCH, Direction.DOWN));
            set(t(4, 2, 2), MultiContent.TITANIUM_PIPE_CASING.get().defaultBlockState());
            set(t(4, 3, 2), MultiContent.TITANIUM_PIPE_CASING.get().defaultBlockState());
            set(t(4, 4, 2), hatch(HatchType.OUTPUT_HATCH, Direction.UP));
            set(t(3, 1, 2), hatch(HatchType.INPUT_HATCH, Direction.WEST));
            set(t(5, 1, 2), hatch(HatchType.OUTPUT_HATCH, Direction.EAST));
            set(t(3, 2, 1), hatch(HatchType.MAINTENANCE, Direction.NORTH));
            controller(t(4, 1, 1), MultiContent.HEAT_EXCHANGER.get(), "Теплообменник", "Лава или теплоноситель: перегретый пар");
        }
        tx = col;
        {
            BlockPos c = t(4, 2, 2);
            box(c, -1, 1, -1, 1, -1, 1, MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get());
            set(c, Blocks.AIR.defaultBlockState());
            set(c.below(), hatch(HatchType.ENERGY, Direction.DOWN));
            set(c.west(), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c.east(), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c.above(), hatch(HatchType.MAINTENANCE, Direction.UP));
            controller(c.north(), MultiContent.PROCESSING_ARRAY.get(), "Обрабатывающий массив", "До 16 одноблочных машин внутри");
        }
        tx = col * 2 + 1;
        {
            BlockPos ctrl = t(4, 2, 2), c = ctrl.south(2).above();
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                boolean inner = Math.abs(dx) < 2 && Math.abs(dy) < 2 && Math.abs(dz) < 2;
                set(c.offset(dx, dy, dz), (inner ? Blocks.AIR : MultiContent.ROBUST_HSSG_CASING.get()).defaultBlockState());
            }
            set(c.below(2), hatch(HatchType.ENERGY, Direction.DOWN));
            set(c.west(2), hatch(HatchType.INPUT_BUS, Direction.WEST));
            set(c.east(2), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c.above(2), hatch(HatchType.MAINTENANCE, Direction.UP));
            controller(ctrl, MultiContent.ADVANCED_PROCESSING_ARRAY.get(), "Улучшенный массив", "Куб 5×5×5, до 64 машин");
        }
        // буровые: все уровни
        tz = 2 + row * 3;
        Object[][] drills = {{MultiContent.ORE_DRILLS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), "steel", HatchType.ENERGY_MV, "Рудная буровая I", "Радиус 48 блоков"},
                {MultiContent.ORE_DRILLS.get(1).get(), MultiContent.STABLE_TITANIUM_CASING.get(), "titanium", HatchType.ENERGY_HV, "Рудная буровая II", "Радиус 64 блока"},
                {MultiContent.ORE_DRILLS.get(2).get(), MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get(), "tungsten_steel", HatchType.ENERGY_EV, "Рудная буровая III", "Радиус 96 блоков"},
                {MultiContent.ORE_DRILLS.get(3).get(), MultiContent.MINING_OSMIRIDIUM_CASING.get(), "osmiridium", HatchType.ENERGY_IV, "Рудная буровая IV", "Радиус 144 блока"}};
        for (int i = 0; i < drills.length; i++) {
            tx = col * i;
            BlockPos ctrlP = t(4, 1, 1), column = ctrlP.south();
            Block casing = (Block) drills[i][1], fr = MultiContent.FRAMES.get(drills[i][2]).get();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(column.offset(dx, 0, dz), casing.defaultBlockState());
            for (int y = 1; y < 4; y++) {
                set(column.above(y), casing.defaultBlockState());
                set(column.above(y + 3), fr.defaultBlockState());
                for (Direction d : Direction.Plane.HORIZONTAL) set(column.above(y).relative(d), fr.defaultBlockState());
            }
            set(column.west(), hatch((HatchType) drills[i][3], Direction.WEST));
            set(column.east(), hatch(HatchType.MAINTENANCE, Direction.EAST));
            set(column.south().west(), hatch(HatchType.INPUT_HATCH, Direction.WEST));
            set(column.south().east(), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            controller(ctrlP, (Block) drills[i][0], (String) drills[i][4], (String) drills[i][5]);
        }
        // нефть и турбина высокого давления
        Object[][] oil = {{MultiContent.OIL_DRILLS.get(0).get(), MultiContent.SOLID_STEEL_CASING.get(), "steel", HatchType.ENERGY_MV, "Нефтяная буровая I", "Качает нефть поля под чанком"},
                {MultiContent.OIL_DRILLS.get(1).get(), MultiContent.STABLE_TITANIUM_CASING.get(), "titanium", HatchType.ENERGY_HV, "Нефтяная буровая II", "Поле шире"},
                {MultiContent.OIL_DRILLS.get(2).get(), MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get(), "tungsten_steel", HatchType.ENERGY_EV, "Нефтяная буровая III", "Самый широкий охват"}};
        tz = 2 + row * 4;
        for (int i = 0; i < oil.length; i++) {
            tx = col * i;
            BlockPos ctrlP = t(4, 1, 1), column = ctrlP.south();
            Block casing = (Block) oil[i][1], fr = MultiContent.FRAMES.get(oil[i][2]).get();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(column.offset(dx, 0, dz), casing.defaultBlockState());
            for (int y = 1; y < 4; y++) {
                set(column.above(y), casing.defaultBlockState());
                set(column.above(y + 3), fr.defaultBlockState());
                for (Direction d : Direction.Plane.HORIZONTAL) set(column.above(y).relative(d), fr.defaultBlockState());
            }
            set(column.west(), hatch((HatchType) oil[i][3], Direction.WEST));
            set(column.east(), hatch(HatchType.MAINTENANCE, Direction.EAST));
            set(column.south().east(), hatch(HatchType.OUTPUT_HATCH, Direction.EAST));
            controller(ctrlP, (Block) oil[i][0], (String) oil[i][4], (String) oil[i][5]);
        }
        tx = col * 3;
        {
            for (int x = 3; x <= 5; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++) set(t(x, y, z), MultiContent.TURBINE_CASING_TITANIUM.get().defaultBlockState());
            set(t(4, 2, 2), Blocks.AIR.defaultBlockState());
            set(t(4, 2, 3), Blocks.AIR.defaultBlockState());
            set(t(4, 3, 2), hatch(HatchType.INPUT_HATCH, Direction.UP));
            set(t(3, 2, 2), hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
            set(t(4, 1, 3), hatch(HatchType.MAINTENANCE, Direction.DOWN));
            set(t(4, 2, 4), hatch(HatchType.DYNAMO_MV, Direction.SOUTH));
            controller(t(4, 2, 1), MultiContent.LARGE_HP_TURBINE.get(), "Турбина высокого давления", "Перегретый пар в обычный пар");
        }
        // котлы: все уровни
        tz = 2 + row * 5;
        ru.iis.multi.logic.LargeBoilerLogic.Tier[] tiers = ru.iis.multi.logic.LargeBoilerLogic.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            tx = col * i;
            LargeBoilerBlockEntity.Casings c = MultiContent.boilerCasings(tiers[i]);
            BlockPos ctrlP = t(4, 1, 2), center = ctrlP.south();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos colB = center.offset(dx, 0, dz);
                set(colB, c.firebox().defaultBlockState());
                for (int k = 1; k <= 4; k++) set(colB.above(k), (dx == 0 && dz == 0 && k < 4 ? c.pipe() : c.casing()).defaultBlockState());
            }
            set(t(3, 1, 2), hatch(HatchType.INPUT_BUS, Direction.NORTH));
            set(t(5, 1, 2), hatch(HatchType.INPUT_HATCH, Direction.NORTH));
            set(t(4, 1, 4), hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
            set(t(3, 1, 4), hatch(HatchType.MAINTENANCE, Direction.SOUTH));
            set(t(5, 1, 4), hatch(HatchType.MUFFLER, Direction.EAST));
            set(t(4, 2, 2), hatch(HatchType.OUTPUT_HATCH, Direction.NORTH));
            controller(ctrlP, MultiContent.LARGE_BOILERS.get(tiers[i]).get(), "Большой котёл", "Твёрдое или жидкое топливо, вода, пар");
        }
        // кокс, пиролиз, чистая комната, сборочная линия
        tz = 2 + row * 6;
        tx = 0;
        {
            BlockPos center = t(4, 2, 2);
            box(center, -1, 1, -1, 1, -1, 1, LvContent.COKE_OVEN_BRICKS.get());
            set(center, Blocks.AIR.defaultBlockState());
            controller(center.north(), LvContent.COKE_OVEN.get(), "Коксовая печь", "Без энергии: уголь в кокс и креозот");
        }
        tx = col;
        {
            BlockPos c = t(4, 1, 3);
            Block coil = MultiContent.COIL_TUNGSTENSTEEL.get(), casing = MultiContent.CASING_ULV.get();
            for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) for (int y = 0; y < 4; y++) {
                boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2;
                BlockPos q = c.offset(i, y, j);
                set(q, (inner && y == 0 ? coil : inner && y < 3 ? Blocks.AIR : casing).defaultBlockState());
            }
            set(c.offset(-2, 0, 0), hatch(HatchType.ENERGY_HV, Direction.WEST));
            set(c.offset(2, 0, 0), hatch(HatchType.OUTPUT_BUS, Direction.EAST));
            set(c.offset(0, 0, 2), hatch(HatchType.OUTPUT_HATCH, Direction.SOUTH));
            set(c.offset(-2, 0, 2), hatch(HatchType.MAINTENANCE, Direction.SOUTH));
            set(c.offset(0, 3, 0), hatch(HatchType.INPUT_BUS, Direction.UP));
            set(c.offset(-1, 3, 0), hatch(HatchType.MUFFLER, Direction.UP));
            controller(c.offset(0, 0, -2), MultiContent.PYROLYSE_OVEN.get(), "Пиролизная печь", "Древесина в уголь и органику");
        }
        tx = col * 2 + 2;
        {
            Block plascrete = MultiContent.PLASCRETE.get();
            for (int x = 3; x <= 5; x++) for (int y = 1; y <= 4; y++) for (int z = 3; z <= 5; z++) set(t(x, y, z), plascrete.defaultBlockState());
            BlockPos ctrl = t(4, 4, 4);
            set(ctrl, MultiContent.CLEANROOM.get().defaultBlockState());
            set(t(4, 3, 4), Blocks.AIR.defaultBlockState());
            set(t(3, 2, 4), hatch(HatchType.MAINTENANCE, Direction.WEST));
            set(t(5, 2, 4), hatch(HatchType.ENERGY_EV, Direction.EAST));
            BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH);
            set(t(4, 2, 3), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            set(t(4, 3, 3), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            if (level.getBlockEntity(ctrl) instanceof MultiblockControllerBlockEntity e) e.maintenance().setMask(63);
            controllers.add(ctrl);
            label(ctrl.getX() + 0.5, y0 + 5.2, ctrl.getZ() - 2.5, "Чистая комната\nПока она работает, внутри доступны чистые рецепты", "white", 0.7, false);
        }
        tx = col * 3 + 2;
        {
            for (int x = 4; x <= 8; x++) {
                for (int z = 2; z <= 4; z++) set(t(x, 1, z), MultiContent.SOLID_STEEL_CASING.get().defaultBlockState());
                set(t(x, 2, 2), MultiContent.REINFORCED_GLASS.get().defaultBlockState());
                set(t(x, 2, 3), MultiContent.GEAR_BOX_TUNGSTEN_STEEL.get().defaultBlockState());
                set(t(x, 2, 4), MultiContent.REINFORCED_GLASS.get().defaultBlockState());
                set(t(x, 3, 2), MultiContent.GRATE_CASING.get().defaultBlockState());
                set(t(x, 3, 3), MultiContent.ASSEMBLER_CASING.get().defaultBlockState());
                set(t(x, 3, 4), MultiContent.GRATE_CASING.get().defaultBlockState());
                set(t(x, 4, 3), MultiContent.SOLID_STEEL_CASING.get().defaultBlockState());
            }
            set(t(6, 4, 3), hatch(HatchType.ENERGY_LUV, Direction.UP));
            for (int[] b : new int[][]{{8, 2}, {8, 3}, {8, 4}, {7, 2}, {7, 3}, {7, 4}, {6, 2}}) set(t(b[0], 1, b[1]), hatch(HatchType.INPUT_BUS, Direction.DOWN));
            set(t(6, 1, 3), hatch(HatchType.INPUT_HATCH, Direction.DOWN));
            set(t(6, 1, 4), hatch(HatchType.INPUT_HATCH, Direction.DOWN));
            set(t(5, 1, 2), hatch(HatchType.MAINTENANCE, Direction.DOWN));
            set(t(5, 1, 3), hatch(HatchType.OUTPUT_BUS, Direction.DOWN));
            controller(t(6, 3, 2), MultiContent.ASSEMBLY_LINE.get(), "Сборочная линия", "Рецепты с данных: ингредиенты строго по порядку");
        }
        endHall(Blocks.SMOOTH_STONE);
    }

    private void turbine(Block ctrlBlock, Block casing, HatchType dynamo, String title, String about, boolean muffler) {
        for (int x = 3; x <= 5; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++) set(t(x, y, z), casing.defaultBlockState());
        set(t(4, 2, 2), Blocks.AIR.defaultBlockState());
        set(t(4, 2, 3), Blocks.AIR.defaultBlockState());
        set(t(4, 3, 2), hatch(HatchType.INPUT_HATCH, Direction.UP));
        if (muffler) set(t(3, 2, 2), hatch(HatchType.MUFFLER, Direction.WEST));
        set(t(4, 1, 3), hatch(HatchType.MAINTENANCE, Direction.DOWN));
        set(t(4, 2, 4), hatch(dynamo, Direction.SOUTH));
        controller(t(4, 2, 1), ctrlBlock, title, about);
        if (level.getBlockEntity(t(4, 2, 1)) instanceof LargeTurbineBlockEntity e) e.setItem(0, MultiContent.ROTORS.get(ru.iis.multi.logic.LargeTurbineLogic.Size.SMALL).get().create("steel"));
    }

    // --- 9. Термояд и реактор -----------------------------------------------------------------------------------------
    private void fusionRing(int cxLocal, int czLocal, Block casing, Block coil, Block ctrlBlock, HatchType energy, HatchType in, HatchType out, String title, String about) {
        BlockPos center = at(cxLocal, 1, czLocal);
        for (int[] d : FusionShape.COILS) set(center.offset(d[0], d[1], d[2]), coil.defaultBlockState());
        for (int[] d : FusionShape.CASINGS) set(center.offset(d[0], d[1], d[2]), casing.defaultBlockState());
        for (int[][] cells : new int[][][]{FusionShape.ENERGY, FusionShape.OUTPUT, FusionShape.INPUT}) for (int[] d : cells) set(center.offset(d[0], d[1], d[2]), casing.defaultBlockState());
        for (int i = 0; i < 4; i++) {
            int[] d = FusionShape.ENERGY[i * 3];
            set(center.offset(d[0], d[1], d[2]), hatch(energy, Direction.UP));
        }
        int[] o = FusionShape.OUTPUT[0];
        set(center.offset(o[0], o[1], o[2]), hatch(out, Direction.UP));
        for (int i = 0; i < 2; i++) {
            int[] d = FusionShape.INPUT[i];
            set(center.offset(d[0], d[1], d[2]), hatch(in, Direction.UP));
        }
        BlockPos ctrl = center.east(FusionReactorBlockEntity.RING);
        set(ctrl, ctrlBlock.defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        controllers.add(ctrl);
        label(center.getX() + 0.5, y0 + 4.5, center.getZ() + 0.5, title + "\n" + about, "light_purple", 1.0, true);
    }

    private void bigHall() {
        beginHall("Термояд и ядерный реактор", "Кольца собраны по настоящей форме. Реактор стоит без сигнала: подайте красный камень сами и следите за теплом");
        fusionRing(9, 12, LvContent.CASING_LUV.get(), MultiContent.COIL_SUPERCONDUCTOR.get(), MultiContent.FUSION_COMPUTER_LUV.get(), HatchType.ENERGY_LUV, HatchType.INPUT_HATCH_LUV, HatchType.OUTPUT_HATCH_LUV,
                "Термояд Mark I", "Запас 160 млн EU, люки от LuV");
        fusionRing(30, 12, MultiContent.CASING_FUSION.get(), MultiContent.FUSION_COIL.get(), MultiContent.FUSION_COMPUTER_ZPM.get(), HatchType.ENERGY_ZPM, HatchType.INPUT_HATCH_ZPM, HatchType.OUTPUT_HATCH_ZPM,
                "Термояд Mark II", "Запас 320 млн EU, люки от ZPM, наквада");
        fusionRing(51, 12, MultiContent.CASING_FUSION_2.get(), MultiContent.FUSION_COIL.get(), MultiContent.FUSION_COMPUTER_UV.get(), HatchType.ENERGY_UV, HatchType.INPUT_HATCH_UV, HatchType.OUTPUT_HATCH_UV,
                "Термояд Mark III", "Запас 640 млн EU, люки UV, нейтроний");
        // реактор: шесть камер и безопасная сборка внутри; без сигнала молчит
        int rx = 66, rz = 8;
        BlockPos core = at(rx, 1, rz);
        set(core, ReactorContent.NUCLEAR_REACTOR.get().defaultBlockState());
        for (Direction d : Direction.values()) set(core.relative(d), ReactorContent.REACTOR_CHAMBER.get().defaultBlockState());
        NuclearReactorBlockEntity r = be(core, NuclearReactorBlockEntity.class);
        if (r != null) {
            String[] layout = {"reactor_vent", "rod_uranium_1", "reactor_vent", "reactor_heat_switch", "rod_uranium_2", "reactor_heat_switch", "reactor_vent", "rod_uranium_1", "reactor_vent", "reactor_plating", "coolant_30k", "reactor_plating"};
            for (int i = 0; i < layout.length && i < r.getContainerSize(); i++) r.setItem(i, stack(layout[i], 1));
        }
        labelAt(rx, 5.0, rz, "Ядерный реактор\nЯдро в окружении 6 камер: 54 ячейки. Внутри показана сборка. Без красного камня не работает", "green", 0.9);
        sign(at(rx, 0, rz - 3), "Откройте ядро");
        items(rx - 5, rz + 5, List.of("rod_uranium_1", "rod_uranium_2", "rod_uranium_4", "rod_mox_1", "rod_mox_2", "rod_mox_4", "rod_thorium_1", "rod_thorium_2", "rod_thorium_4", "rod_naquadah_1", "rod_naquadah_2", "rod_naquadah_4",
                "reactor_vent", "reactor_vent_core", "reactor_vent_gold", "reactor_vent_diamond", "reactor_vent_spread", "reactor_heat_switch", "reactor_heat_switch_core", "reactor_heat_switch_spread", "reactor_heat_switch_diamond",
                "coolant_10k", "coolant_30k", "coolant_60k", "reactor_condensator", "reactor_condensator_lap", "reactor_plating", "reactor_plating_heat", "reactor_plating_explosive", "reactor_reflector", "reactor_reflector_thick", "neutron_reflector"), 6);
        endHall(Blocks.PURPLE_TERRACOTTA);
    }

    // --- 10. Детали и схемы -------------------------------------------------------------------------------------------
    private void partsHall() {
        beginHall("Детали и схемы", "Рамки показывают предмет, таблички его называют");
        // стена деталей: столбцы — уровни, строки — детали
        int wall = 5;
        String[][] parts = {{"electric_motor", "Двигатель"}, {"electric_pump", "Помпа"}, {"conveyor_module", "Конвейер"}, {"electric_piston", "Поршень"}, {"robot_arm", "Манипулятор"}, {"emitter", "Излучатель"}, {"sensor", "Сенсор"}, {"field_generator", "Генератор поля"}};
        for (int x = 2; x <= 19; x++) for (int r = 0; r < parts.length; r++) set(at(x, r, wall), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int c = 0; c < 8; c++) {
            labelAt(4 + c * 2, parts.length + 0.3, wall - 1, TIER_NAMES[c], TEXT_COLOR[c], 1.0);
            for (int r = 0; r < parts.length; r++) {
                ItemStack s = stack(parts[r][0] + "_" + TIERS[c], 1);
                if (!s.isEmpty()) frame(at(4 + c * 2, parts.length - 1 - r, wall), s);
            }
        }
        for (int r = 0; r < parts.length; r++) labelAt(0, parts.length - 1 - r + 0.2, wall - 1, parts[r][1], "white", 0.8);
        // схемы по цепочкам
        int z = wall + 3;
        String[][] chains = {{"Вакуумные и первые", "vacuum_tube", "circuit_basic", "circuit_good", "circuit_advanced", "circuit_data", "circuit_data_control", "circuit_elite"},
                {"Интегральные", "circuit_microprocessor", "circuit_integrated", "circuit_processor", "circuit_processor_assembly", "circuit_workstation", "circuit_mainframe"},
                {"Нано", "circuit_nanoprocessor", "circuit_nanocomputer", "circuit_elitenanocomputer", "circuit_master"},
                {"Квантовые", "circuit_quantumprocessor", "circuit_quantumcomputer", "circuit_masterquantumcomputer", "circuit_quantummainframe"},
                {"Кристаллические", "circuit_crystalprocessor", "circuit_crystalcomputer", "circuit_ultimatecrystalcomputer", "circuit_crystalmainframe"},
                {"Биосхемы", "circuit_neuroprocessor", "circuit_wetwarecomputer", "circuit_wetwaresupercomputer", "circuit_wetwaremainframe"},
                {"Платы и чипы", "circuit_board_coated", "circuit_board_phenolic", "circuit_board_plastic", "circuit_board_epoxy", "circuit_board_fiberglass", "circuit_board_multifiberglass", "circuit_board_wetware",
                        "chip_ram", "chip_nand", "chip_nor", "chip_cpu", "chip_nano_cpu", "chip_quantum_cpu", "chip_crystal_cpu", "chip_neuro_cpu", "chip_hpic"},
                {"Данные и инструменты", "data_stick", "data_orb", "data_reader_mv", "data_reader_ev", "portable_scanner", "programmed_circuit", "quantum_star", "gravi_star"}};
        for (String[] chain : chains) {
            labelAt(10, 2.8, z, chain[0], "gold", 1.0);
            z = items(2, z + 2, Arrays.asList(chain).subList(1, chain.length), 9);
        }
        endHall(Blocks.WHITE_CONCRETE);
    }

    // --- 11. Снаряжение -----------------------------------------------------------------------------------------------
    private void stand(int x, int z, String title, String head, String chest, String legs, String feet) {
        BlockPos p = at(x, 0, z);
        track(p);
        String eq = (head == null ? "" : "head:{id:\"iis:" + head + "\",count:1},") + (chest == null ? "" : "chest:{id:\"iis:" + chest + "\",count:1},") + (legs == null ? "" : "legs:{id:\"iis:" + legs + "\",count:1},")
                + (feet == null ? "" : "feet:{id:\"iis:" + feet + "\",count:1},");
        if (eq.endsWith(",")) eq = eq.substring(0, eq.length() - 1);
        command(String.format(Locale.ROOT, "summon minecraft:armor_stand %d.5 %d %d.5 {Rotation:[180f,0f],NoGravity:1b,Invulnerable:1b,ShowArms:1b,NoBasePlate:0b,DisabledSlots:4144959,equipment:{%s}}", p.getX(), p.getY(), p.getZ(), eq));
        sign(p.north(), title);
    }

    private void gearHall() {
        beginHall("Снаряжение", "Броня на стойках, инструмент и батареи в рамках. Клавиша N включает ночное зрение");
        stand(2, 4, "Нано-броня", "nano_helmet", "nano_chestplate", "nano_leggings", "nano_boots");
        stand(6, 4, "Квантовая броня", "quantum_helmet", "quantum_chestplate", "quantum_leggings", "quantum_boots");
        stand(10, 4, "Защитный костюм", "hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "rubber_boots");
        stand(14, 4, "Реактивный ранец", "nightvision_goggles", "jetpack", null, null);
        stand(18, 4, "Лазуротроновый ранец", null, "lappack", null, null);
        stand(22, 4, "Энергетический ранец", null, "energy_pack", null, null);
        stand(26, 4, "Аккумуляторный ранец", null, "batpack", null, null);
        stand(30, 4, "Кислородное снаряжение", "oxygen_mask", "oxygen_gear", null, null);
        int z = items(2, 8, List.of("mining_laser", "nano_saber", "battery_re", "battery_adv", "energy_crystal", "lapotron_crystal", "energy_orb", "energy_orb_cluster", "ultimate_battery",
                "charging_battery_re", "charging_battery_adv", "charging_energy_crystal", "charging_lapotron_crystal", "duct_tape", "auto_repair_kit", "scrap", "stem_cell", "dynamite", "industrial_tnt", "gelled_toluene"), 10);
        List<String> credits = new ArrayList<>();
        for (String c : List.of("copper", "cupronickel", "silver", "gold", "platinum", "osmium", "naquadah", "neutronium")) credits.add("credit_" + c);
        labelAt(10, 2.8, z, "Жетоны GT: валюта квестов", "gold", 1.0);
        items(2, z + 2, credits, 8);
        endHall(Blocks.BLUE_TERRACOTTA);
    }

    // --- 12. Руды и металлы -------------------------------------------------------------------------------------------
    private void oresHall() {
        beginHall("Руды и металлы", "Руда каждого материала в каждой породе и блоки металлов. В обычном мире её ищут молотом и сейсморазведкой");
        List<Row> rows = new ArrayList<>();
        OreHost[] hosts = OreHost.values();
        List<String> cols = new ArrayList<>();
        for (OreHost h : hosts) cols.add(switch (h.name()) {
            case "STONE" -> "Камень";
            case "DEEPSLATE" -> "Сланец";
            case "NETHERRACK" -> "Незерак";
            case "END_STONE", "ENDSTONE" -> "Эндерняк";
            case "GRANITE" -> "Гранит";
            case "DIORITE" -> "Диорит";
            case "ANDESITE" -> "Андезит";
            case "TUFF" -> "Туф";
            case "MOON_ROCK", "MOON_STONE", "MOON" -> "Лунный камень";
            default -> h.name();
        });
        cols.add("Малая");
        List<String> mats = new ArrayList<>(OreBlocks.all().keySet());
        Collections.sort(mats);
        for (String m : mats) {
            List<Block> c = new ArrayList<>();
            var byHost = OreBlocks.all().get(m);
            Block first = null;
            for (OreHost h : hosts) {
                Block b = byHost.containsKey(h) ? byHost.get(h).get() : null;
                c.add(b);
                if (first == null) first = b;
            }
            var small = OreBlocks.allSmall().get(m);
            c.add(small != null && !small.isEmpty() ? small.values().iterator().next().get() : null);
            if (first == null) continue;
            rows.add(new Row(nameOf(first), c));
        }
        int x = table(0, 4, cols, null, null, rows, 24, 2);
        // оставшиеся малые руды и породы не теряются: все варианты считаются показанными
        OreBlocks.allSmall().values().forEach(m -> m.values().forEach(d -> shown.add(d.get())));
        List<Block> metal = new ArrayList<>();
        for (Identifier key : new TreeSet<>(BuiltInRegistries.BLOCK.keySet())) if (key.getNamespace().equals(IIS.MODID) && key.getPath().startsWith("block_")) metal.add(BuiltInRegistries.BLOCK.getValue(key));
        labelAt(x + 12, 3.4, 0, "Блоки материалов", "gold", 1.6);
        gallery(x + 3, 6, metal, 12, 2, 3);
        endHall(Blocks.TERRACOTTA);
    }

    // --- 13. Космос ---------------------------------------------------------------------------------------------------
    private void spaceHall() {
        beginHall("Космос", "Ракета на площадке, верстак НАСА, заправка, кислород и грунт Луны");
        int cx = 10, cz = 10;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(at(cx + dx, 0, cz + dz), SpaceContent.LAUNCH_PAD.get().defaultBlockState());
        set(at(cx + 3, 0, cz), SpaceContent.FUEL_LOADER.get().defaultBlockState());
        sign(at(cx + 3, 0, cz - 1), nameOf(SpaceContent.FUEL_LOADER.get()));
        RocketEntity r = SpaceContent.ROCKET.get().create(level, EntitySpawnReason.TRIGGERED);
        if (r != null) {
            BlockPos p = at(cx, 1, cz);
            r.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            r.setup(new RocketItem.Stored(18, RocketEntity.CAPACITY_T1));
            level.addFreshEntity(r);
        }
        labelAt(cx, 12.0, cz, "Ракета первого уровня\nЗаправлена: садитесь и летите на Луну", "aqua", 1.2);
        sign(at(cx, 0, cz - 2), "Стартовая площадка");
        List<Block> rest = new ArrayList<>();
        SpaceContent.BLOCKS.getEntries().forEach(h -> {
            Block b = h.get();
            if (!(b instanceof LiquidBlock) && b != SpaceContent.LAUNCH_PAD.get() && b != SpaceContent.FUEL_LOADER.get() && !shown.contains(b)) rest.add(b);
        });
        gallery(cx + 8, 6, rest, 6, 3, 4);
        int z = 22;
        labelAt(10, 2.8, z, "Детали ракеты и припасы", "aqua", 1.0);
        List<String> ids = new ArrayList<>();
        SpaceContent.ITEMS.getEntries().forEach(h -> {
            if (!(h.get() instanceof net.minecraft.world.item.BlockItem)) ids.add(h.getId().getPath());
        });
        items(2, z + 2, ids, 10);
        endHall(Blocks.BLACK_CONCRETE);
    }

    // --- 14. Железная дорога ------------------------------------------------------------------------------------------
    private void railHall() {
        beginHall("Железная дорога", "Все виды путей и блоки дороги");
        List<Block> tracks = new ArrayList<>();
        ru.iis.rail.RailContent.TRACKS.values().forEach(d -> tracks.add(d.get()));
        for (int i = 0; i < tracks.size(); i++) {
            int x = 2 * (i % 13), z = 6 + 3 * (i / 13);
            set(at(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            set(at(x, 1, z), tracks.get(i).defaultBlockState());
            wallSign(at(x, 0, z), nameOf(tracks.get(i)));
        }
        List<Block> rest = new ArrayList<>();
        ru.iis.rail.RailContent.BLOCKS.getEntries().forEach(h -> {
            if (!shown.contains(h.get())) rest.add(h.get());
        });
        gallery(0, 6 + 3 * (tracks.size() / 13 + 1) + 2, rest, 9, 3, 3);
        endHall(Blocks.ANDESITE);
    }

    // --- 15. Био ------------------------------------------------------------------------------------------------------
    private void bioHall() {
        cursor -= 70;
        mirrored = false;
        beginHall("Био", "Колония, ручные места очага, LV-органы, сеть сосудов с насосами, все предметы и жидкости");
        bioOrigin = new BlockPos(ox, y0, oz);
        bio();
        for (int cxx = (hallMinX - 2) >> 4; cxx <= (hallMaxX + 2) >> 4; cxx++) for (int czz = (oz - 3) >> 4; czz <= (hallMaxZ + 3) >> 4; czz++) keepForced.add(ChunkPos.pack(cxx, czz));
        ShuttleSpawner.build(level, ox + 50, oz + 17, y0, 0, false, ox + 46.0, oz + 20.0, 1234L);
        sign(at(45, 0, 16), "Аварийный челнок");
        track(at(60, 0, 34));
        mirrored = true;
        shown.addAll(BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getPath().startsWith("shuttle_")).toList());
        endHall(Blocks.POLISHED_DEEPSLATE);
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

    // --- 16. Прочее ---------------------------------------------------------------------------------------------------
    /** Всё, что не вошло в залы: так стенд показывает всё содержимое мода, даже новое. */
    private int leftoversHall() {
        List<Block> rest = new ArrayList<>();
        for (Identifier key : new TreeSet<>(BuiltInRegistries.BLOCK.keySet())) {
            if (!key.getNamespace().equals(IIS.MODID)) continue;
            Block b = BuiltInRegistries.BLOCK.getValue(key);
            if (b instanceof LiquidBlock || shown.contains(b)) continue;
            rest.add(b);
        }
        if (rest.isEmpty()) return 0;
        int n = rest.size();
        IIS.LOGGER.info("[IIS] stand leftovers: {}", rest.stream().limit(80).map(b -> BuiltInRegistries.BLOCK.getKey(b).getPath()).toList());
        beginHall("Прочее", "Всё остальное, что есть в моде");
        gallery(0, 4, rest, 14, 3, 3);
        endHall(Blocks.GRAY_TERRACOTTA);
        return n;
    }

    // --- аллея и доска переходов ---------------------------------------------------------------------------------------
    private void avenue() {
        ox = 0;
        oz = 0;
        for (int x = cursor; x <= SPAWN_X + 13; x++) {
            int top = x >= 4 ? -8 : AVENUE_Z0;
            for (int z = top; z <= AVENUE_Z1; z++) {
                BlockPos p = new BlockPos(x, y0 - 1, z);
                track(p);
                boolean edge = z == top || z == AVENUE_Z1;
                boolean lamp = Math.floorMod(x, 8) == 0 && edge;
                level.setBlock(p, (lamp ? Blocks.SEA_LANTERN : edge ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ).defaultBlockState(), 2);
            }
        }
        // доска: таблички смотрят на юг, клик переносит в зал
        int wallZ = -7, perRow = 4, rows = (halls.size() + perRow - 1) / perRow, x0 = SPAWN_X - perRow + 1;
        for (int x = x0 - 2; x <= x0 + perRow * 2; x++) for (int dy = 0; dy <= rows + 1; dy++) {
            BlockPos p = new BlockPos(x, y0 + dy, wallZ);
            track(p);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
        }
        for (int i = 0; i < halls.size(); i++) {
            Hall h = halls.get(i);
            BlockPos p = new BlockPos(x0 + (i % perRow) * 2, y0 + rows - i / perRow, wallZ + 1);
            track(p);
            level.setBlock(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), 2);
            writeSign(p, h.title(), "/tp @s " + h.x() + " " + y0 + " " + (h.z() - 2) + " 0 0");
        }
        label(SPAWN_X + 0.5, y0 + rows + 4.2, wallZ + 0.5, "ИИС 2 — стенд", "gold", 3.0, true);
        label(SPAWN_X + 0.5, y0 + rows + 2.2, wallZ + 0.5, "Нажмите на табличку правой кнопкой, чтобы перейти в зал. Или идите по аллее: первый зал — рабочая сеть", "white", 0.9, false);
        views.add(new View("Вход", SPAWN_X + 0.5, y0 + 1.6, SPAWN_Z + 0.5, 180, -8));
        views.add(new View("Аллея", SPAWN_X + 4, y0 + 18, -14, 55, 30));
    }
}
