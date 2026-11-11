package ru.iis.rail.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.FluidTank;
import ru.iis.rail.RailContent;

/**
 * Водонапорная башня Railcraft ({@code TileTankWater}, D146): полый куб 3×3×3 из 26 блоков обшивки. Главный — центр нижнего слоя:
 * раз в 8 тиков набирает 10 л × погоду биома (без осадков 0, снег ½, дождь 1), в дождь втрое больше, без неба над крышей вдвое меньше
 * (REFILL_RATE 10, REFILL_INTERVAL 8, REFILL_BOOST_RAIN 3, REFILL_PENALTY_INSIDE 0.5, REFILL_PENALTY_SNOW 0.5). Бак 400 вёдер; вода
 * отдаётся вниз из-под нижнего слоя и забирается трубой из любого блока обшивки.
 */
public class WaterTankBlockEntity extends BlockEntity {
    public static final int CAPACITY = 400_000, INTERVAL = 8, RATE = 10, PUSH = 1000;
    private final FluidTank tank = new FluidTank(CAPACITY, f -> f.isSame(Fluids.WATER), true, this::setChanged);
    private @Nullable BlockPos master;
    private long masterCheckedAt = -1000;   // не Long.MIN_VALUE: разность с временем мира переполнялась

    public WaterTankBlockEntity(BlockPos pos, BlockState state) {
        super(RailContent.WATER_TANK_BE.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    static boolean siding(Level level, BlockPos p) {
        return level.getBlockState(p).is(RailContent.WATER_TANK_SIDING.get());
    }

    /** Центр нижнего слоя целого куба? */
    public static boolean isMaster(Level level, BlockPos p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos q = p.offset(dx, dy, dz);
                    if (dx == 0 && dy == 1 && dz == 0) {
                        if (!level.getBlockState(q).isAir()) return false;
                    } else if (!siding(level, q)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Главный блок куба, в который входит этот блок; null — куба нет. Проверка раз в 40 тиков. */
    public @Nullable WaterTankBlockEntity master() {
        Level level = getLevel();
        if (level == null) return null;
        if (level.getGameTime() - masterCheckedAt >= 40) {
            masterCheckedAt = level.getGameTime();
            master = null;
            search:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -2; dy <= 0; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos c = worldPosition.offset(dx, dy, dz);
                        if (isMaster(level, c)) {
                            master = c;
                            break search;
                        }
                    }
                }
            }
        }
        return master != null && level.getBlockEntity(master) instanceof WaterTankBlockEntity m ? m : null;
    }

    public @Nullable ResourceHandler<FluidResource> handler(@Nullable Direction side) {
        WaterTankBlockEntity m = master();
        return m == null ? null : m.tank;
    }

    /** Литров за один набор (раз в 8 тиков) при такой погоде. */
    public static int refill(Biome.Precipitation precipitation, boolean raining, boolean seesSky) {
        double r = RATE * switch (precipitation) {
            case NONE -> 0.0;
            case SNOW -> 0.5;
            case RAIN -> 1.0;
        };
        if (raining) r *= 3.0;
        if (!seesSky) r *= 0.5;
        return (int) Math.floor(r);
    }

    public void serverTick() {
        Level level = getLevel();
        if (level == null || level.getGameTime() % INTERVAL != 0 || !isMaster(level, worldPosition)) return;
        BlockPos top = worldPosition.above(3);
        Biome.Precipitation pr = level.getBiome(top).value().getPrecipitationAt(top, level.getSeaLevel());
        int add = refill(pr, level.isRainingAt(top), level.canSeeSky(top));
        if (add > 0) tank.setAmount(Fluids.WATER, tank.amount() + add);
        if (level.getGameTime() % 20 == 0 && tank.amount() > 0) {
            for (int dx = -1; dx <= 1 && tank.amount() > 0; dx++) {
                for (int dz = -1; dz <= 1 && tank.amount() > 0; dz++) {
                    BlockPos below = worldPosition.offset(dx, -1, dz);
                    ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, below, Direction.UP);
                    if (target == null || level.getBlockEntity(below) instanceof WaterTankBlockEntity) continue;
                    try (Transaction tx = Transaction.openRoot()) {
                        int moved = target.insert(FluidResource.of(Fluids.WATER), Math.min(PUSH, tank.amount()), tx);
                        if (moved > 0) {
                            tank.drainInternal(moved);
                            tx.commit();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}
