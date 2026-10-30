package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvContent;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.EbfMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Чистая комната (GT_MetaTileEntity_Cleanroom, D105). Контроллер — в центре потолка; комната от 3×4×3 до 15×15×15,
 * квадратная в плане. Потолок кроме рёбер — фильтрующие корпуса, рёбра — пласкрит (на рёбрах можно ставить люк
 * обслуживания и энергетический люк), стены и пол — пласкрит, окна из пласкрита, люки, одна железная дверь (вторая —
 * если площадь пола не меньше 7×7) и корпуса машин (не больше трети стены) для передачи предметов и энергии.
 * Ровно один люк обслуживания и один энергетический. Энергия — 3 EU/t на клетку потолка внутри рёбер; очистка — 80 с на
 * блок высоты внутри; люк выше нужного уровня ускоряет очистку, но берёт вдвое больше за каждый уровень. Очищенная
 * комната берёт втрое меньше. Открытая дверь пачкает комнату. Машины внутри делают рецепты «только в чистой комнате»,
 * выходы которых пропадают с шансом её загрязнённости.
 */
public class CleanroomBlockEntity extends MultiblockControllerBlockEntity {
    /** MachineStats.cfg ИИС: Cleanroom.euPerVent, cleanBlockTimeByVentTicks, idleEnergyReduceMultiplier, mHullsLessInTimesThanWallsSquare. */
    public static final int EU_PER_VENT = 3, CLEAN_TICKS_PER_BLOCK = 1600, IDLE_REDUCE = 3, HULL_RATIO = 3;
    private static final Set<CleanroomBlockEntity> FORMED = ConcurrentHashMap.newKeySet();

    private int sizeX, sizeY, sizeZ, openDoors, doorsUsed, doorsAllowed, hullsUsed, hullsAllowed, progressMultiplier = 1;
    private @Nullable BoundingBox interior;

