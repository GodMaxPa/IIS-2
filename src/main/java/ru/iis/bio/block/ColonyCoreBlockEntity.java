package ru.iis.bio.block;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.IISConfig;
import ru.iis.bio.BioContent;
import ru.iis.bio.BioSounds;
import ru.iis.bio.core.ColonyState;
import ru.iis.bio.core.ColonyState.Action;
import ru.iis.bio.core.ColonyState.Mood;
import ru.iis.bio.core.TissueGraph;
import ru.iis.bio.core.TissueGraph.Square;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.production.BioStationMenu;
import ru.iis.bio.production.BioTank;
import ru.iis.bio.production.Station;
import ru.iis.bio.production.StationLogic;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import java.util.UUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Блок-сущность ядра: хранит {@link ColonyState}, список ячеек с тканью и раз в секунду выполняет решение чистой логики
 * (вырастить клетку, отступить, поправить вид ткани). Ткань не разрывается (D68): живы только квадраты, которые напрямую
 * цепочкой соединены с ядром ({@link TissueGraph}); оторванный кусок отмирает, а при голоде отступает самый дальний конец.
 */
public class ColonyCoreBlockEntity extends BlockEntity implements StationLogic.Host, MenuProvider {
    /** Территория: чанк ядра ± 1 по горизонтали (CONCEPT §8.3), по вертикали ± 24 блока. */
    public static final int TERRITORY_CHUNKS = 1, TERRITORY_HEIGHT = 24;
    private static final int SWEEP_PER_SECOND = 12;

    private final ColonyState colony = new ColonyState(IISConfig.bioParams());
    /** Ячейки, в которых колония вырастила ткань (список — для случайного выбора и сохранения). */
    private final LongArrayList tiles = new LongArrayList();
    private final LongOpenHashSet tileSet = new LongOpenHashSet();
    private int secondTimer, beatTimer, frontierBlockedSec, sweepCursor, growlTimer, lastSquares;
    private Mood lastMood = Mood.DORMANT;
    /** Часть ткани лежит в невыгруженных чанках: связность и потери в эту секунду не судим. */
    private boolean partial;
    /** Ниже этой высоты ткань не растёт (только на время начального заражения челнока). */
    private int growMinY = Integer.MIN_VALUE;
    /** Достижимые от ядра квадраты на момент последней проверки (источники роста). */
    private List<Square> reachable = new ArrayList<>();

    /** Линия колонии: переходит на соскобы, посевы и выращенные из них органы. */
    private String lineage = UUID.randomUUID().toString();
    /** Пищеварительный карман (D70): готовит N0, ростовую фракцию, гидролизат и посев; пятый бак — N0 самой колонии. */
    public final StationLogic pocket;

