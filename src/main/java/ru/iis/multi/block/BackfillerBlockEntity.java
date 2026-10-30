package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.fluid.GtFluids;
import ru.iis.multi.MultiContent;

/**
 * Бетонный засыпщик и улучшенный засыпщик (GT_MetaTileEntity_ConcreteBackfiller1–2, D106). Спускает трубу до бедрока,
 * а на обратном пути заливает пустоты: под наконечником и на каждом слое в квадрате радиусом 16 блоков (у улучшенного —
 * 64) — по блоку светлого бетона за цикл
 * из 144 л расплава бетона. Трубы складывает в контроллер. Нужны люк обслуживания, входной люк и энергия не ниже MV и EV.
 * Цикл 80 тиков (на подъёме 240), делённые на 2 за ступень напряжения; 6 × 4^T EU/t.
 */
public class BackfillerBlockEntity extends DrillerBlockEntity {
    public static final int CONCRETE_PER_BLOCK = 144;
    private static final int[] MIN_TIER = {2, 4};
    private int lastX, lastZ;

    public BackfillerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.BACKFILLER_BE.get(), pos, state);
    }

    @Override
    protected int level() {
        return Math.max(0, MultiContent.index(MultiContent.BACKFILLERS, getBlockState().getBlock()));
    }

    @Override
    protected Block casing() {
        return level() == 0 ? MultiContent.SOLID_STEEL_CASING.get() : MultiContent.STABLE_TITANIUM_CASING.get();
    }

    @Override
    protected Block frame() {
        return MultiContent.FRAMES.get(level() == 0 ? "steel" : "titanium").get();
    }

    @Override
    protected int minTier() {
        return MIN_TIER[level()];
    }

    /** GT getRadius: засыпщик — 16; улучшенный — 64 (настройка схемами через люк доступа к данным не перенесена). */
    public int radius() {
        return level() == 0 ? 16 : 64;
    }

    @Override
    protected boolean checkHatches() {
        return !parts.maintenance.isEmpty() && !parts.inputHatches.isEmpty() && !parts.energy.isEmpty();
    }

    @Override
    protected boolean allowPutPipesToController() {
        return true;
    }

    @Override
    protected void setElectricityStats(ServerLevel sl) {
        int tier = voltageTier(sl);
        setCycle(6 * (1 << (tier << 1)), (workState == STATE_UPWARD ? 240 : 80) / (1 << tier));
    }

    /** GT workingUpward: залить пустоту под наконечником, потом на слое, потом поднять трубу. */
    @Override
    protected boolean workingUpward(ServerLevel sl, int oldYHead) {
        BlockPos c = pipeColumn();
        BlockPos below = c.atY(yHead - 1);
        if (refillable(sl, below)) return refill(sl, below);
        int r = radius();
        if (lastX == 0 && lastZ == 0) {
            lastX = -r;
            lastZ = -r;
        }
        if (yHead != worldPosition.getY()) {
            for (int i = lastX; i <= r; i++) {
                for (int j = i == lastX ? lastZ : -r; j <= r; j++) {
                    BlockPos p = new BlockPos(c.getX() + i, yHead, c.getZ() + j);
                    if (refillable(sl, p)) {
                        lastX = i;
                        lastZ = j;
                        return refill(sl, p);
                    }
                }
            }
        }
        if (tryPickPipe(sl)) {
            lastX = lastZ = 0;
            return true;
        }
        workState = STATE_DOWNWARD;
        stop(true);
        return false;
    }

    private static boolean refillable(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).isAir();
    }

    private boolean refill(ServerLevel sl, BlockPos p) {
        if (!depleteInput(sl, GtFluids.get("molten_concrete").source().get(), CONCRETE_PER_BLOCK)) {
            maxProgress = 0;
            return false;
        }
        sl.setBlock(p, MultiContent.LIGHT_CONCRETE.get().defaultBlockState(), 3);
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("lastX", lastX);
        out.putInt("lastZ", lastZ);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        lastX = in.getIntOr("lastX", 0);
        lastZ = in.getIntOr("lastZ", 0);
    }
}