    public CleanroomBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.CLEANROOM_BE.get(), pos, state);
    }

    /** Чистота (0…10000) лучшей собранной чистой комнаты, внутри которой стоит блок; 0 — вне комнаты. */
    public static int cleanlinessAt(Level level, BlockPos pos) {
        int best = 0;
        for (CleanroomBlockEntity c : FORMED) {
            if (c.isRemoved() || c.level != level || !c.formed || c.interior == null || !c.interior.isInside(pos)) continue;
            best = Math.max(best, c.efficiency);
        }
        return best;
    }

    /** GT: каждый выход рецепта чистой комнаты пропадает, если случайное 0…9999 больше её чистоты. */
    public static List<ItemStack> spoil(List<ItemStack> outputs, int cleanliness, RandomSource random) {
        List<ItemStack> r = new ArrayList<>(outputs.size());
        for (ItemStack s : outputs) r.add(!s.isEmpty() && random.nextInt(10000) > cleanliness ? ItemStack.EMPTY : s);
        return r;
    }

    public int openDoors() {
        return openDoors;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        boolean ok = check(sl);
        if (ok) FORMED.add(this);
        else FORMED.remove(this);
        return ok;
    }

    @Override
    public void setRemoved() {
        FORMED.remove(this);
        super.setRemoved();
    }

    private boolean is(ServerLevel sl, BlockPos p, Block b) {
        return sl.getBlockState(p).is(b);
    }

    /** Люк обслуживания или энергетический люк. */
    private boolean addHatch(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == HatchType.MAINTENANCE) parts.maintenance.add(p);
        else if (t != null && t.energy() && !t.dynamo()) parts.energy.add(p);
        else return false;
        return true;
    }

    private static boolean hull(Block b) {
        return b == LvContent.HULL_LV.get() || b == LvContent.HULL_MV.get() || b == LvContent.HULL_HV.get() || b == LvContent.HULL_EV.get()
                || b == LvContent.HULL_IV.get();
    }

    /** GT checkMachine. */
    private boolean check(ServerLevel sl) {
        parts.clear();
        interior = null;
        Block filter = MultiContent.FILTER_CASING.get(), plascrete = MultiContent.PLASCRETE.get();
        int x = 0, y = 0;
        doorsUsed = hullsUsed = openDoors = 0;
        doorsAllowed = 2;
        for (int i = 1; i < 8; i++) {
            BlockPos bx = worldPosition.offset(i, 0, 0), bz = worldPosition.offset(0, 0, i);
            if (is(sl, bx, filter) && is(sl, bz, filter)) continue;
            if (!is(sl, bx, plascrete) || !is(sl, bz, plascrete)) return false;
            x = i;
            break;
        }
        if (x == 0) return false;
        int z = x;
        for (int i = -1; i > -16; i--) {
            if (is(sl, worldPosition.offset(x, i, z), plascrete)) y = i;
            else break;
        }
        if (y > -3) return false;
        sizeX = x * 2 + 1;
        sizeY = -y + 1;
        sizeZ = z * 2 + 1;
        hullsAllowed = ((sizeX - 2) * (sizeY - 2) * 4 - 2) / HULL_RATIO;
        if (sizeX * sizeZ >= 49) doorsAllowed = 4;
        for (int dX = -x; dX <= x; dX++) {
            for (int dZ = -z; dZ <= z; dZ++) {
                for (int dY = 0; dY >= y; dY--) {
                    boolean ex = dX == -x || dX == x, ey = dY == 0 || dY == y, ez = dZ == -z || dZ == z;
                    if (!ex && !ey && !ez) continue;
                    if (dX == 0 && dY == 0 && dZ == 0) continue;
                    BlockPos p = worldPosition.offset(dX, dY, dZ);
                    BlockState st = sl.getBlockState(p);
                    if ((ex && ey) || (ex && ez) || (ez && ey)) {
                        if (!st.is(plascrete) && !addHatch(sl, p)) return false;
                    } else if (dY == 0) {
                        if (!st.is(filter)) return false;
                    } else if (!st.is(plascrete) && !addHatch(sl, p)) {
                        if (st.is(Blocks.IRON_DOOR)) {
                            if (st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && st.getValue(DoorBlock.OPEN)) openDoors++;
                            doorsUsed++;
                        } else if (st.is(MultiContent.PLASCRETE_WINDOW.get())) {
                            // окно — можно
                        } else if (hull(st.getBlock())) {
                            hullsUsed++;
                        } else {
                            return false;
                        }
                    }
                }
            }
        }
        if (parts.maintenance.size() != 1 || parts.energy.size() != 1 || doorsUsed < 2 || doorsUsed > doorsAllowed || hullsUsed > hullsAllowed) {
            return false;
        }
        interior = BoundingBox.fromCorners(worldPosition.offset(-x + 1, y + 1, -z + 1), worldPosition.offset(x - 1, -1, z - 1));
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected int idleInterval() {
        return 100;
    }

    /** GT checkRecipe: цикл 100 тиков; рост чистоты за цикл обратно пропорционален высоте, открытые двери её снижают. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        maxProgress = 100;
        progress = 0;
        long e = (long) EU_PER_VENT * (sizeX - 2) * (sizeZ - 2);
        int currentTier = BlastLogic.tier(maxInputVoltage(sl)), requiredTier = BlastLogic.tier(e);
        progressMultiplier = currentTier <= requiredTier ? 1 : 1 << (currentTier - requiredTier);
        int reduce = CLEAN_TICKS_PER_BLOCK * (sizeY - 2), max = 1000000;
        if (progressMultiplier > 1) {
            efficiencyIncrease = max * progressMultiplier / reduce;
            e *= progressMultiplier * 2L;
        } else {
            efficiencyIncrease = max / reduce;
        }
        if (efficiency >= 10000) e /= IDLE_REDUCE;
        if (openDoors > 0) efficiencyIncrease = -(efficiencyIncrease * openDoors);
        eut = (int) e;
        pendingItems = List.of();
        pendingFluids = List.of();
        setChanged();
        return true;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.cleanroom(id, inventory, data);
    }
}
