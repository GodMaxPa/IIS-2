package ru.iis.rail.cart;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.rail.RailSpeed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сцепка вагонеток в поезд (Railcraft {@code LinkageManager}/{@code LinkageHandler}, D102). У вагонетки две сцепки A и B,
 * сцепляются вагонетки не дальше 2 × 1.25 блока. Каждый тик пары держатся на 2 × 0.78 блока пружиной (жёсткость 0.7)
 * с демпфированием (0.4, на высокой скорости 0.3), сила не больше 6; дальше 8 блоков сцепка рвётся.
 * Хранится в данных вагонетки (UUID соседей), поэтому переживает перезапуск.
 */
public final class TrainLinks {
    private TrainLinks() {}

    /** ILinkageManager.LINKAGE_DISTANCE / OPTIMAL_DISTANCE (API Railcraft, MIT). */
    public static final double LINKAGE_DISTANCE = 1.25, OPTIMAL_DISTANCE = 0.78;
    static final double MAX_DISTANCE = 8, STIFFNESS = 0.7, DAMPING = 0.4, HS_DAMPING = 0.3, FORCE_LIMIT = 6;
    static final String[] SLOTS = {"iis_link_a", "iis_link_b"};
    public static final String AUTO_LINK = "iis_autolink";

    public static @Nullable UUID link(AbstractMinecart cart, int slot) {
        String s = cart.getPersistentData().getStringOr(SLOTS[slot], "");
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<UUID> links(AbstractMinecart cart) {
        List<UUID> out = new ArrayList<>(2);
        for (int i = 0; i < SLOTS.length; i++) {
            UUID u = link(cart, i);
            if (u != null) out.add(u);
        }
        return out;
    }

    public static boolean hasFreeLink(AbstractMinecart cart) {
        return link(cart, 0) == null || link(cart, 1) == null;
    }

    public static boolean areLinked(AbstractMinecart a, AbstractMinecart b) {
        return links(a).contains(b.getUUID()) && links(b).contains(a.getUUID());
    }

    /** LinkageManager#canLinkCarts + createLink. */
    public static boolean createLink(AbstractMinecart a, AbstractMinecart b) {
        if (a == b || a.level() != b.level() || areLinked(a, b) || !hasFreeLink(a) || !hasFreeLink(b)) return false;
        double max = 2 * LINKAGE_DISTANCE;
        if (a.distanceToSqr(b) > max * max) return false;
        put(a, b.getUUID());
        put(b, a.getUUID());
        return true;
    }

    private static void put(AbstractMinecart cart, UUID other) {
        CompoundTag d = cart.getPersistentData();
        for (String slot : SLOTS) {
            if (d.getStringOr(slot, "").isEmpty()) {
                d.putString(slot, other.toString());
                return;
            }
        }
    }

    private static void remove(AbstractMinecart cart, UUID other) {
        CompoundTag d = cart.getPersistentData();
        for (String slot : SLOTS) if (other.toString().equals(d.getStringOr(slot, ""))) d.remove(slot);
    }

    public static void breakLink(AbstractMinecart a, AbstractMinecart b) {
        remove(a, b.getUUID());
        remove(b, a.getUUID());
    }

    public static void breakAll(ServerLevel level, AbstractMinecart cart) {
        for (UUID u : links(cart)) {
            if (level.getEntity(u) instanceof AbstractMinecart other) remove(other, cart.getUUID());
            remove(cart, u);
        }
    }

    public static void setAutoLink(AbstractMinecart cart, boolean on) {
        if (on && hasFreeLink(cart)) cart.getPersistentData().putBoolean(AUTO_LINK, true);
        else if (!on) cart.getPersistentData().remove(AUTO_LINK);
    }

    /** Раз в тик вагонетки: автосцепка, затем физика пар (каждая пара — один раз, со стороны меньшего UUID). */
    public static void tick(ServerLevel level, AbstractMinecart cart) {
        if (cart.getPersistentData().getBooleanOr(AUTO_LINK, false)) tryAutoLink(level, cart);
        for (int i = 0; i < SLOTS.length; i++) {
            UUID u = link(cart, i);
            if (u == null) continue;
            Entity e = level.getEntity(u);
            if (e == null) continue;   // сосед в незагруженном чанке — сцепка ждёт
            if (!(e instanceof AbstractMinecart other) || other.isRemoved()) {
                cart.getPersistentData().remove(SLOTS[i]);
                continue;
            }
            if (cart.getUUID().compareTo(u) < 0) adjust(cart, other);
        }
    }

    private static void tryAutoLink(ServerLevel level, AbstractMinecart cart) {
        double r = 2 * LINKAGE_DISTANCE;
        for (AbstractMinecart other : level.getEntitiesOfClass(AbstractMinecart.class, cart.getBoundingBox().inflate(r), c -> c != cart)) {
            if (createLink(cart, other)) {
                cart.getPersistentData().remove(AUTO_LINK);
                other.getPersistentData().remove(AUTO_LINK);
                return;
            }
        }
    }

    /** LinkageHandler#adjustVelocity: пружина и демпфер по плоскости XZ. */
    static void adjust(AbstractMinecart a, AbstractMinecart b) {
        double dist = a.distanceTo(b);
        if (dist > MAX_DISTANCE) {
            breakLink(a, b);
            return;
        }
        double ux = b.getX() - a.getX(), uz = b.getZ() - a.getZ();
        double len = Math.sqrt(ux * ux + uz * uz);
        if (len < 1.0e-6) return;
        ux /= len;
        uz /= len;
        double stretch = dist - 2 * OPTIMAL_DISTANCE;
        double sx = limit(STIFFNESS * stretch * ux), sz = limit(STIFFNESS * stretch * uz);
        Vec3 va = a.getDeltaMovement().add(sx, 0, sz), vb = b.getDeltaMovement().subtract(sx, 0, sz);
        double dot = (vb.x - va.x) * ux + (vb.z - va.z) * uz;
        double damping = RailSpeed.isHighSpeed(a) ? HS_DAMPING : DAMPING;
        double dx = limit(damping * dot * ux), dz = limit(damping * dot * uz);
        a.setDeltaMovement(va.add(dx, 0, dz));
        b.setDeltaMovement(vb.subtract(dx, 0, dz));
    }

    private static double limit(double f) {
        return Math.copySign(Math.min(Math.abs(f), FORCE_LIMIT), f);
    }
}