    public ColonyCoreBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.CORE_BE.get(), pos, state);
        this.pocket = new StationLogic(Station.POCKET, this, new ColonyN0Tank());
    }

    /** Запас N0 колонии как бак: карман наливает в него, ковш берёт, органы списывают поддержание. */
    private final class ColonyN0Tank extends BioTank {
        ColonyN0Tank() {
            super(ColonyState.Params.DEFAULT.nutritionCap(), true);
        }

        @Override
        public Fluid fluid() {
            return colony.nutrition() >= 1 ? BioFluids.N0.source().get() : net.minecraft.world.level.material.Fluids.EMPTY;
        }

        @Override
        public int amount() {
            return (int) colony.nutrition();
        }

        @Override
        public int capacity() {
            return colony.params().nutritionCap();
        }

        @Override
        public int space(Fluid f) {
            return f == BioFluids.N0.source().get() ? Math.max(0, capacity() - amount()) : 0;
        }

        @Override
        public void setRaw(Fluid f, int mb) {
            colony.setNutrition(mb);
        }

        @Override
        public boolean dedicated() {
            return true;
        }

        @Override
        public boolean dedicatedTo(Fluid f) {
            return f == BioFluids.N0.source().get();
        }

        @Override
        public int fill(Fluid f, int mb, boolean simulate) {
            int n = Math.min(mb, space(f));
            if (n <= 0 || simulate) return Math.max(0, n);
            return feed(n);
        }

        @Override
        public int drain(Fluid f, int mb, boolean simulate) {
            if (f != net.minecraft.world.level.material.Fluids.EMPTY && f != BioFluids.N0.source().get()) return 0;
            int n = Math.min(mb, amount());
            if (n <= 0 || simulate) return Math.max(0, n);
            colony.drain(n);
            setChanged();
            return n;
        }
    }

    /** Карман и запас N0 как обработчик жидкостей (сосуды, трубы, капсулы). */
    public final ru.iis.bio.production.BioFluidHandler fluids() {
        if (fluidHandler == null) fluidHandler = new ru.iis.bio.production.BioFluidHandler(pocket.tanks, this::setChanged);
        return fluidHandler;
    }

    private ru.iis.bio.production.BioFluidHandler fluidHandler;

    public String lineage() {
        return lineage;
    }

    /** Списать поддержание органа (мБ N0); false — не хватает. */
    public boolean drainN0(double mb) {
        if (colony.nutrition() < mb) return false;
        colony.drain(mb);
        return true;
    }

    /** Пасть передаёт проглоченное в карман: true — нашлось место во входных слотах. */
    public boolean swallow(ItemStack one) {
        for (int i = 0; i < Station.POCKET.inputs; i++) {
            ItemStack cur = pocket.getItem(i);
            if (cur.isEmpty()) {
                pocket.setItem(i, one.copyWithCount(1));
                return true;
            }
            if (ItemStack.isSameItemSameComponents(cur, one) && cur.getCount() < cur.getMaxStackSize()) {
                cur.grow(1);
                pocket.setChanged();
                return true;
            }
        }
        return false;
    }

    // --- StationLogic.Host / меню кармана ---------------------------------------------------------------------------
    @Override
    public @org.jspecify.annotations.Nullable ServerLevel serverLevel() {
        return level instanceof ServerLevel sl ? sl : null;
    }

    @Override
    public void changed() {
        setChanged();
    }

    @Override
    public String defaultLineage() {
        return lineage;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.iis.bio.pocket");
    }

    @Override
    public @org.jspecify.annotations.Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BioStationMenu(id, inventory, pocket, pocket.data, Station.POCKET, worldPosition);
    }

    // --- запросы ------------------------------------------------------------------------------------------------
    public ColonyState colony() {
        return colony;
    }

    /** Число клеток ткани (квадратов), напрямую связанных с ядром. */
    public int tileCount() {
        return reachable.size();
    }

    public boolean hasTile(BlockPos p) {
        return tileSet.contains(p.asLong());
    }

    /** Принять уже стоящую ячейку ткани в колонию (тесты, мировая генерация). Связность проверит ближайшая секунда. */
    public boolean adoptTile(BlockPos p) {
        if (!tileSet.add(p.asLong())) return false;
        tiles.add(p.asLong());
        setChanged();
        return true;
    }

    /** Лежит ли позиция в территории этого ядра. */
    public boolean claims(BlockPos p) {
        int dcx = Math.abs((p.getX() >> 4) - (worldPosition.getX() >> 4));
        int dcz = Math.abs((p.getZ() >> 4) - (worldPosition.getZ() >> 4));
        return dcx <= TERRITORY_CHUNKS && dcz <= TERRITORY_CHUNKS && Math.abs(p.getY() - worldPosition.getY()) <= TERRITORY_HEIGHT;
    }

    /** Связан ли блок на этой позиции с колонией: вплотную к нему ядро или ячейка с тканью. */
    public boolean connectsTo(BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            if (n.equals(worldPosition) || tileSet.contains(n.asLong())) return true;
        }
        return false;
    }

    /** Ядро, к колонии которого подключён блок на этой позиции (поиск по 3×3 чанкам вокруг), или null. */
    public static @Nullable ColonyCoreBlockEntity find(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = level.getChunkSource().getChunkNow((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
                if (c == null) continue;
                for (BlockEntity be : c.getBlockEntities().values()) {
                    if (be instanceof ColonyCoreBlockEntity core && core.claims(pos) && core.connectsTo(pos)) return core;
                }
            }
        return null;
    }

    /** Ядро, в чьей территории лежит позиция (без требования соседства), или null. */
    public static @Nullable ColonyCoreBlockEntity findOwnerByTerritory(Level level, BlockPos pos) {
        // ближайшее ядро, чья территория включает позицию (территории соседних колоний могут перекрываться)
        ColonyCoreBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = level.getChunkSource().getChunkNow((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
                if (c == null) continue;
                for (BlockEntity be : c.getBlockEntities().values()) {
                    if (be instanceof ColonyCoreBlockEntity core && core.claims(pos)) {
                        double d = core.getBlockPos().distSqr(pos);
                        if (d < bestD) {
                            bestD = d;
                            best = core;
                        }
                    }
                }
            }
        return best;
    }

    /** Ядро, которому принадлежит клетка ткани на этой позиции, или null. */
    public static @Nullable ColonyCoreBlockEntity owner(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = level.getChunkSource().getChunkNow((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
                if (c == null) continue;
                for (BlockEntity be : c.getBlockEntities().values()) {
                    if (be instanceof ColonyCoreBlockEntity core && core.hasTile(pos)) return core;
                }
            }
        return null;
    }

    public Component status() {
        Mood m = colony.mood();
        return Component.translatable("message.iis.bio.status",
                Component.translatable("message.iis.bio.mood." + m.name().toLowerCase()),
                (int) colony.nutrition(), colony.params().nutritionCap(),
                tileCount(), colony.params().maxTiles());
    }

    // --- питание ------------------------------------------------------------------------------------------------
    /** Накормить организм (вызывает пасть). Возвращает принятые единицы. */
    public int feed(int units) {
        if (!(level instanceof ServerLevel sl)) return 0;
        boolean wasAwake = colony.awake();
        int accepted = colony.feed(units);
        if (accepted <= 0) return 0;
        setChanged();
        if (!wasAwake) {
            // пробуждение очага
            sl.playSound(null, worldPosition, BioSounds.GROW, SoundSource.BLOCKS, 1.0f, 0.6f);
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.02);
        }
        syncMood(sl);
        return accepted;
    }

    // --- такт ---------------------------------------------------------------------------------------------------
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        pocket.tick();
        heartbeat(sl);
        if (++secondTimer < 20) return;
        secondTimer = 0;
        secondTick(sl);
    }

    private void heartbeat(ServerLevel sl) {
        if (--beatTimer > 0) return;
        Mood m = colony.mood();
        float vol, pitch;
        switch (m) {
            case DORMANT -> { beatTimer = 130; vol = 0.25f; pitch = 0.75f; }
            case HUNGRY -> { beatTimer = 75; vol = 0.4f; pitch = 0.6f; }
            case CONTENT -> { beatTimer = 90; vol = 0.5f; pitch = 1.0f; }
            case GROWING -> { beatTimer = 55; vol = 0.65f; pitch = 1.15f; }
            default -> { beatTimer = 16; vol = 0.85f; pitch = 1.5f; }
        }
        sl.playSound(null, worldPosition, BioSounds.HEARTBEAT, SoundSource.BLOCKS, vol, pitch);
        RandomSource r = sl.getRandom();
        ParticleOptions p = switch (m) {
            case GROWING -> ParticleTypes.WARPED_SPORE;
            case HUNGRY -> ParticleTypes.ASH;
            case ALARMED -> ParticleTypes.CRIMSON_SPORE;
            case CONTENT -> r.nextInt(3) == 0 ? ParticleTypes.CRIMSON_SPORE : null;
            default -> null;
        };
        if (p != null) {
            sl.sendParticles(p, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, m == Mood.ALARMED ? 10 : 3, 0.35, 0.2, 0.35, 0.01);
        }
    }

    private void secondTick(ServerLevel sl) {
        // 1. что сейчас есть: квадраты ткани в ячейках колонии; потерянные ячейки забываем
        Set<Square> squares = collectSquares(sl);
        int before = lastSquares;

        // 2. связность: всё, что не дотягивается до ядра напрямую, отмирает
        Map<Square, Integer> dist = TissueGraph.distances(squares, this::isRoot);
        int orphans = 0;
        for (Square s : squares) {
            if (!partial && !dist.containsKey(s)) {
                removeFace(sl, s);
                orphans++;
            }
        }
        if (orphans > 0) {
            sl.playSound(null, worldPosition, BioSounds.HISS, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        if (!partial) squares.removeIf(s -> !dist.containsKey(s));
        int lost = before - squares.size() - orphans;
        if (!partial && lost > 0 && colony.awake()) {
            // кусок ткани сломан снаружи (игрок, взрыв, потеряна опора)
            colony.alarm();
            sl.playSound(null, worldPosition, BioSounds.HISS, SoundSource.BLOCKS, 0.7f, 1.2f);
        }
        reachable = new ArrayList<>(squares);
        colony.setTiles(reachable.size());

        // 3. вид ткани подстраивается под состояние (увядание при голоде)
        sweepVitality(sl);

        // 4. решение чистой логики
        if (frontierBlockedSec > 0) frontierBlockedSec--;
        Action a = colony.tickSecond(frontierBlockedSec == 0);
        if (a == Action.GROW) growTile(sl);
        else if (a == Action.RETREAT) retreatTile(sl, dist);
        colony.setTiles(reachable.size());
        lastSquares = reachable.size();

        // 5. голодное урчание
        if (colony.mood() == Mood.HUNGRY && --growlTimer <= 0) {
            growlTimer = 12;
            sl.playSound(null, worldPosition, BioSounds.GROWL, SoundSource.BLOCKS, 0.9f, 0.9f + sl.getRandom().nextFloat() * 0.2f);
        }
        syncMood(sl);
        setChanged();
    }

    /** Квадрат — корень, если прикреплён к самому ядру. */
    private boolean isRoot(Square s) {
        return s.x() + TissueGraph.DX[s.face()] == worldPosition.getX() && s.y() + TissueGraph.DY[s.face()] == worldPosition.getY()
                && s.z() + TissueGraph.DZ[s.face()] == worldPosition.getZ();
    }

    private Set<Square> collectSquares(ServerLevel sl) {
        Set<Square> out = new HashSet<>();
        partial = false;
        BioTissueBlock tissue = BioContent.TISSUE.get();
        for (int i = tiles.size() - 1; i >= 0; i--) {
            BlockPos p = BlockPos.of(tiles.getLong(i));
            if (!sl.hasChunkAt(p)) {
                partial = true; // чанк не загружен: живое не трогаем
                continue;
            }
            BlockState st = sl.getBlockState(p);
            if (!st.is(tissue)) {
                tileSet.remove(tiles.getLong(i));
                tiles.removeLong(i);
                continue;
            }
            for (Direction d : Direction.values()) {
                if (MultifaceBlock.hasFace(st, d)) out.add(new Square(p.getX(), p.getY(), p.getZ(), d.ordinal()));
            }
        }
        return out;
    }

    private void removeFace(ServerLevel sl, Square s) {
        BlockPos p = new BlockPos(s.x(), s.y(), s.z());
        BlockState st = sl.getBlockState(p);
        if (!st.is(BioContent.TISSUE.get())) return;
        Direction d = Direction.from3DDataValue(s.face());
        BlockState ns = st.setValue(MultifaceBlock.getFaceProperty(d), false);
        boolean any = false;
        for (Direction x : Direction.values()) any |= MultifaceBlock.hasFace(ns, x);
        if (any) {
            sl.setBlock(p, ns, 3);
        } else {
            sl.removeBlock(p, false);
        }
        sl.sendParticles(ParticleTypes.ASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
    }

    private void syncMood(ServerLevel sl) {
        Mood m = colony.mood();
        CoreMood cm = CoreMood.of(m);
        BlockState st = getBlockState();
        if (st.getValue(ColonyCoreBlock.MOOD) != cm) sl.setBlock(worldPosition, st.setValue(ColonyCoreBlock.MOOD, cm), 3);
        if (m != lastMood) {
            if (m == Mood.HUNGRY) sl.playSound(null, worldPosition, BioSounds.GROWL, SoundSource.BLOCKS, 1.0f, 0.8f);
            beatTimer = Math.min(beatTimer, 10);
            lastMood = m;
        }
    }

    private void sweepVitality(ServerLevel sl) {
        if (tiles.isEmpty()) return;
        Vitality target = colony.mood() == Mood.HUNGRY ? Vitality.STARVING : Vitality.ALIVE;
        int n = tiles.size();
        for (int k = 0; k < Math.min(n, SWEEP_PER_SECOND); k++) {
            sweepCursor = (sweepCursor + 1) % n;
            BlockPos p = BlockPos.of(tiles.getLong(sweepCursor));
            if (!sl.hasChunkAt(p)) continue;
            BlockState s = sl.getBlockState(p);
            if (s.is(BioContent.TISSUE.get()) && s.getValue(BioTissueBlock.VITALITY) != target) {
                sl.setBlock(p, s.setValue(BioTissueBlock.VITALITY, target), 2);
            }
        }
    }

    // --- рост и отступление -------------------------------------------------------------------------------------
    private record Candidate(BlockPos cell, Direction face) {}

    /** Начальное заражение (челнок): вырастить без затрат питания до {@code squares} клеток вокруг ядра. */
    public void seedTissue(int squares) {
        if (!(level instanceof ServerLevel sl)) return;
        growMinY = worldPosition.getY(); // заражение снаружи: ниже ядра (в салоне) не растём
        for (int i = 0; i < squares; i++) {
            Candidate c = findCandidate(sl);
            if (c == null || !placeTile(sl, c)) break;
        }
        growMinY = Integer.MIN_VALUE;
        colony.setTiles(reachable.size());
        lastSquares = reachable.size();
        setChanged();
    }

    private void growTile(ServerLevel sl) {
        Candidate c = findCandidate(sl);
        if (c == null) {
            colony.refundGrowth();
            frontierBlockedSec = 10;
            return;
        }
        if (!placeTile(sl, c)) colony.refundGrowth();
    }

    private boolean placeTile(ServerLevel sl, Candidate c) {
        BlockState cur = sl.getBlockState(c.cell);
        BioTissueBlock tissue = BioContent.TISSUE.get();
        if (cur.is(Blocks.SNOW)) {
            // ткань пробивается сквозь снег: слой растаял, место занято тканью
            sl.removeBlock(c.cell, false);
            cur = Blocks.AIR.defaultBlockState();
        }
        BlockState ns = tissue.getStateForPlacement(cur, sl, c.cell, c.face);
        if (ns == null) return false;
        sl.setBlock(c.cell, ns, 3);
        if (tileSet.add(c.cell.asLong())) tiles.add(c.cell.asLong());
        reachable.add(new Square(c.cell.getX(), c.cell.getY(), c.cell.getZ(), c.face.ordinal()));
        sl.playSound(null, c.cell, BioSounds.GROW, SoundSource.BLOCKS, 0.7f, 0.9f + sl.getRandom().nextFloat() * 0.3f);
        sl.sendParticles(ParticleTypes.WARPED_SPORE, c.cell.getX() + 0.5, c.cell.getY() + 0.5, c.cell.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
        return true;
    }

    /** Отступает самый дальний (по цепочке от ядра) квадрат — его удаление не разрывает ткань. */
    private void retreatTile(ServerLevel sl, Map<Square, Integer> dist) {
        Square far = null;
        int best = -1;
        for (Square s : reachable) {
            int d = dist.getOrDefault(s, -1);
            if (d > best) {
                best = d;
                far = s;
            }
        }
        if (far == null) return;
        removeFace(sl, far);
        reachable.remove(far);
        sl.playSound(null, new BlockPos(far.x(), far.y(), far.z()), BioSounds.HISS, SoundSource.BLOCKS, 0.5f, 0.8f);
    }

    /**
     * Место для новой клетки: квадрат, напрямую соединённый с уже живым (или прикреплённый к самому ядру), в воздухе (или в снегу —
     * он тает), с опорой на целый блок. На пасть и другие органы ткань не растёт.
     */
    private @Nullable Candidate findCandidate(ServerLevel sl) {
        RandomSource r = sl.getRandom();
        List<Candidate> options = new ArrayList<>();
        if (reachable.isEmpty()) {
            for (Direction d : Direction.values()) options.add(new Candidate(worldPosition.relative(d), d.getOpposite()));
        } else {
            int tries = Math.min(reachable.size(), 8);
            for (int i = 0; i < tries; i++) {
                Square src = reachable.get(r.nextInt(reachable.size()));
                for (Square n : TissueGraph.neighbours(src)) {
                    options.add(new Candidate(new BlockPos(n.x(), n.y(), n.z()), Direction.from3DDataValue(n.face())));
                }
            }
            // корень может расти и на самом ядре: свободные грани ядра рядом с живой тканью
            for (Direction d : Direction.values()) options.add(new Candidate(worldPosition.relative(d), d.getOpposite()));
        }
        Collections.shuffle(options, new java.util.Random(r.nextLong()));
        for (Candidate c : options) {
            if (valid(sl, c)) return c;
        }
        return null;
    }

    private boolean valid(ServerLevel sl, Candidate c) {
        BlockPos cell = c.cell;
        if (!claims(cell) || cell.getY() < growMinY || !sl.hasChunkAt(cell) || !sl.isInWorldBounds(cell)) return false;
        BlockPos support = cell.relative(c.face);
        if (!sl.hasChunkAt(support)) return false;
        BlockState cs = sl.getBlockState(cell);
        BioTissueBlock tissue = BioContent.TISSUE.get();
        boolean ownCell = cs.is(tissue) && tileSet.contains(cell.asLong());
        if (!(cs.isAir() || cs.is(Blocks.SNOW) || ownCell)) return false;
        if (ownCell && MultifaceBlock.hasFace(cs, c.face)) return false;
        BlockState sup = sl.getBlockState(support);
        if (sup.is(BioContent.MAW.get())) return false;
        // для снега опору проверяем по воздуху в ячейке: слой не должен мешать расчёту
        return MultifaceBlock.canAttachTo(sl, c.face, support, sup);
    }

    // --- удаление ядра: ткань умирает ---------------------------------------------------------------------------
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel sl) {
            for (int i = 0; i < tiles.size(); i++) {
                BlockPos p = BlockPos.of(tiles.getLong(i));
                if (sl.hasChunkAt(p) && sl.getBlockState(p).is(BioContent.TISSUE.get())) sl.removeBlock(p, false);
            }
            sl.playSound(null, pos, BioSounds.HISS, SoundSource.BLOCKS, 1.0f, 0.6f);
            for (ItemStack st : pocket.drops()) net.minecraft.world.Containers.dropItemStack(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, st);
        }
    }

    // --- сохранение ---------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ColonyState.Snapshot s = colony.snapshot();
        out.putDouble("nutrition", s.nutrition());
        out.putBoolean("awake", s.awake());
        out.putInt("starved", s.starvedSec());
        out.putInt("growCooldown", s.growCooldown());
        out.putInt("alarm", s.alarmLeft());
        out.putInt("squares", lastSquares);
        List<Long> list = new ArrayList<>(tiles.size());
        for (int i = 0; i < tiles.size(); i++) list.add(tiles.getLong(i));
        out.store("tiles", Codec.LONG.listOf(), list);
        out.putString("lineage", lineage);
        pocket.save(out.child("pocket"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tiles.clear();
        tileSet.clear();
        in.read("tiles", Codec.LONG.listOf()).ifPresent(l -> l.forEach(v -> {
            if (tileSet.add(v.longValue())) tiles.add(v.longValue());
        }));
        lastSquares = in.getIntOr("squares", 0);
        colony.restore(new ColonyState.Snapshot(in.getDoubleOr("nutrition", 0), in.getBooleanOr("awake", false), lastSquares,
                in.getIntOr("starved", 0), in.getIntOr("growCooldown", 0), in.getIntOr("alarm", 0)));
        lastMood = colony.mood();
        String l = in.getStringOr("lineage", "");
        if (!l.isEmpty()) lineage = l;
        pocket.load(in.childOrEmpty("pocket"));
    }
}
