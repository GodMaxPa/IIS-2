package ru.iis.bio.production;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.block.ColonyCoreBlockEntity;
import ru.iis.bio.block.LivingTankBlockEntity;
import ru.iis.bio.fluid.BioFluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Простой старт Био (D155, ответ владельца 16.11: «сосуды до LV не нужны»): живое место на ткани колонии само берёт жидкость
 * рецепта из запасов колонии. N0 — из ядра (оставляя колонии {@link #N0_RESERVE} мБ на жизнь), всё остальное — из живых
 * резервуаров на территории колонии. Сосуды, насос и давление нужны только с MV.
 */
public final class ColonySupply {
    private ColonySupply() {}

    /** Сколько N0 место не трогает: запас колонии на собственное поддержание. */
    public static final int N0_RESERVE = 100;

    /** Долить во входной бак места до {@code amount} мБ жидкости; true — получилось (или уже было). */
    public static boolean pull(@Nullable ServerLevel level, @Nullable ColonyCoreBlockEntity core, List<BioTank> tanks, Fluid fluid, int amount) {
        if (level == null || core == null) return false;
        BioTank target = null;
        for (BioTank t : tanks) {
            if (!t.isOutput() && !t.dedicated() && t.amount() > 0 && t.fluid() == fluid) {
                target = t;
                break;
            }
        }
        if (target == null) {
            for (BioTank t : tanks) {
                if (!t.isOutput() && !t.dedicated() && t.amount() == 0) {
                    target = t;
                    break;
                }
            }
        }
        if (target == null) return false;
        int need = amount - target.amount();
        if (need <= 0) return true;
        if (target.space(fluid) < need) return false;
        if (fluid == BioFluids.N0.source().get()) {
            if (core.colony().nutrition() < need + N0_RESERVE || !core.drainN0(need)) return false;
            target.fill(fluid, need, false);
            return true;
        }
        List<LivingTankBlockEntity> sources = new ArrayList<>();
        int have = 0;
        BlockPos c = core.getBlockPos();
        for (int dx = -ColonyCoreBlockEntity.TERRITORY_CHUNKS; dx <= ColonyCoreBlockEntity.TERRITORY_CHUNKS; dx++) {
            for (int dz = -ColonyCoreBlockEntity.TERRITORY_CHUNKS; dz <= ColonyCoreBlockEntity.TERRITORY_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof LivingTankBlockEntity lt && core.claims(lt.getBlockPos()) && !lt.starving() && lt.tank().fluid() == fluid) {
                        sources.add(lt);
                        have += lt.tank().amount();
                    }
                }
            }
        }
        if (have < need) return false;
        for (LivingTankBlockEntity lt : sources) {
            if (need <= 0) break;
            int n = lt.tank().drain(fluid, need, false);
            target.fill(fluid, n, false);
            need -= n;
            lt.setChanged();
        }
        return need <= 0;
    }
}
