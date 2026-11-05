package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.ElectricBlockEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отпугиватель монстров (GT_MetaTileEntity_MonsterRepellent, GT_SpawnEventHandler, D109): не даёт мобам появляться в кубе вокруг себя. Без энергии
 * радиус 4 + 12 × T, при запасе от 512 EU (4^T EU/t) — 16 + 48 × T. Режимы: враждебные; ещё и нейтральные; все.
 */
public class MonsterRepellentBlockEntity extends ElectricBlockEntity {
    public enum Mode { HOSTILES, HOSTILES_NEUTRALS, EVERYONE }

    private static final Set<MonsterRepellentBlockEntity> ACTIVE = ConcurrentHashMap.newKeySet();

    private final int tier;
    private int range;
    public Mode mode = Mode.HOSTILES;
    public boolean allowed = true;

    public MonsterRepellentBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.MONSTER_REPELLENT_BE.get(), pos, state, 0, 512 + tier(state).voltage() * 50, tier(state).voltage());
        this.tier = tier(state).tier();
        this.range = 4 + 12 * this.tier;
        energy.maxAmperesIn = 2;
    }

    private static VoltageTier tier(BlockState s) {
        return s.getBlock() instanceof MonsterRepellentBlock b ? b.tier : VoltageTier.LV;
    }

    public int range() {
        return range;
    }

    public void screwdriver(Player player) {
        int n = Mode.values().length;
        mode = Mode.values()[Math.floorMod(mode.ordinal() + (player.isShiftKeyDown() ? -1 : 1), n)];
        player.sendOverlayMessage(Component.translatable("message.iis.mobrep." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        setChanged();
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return true;
    }

    @Override
    public long minimumStored() {
        return 512;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public boolean allowedToWork() {
        return allowed;
    }

    @Override
    public void setAllowedToWork(boolean a) {
        allowed = a;
        setChanged();
    }

    @Override
    protected void work(ServerLevel sl) {
        if (!allowed) {
            ACTIVE.remove(this);
            return;
        }
        ACTIVE.add(this);
        long cost = 1L << (tier * 2);
        if (energy.stored >= 512 && energy.stored >= cost) {
            energy.stored -= cost;
            range = 16 + 48 * tier;
        } else {
            range = 4 + 12 * tier;
        }
    }

    @Override
    public void setRemoved() {
        ACTIVE.remove(this);
        super.setRemoved();
    }

    /** GT_SpawnEventHandler#denyMobSpawn. */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        Level level = mob.level();
        if (mob instanceof TamableAnimal t && t.isTame()) return;
        EntityType<?> type = mob.getType();
        boolean neutral = type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.OCELOT || type == EntityType.BAT || type == EntityType.SQUID || type == EntityType.WOLF;
        boolean friendly = type.getCategory() == MobCategory.CREATURE;
        for (MonsterRepellentBlockEntity r : ACTIVE) {
            if (r.isRemoved() || r.getLevel() != level) continue;
            double dx = Math.abs(r.worldPosition.getX() + 0.5 - event.getX()), dy = Math.abs(r.worldPosition.getY() + 0.5 - event.getY()), dz = Math.abs(r.worldPosition.getZ() + 0.5 - event.getZ());
            if (dx > r.range || dy > r.range || dz > r.range) continue;
            if (r.denies(neutral, friendly)) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                return;
            }
        }
    }

    /** Запрещает ли режим появление моба этого вида. */
    public boolean denies(boolean neutral, boolean friendly) {
        return (mode != Mode.HOSTILES && neutral) || mode == Mode.EVERYONE || (!friendly && !neutral);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("mode", mode.ordinal());
        out.putBoolean("allowed", allowed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        mode = Mode.values()[Math.floorMod(in.getIntOr("mode", 0), Mode.values().length)];
        allowed = in.getBooleanOr("allowed", true);
    }
}
