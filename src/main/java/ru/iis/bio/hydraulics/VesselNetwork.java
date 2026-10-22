package ru.iis.bio.hydraulics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.block.ColonyCoreBlockEntity;
import ru.iis.bio.block.LivingTankBlockEntity;
import ru.iis.bio.block.MusclePumpBlockEntity;
import ru.iis.bio.block.StationBlockEntity;
import ru.iis.bio.block.VesselBlock;
import ru.iis.bio.block.VesselBlockEntity;
import ru.iis.bio.BioSounds;

import java.util.*;

/**
 * Решатель сети сосудов Био, первый вариант (спецификация v0.7 §20.1–§20.4, §20.11, D73). Раз в {@link #STEP} тиков:
 * <ol>
 *   <li>Сеть — связные участки (сосуды, клапаны, зажимы). Одна жидкость на сеть; участки с другой — пробка.</li>
 *   <li>Насосы: ядро колонии вплотную к участку (ULV: H0 0,050 атм, Q 16 мБ/т) и мышечный насос выходом в сеть (LV: 0,200, 128).
 *       Без насоса жидкость только стекает вниз (0,0968 атм на блок).</li>
 *   <li>Путь к каждому приёмнику — кратчайший по сопротивлению (Σ R × вязкость/вязкость N0), с учётом высоты, порогов клапанов,
 *       узкого места Q_cap, предела порта (ULV 4, LV 16 мБ/т), свободного места.</li>
 *   <li>Общий расход Q ищется так, чтобы напор насоса H(Q) совпал с суммой потоков ветвей; дерево путей даёт расход через каждый участок
 *       и давление на стенку {@code p = H − Σ R·Q + 0,0968·Δz}.</li>
 *   <li>Давление сверх безопасного копит износ D, на разрывном участок лопается; износ ≥ 60 течёт; голод — износ.</li>
 * </ol>
 * Упрощения первого решателя: насос работает целиком или стоит (без промежуточной обеспеченности a); пульсации и гидроудар не считаются;
 * объём сети — общий пул, равномерно разлитый по участкам.
 */
public final class VesselNetwork {
    private VesselNetwork() {}

    public static final int STEP = 5;
    /** Перепад на подъём 1 блока, атм (ρ = 1000 кг/м³, g = 9,80665, §2). */
    public static final double RHO_G = 0.0968;
    private static final int MAX_MEMBERS = 1024;
    /** Вязкость эталонной среды N0 (BioFluids): сопротивление умножается на μ/μ_N0. */
    private static final double MU_N0 = 1100;

    /** Сводка сети для ПКМ по любому участку. */
    public record Summary(int members, int volume, int amount, Fluid fluid, List<Component> pumps, double head, double flow, int sinks) {
        public Component describe() {
            Component f = fluid == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : fluid.getFluidType().getDescription();
            Component pumpText = pumps.isEmpty() ? Component.translatable("message.iis.bio.network.no_pump") : pumps.getFirst();
            return Component.translatable("message.iis.bio.network", members, f, amount, volume, pumpText, String.format(Locale.ROOT, "%.4f", head),
                    String.format(Locale.ROOT, "%.2f", flow), sinks);
        }
    }

    private record Sink(VesselBlockEntity member, Direction side, ResourceHandler<FluidResource> handler, double cap, double pLimit, @Nullable BlockEntity owner) {
        /** Высота самого приёмника (жидкость стекает в него на блок ниже, если он снизу). */
        int y() {
            return member.getBlockPos().getY() + side.getStepY();
        }
    }

    private record Attach(VesselBlockEntity member, Pump pump) {}

    public static void solve(ServerLevel level, VesselBlockEntity start) {
        long now = level.getGameTime();
        // --- 1. участки ------------------------------------------------------------------------------------------------
        Map<BlockPos, VesselBlockEntity> members = new LinkedHashMap<>();
        ArrayDeque<VesselBlockEntity> queue = new ArrayDeque<>();
        members.put(start.getBlockPos(), start);
        queue.add(start);
        while (!queue.isEmpty() && members.size() < MAX_MEMBERS) {
            VesselBlockEntity m = queue.poll();
            BlockState s = m.getBlockState();
            for (Direction d : Direction.values()) {
                if (!s.getValue(VesselBlock.PROPS.get(d))) continue;
                BlockPos n = m.getBlockPos().relative(d);
                if (members.containsKey(n)) continue;
                if (level.getBlockEntity(n) instanceof VesselBlockEntity o && o.getBlockState().getValue(VesselBlock.PROPS.get(d.getOpposite()))) {
                    members.put(n, o);
                    queue.add(o);
                }
            }
        }
        for (VesselBlockEntity m : members.values()) m.solvedAt = now;

        // --- 2. жидкость сети, поддержание ----------------------------------------------------------------------------
        Map<Fluid, Integer> byFluid = new HashMap<>();
        for (VesselBlockEntity m : members.values()) if (m.tank().amount() > 0) byFluid.merge(m.tank().fluid(), m.tank().amount(), Integer::sum);
        Fluid fluid = byFluid.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Fluids.EMPTY);
        ColonyCoreBlockEntity colony = ColonyCoreBlockEntity.findOwnerByTerritory(level, start.getBlockPos());
        Set<VesselBlockEntity> blocked = new HashSet<>();
        int volume = 0;
        for (VesselBlockEntity m : members.values()) {
            m.upkeep(colony != null && colony.claims(m.getBlockPos()) ? colony : null, STEP);
            boolean foreign = m.tank().amount() > 0 && m.tank().fluid() != fluid;
            if (m.starving() || foreign || Double.isInfinite(m.block().resistance(m.getBlockState()))) blocked.add(m);
            if (!foreign) volume += m.tank().capacity();
            m.networkFluid = fluid;
        }
        int pool = pool(members.values(), fluid, blocked);
        if (pool == 0) members.values().forEach(VesselBlockEntity::clearSources);
        double mu = fluid == Fluids.EMPTY ? 1 : Math.max(0.1, fluid.getFluidType().getViscosity() / MU_N0);

        // --- 3. насосы и приёмники ------------------------------------------------------------------------------------
        List<Attach> pumps = new ArrayList<>();
        List<Sink> sinks = new ArrayList<>();
        for (VesselBlockEntity m : members.values()) {
            if (blocked.contains(m)) continue;
            BlockState s = m.getBlockState();
            VesselBlock vb = m.block();
            for (Direction d : Direction.values()) {
                if (!s.getValue(VesselBlock.PROPS.get(d))) continue;
                BlockPos n = m.getBlockPos().relative(d);
                if (members.containsKey(n)) continue;
                BlockEntity be = level.getBlockEntity(n);
                if (be instanceof ColonyCoreBlockEntity core) {
                    if (vb.entry(s, d)) pumps.add(new Attach(m, corePump(core)));
                    continue;
                }
                if (be instanceof MusclePumpBlockEntity mp) {
                    if (mp.outlet() == d.getOpposite() && vb.entry(s, d)) pumps.add(new Attach(m, mp));
                    continue;
                }
                if (be instanceof LivingTankBlockEntity lt && lt.supplying()) continue;
                // универсальный резервуар над сосудом сам сливает в него — это источник, не приёмник
                if (be instanceof ru.iis.fluid.UniversalTankBlockEntity && d == Direction.UP) continue;
                if (!vb.exit(s, d) || m.fromSource(d) || fluid == Fluids.EMPTY) continue;
                ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, n, d.getOpposite());
                if (h == null) continue;
                double cap = 128, limit = Double.POSITIVE_INFINITY;
                if (be instanceof StationBlockEntity st) {
                    boolean lv = st.logic.station.lv();
                    cap = lv ? 16 : 4;       // §21.4: трубопроводная загрузка входа ULV 4, LV 16 мБ/т
                    limit = lv ? 0.200 : 0.050; // и предел давления порта (защитный дозатор закрывается)
                } else if (be instanceof LivingTankBlockEntity) {
                    cap = 128;
                }
                sinks.add(new Sink(m, d, h, cap, limit, be));
            }
        }
        pumps.removeIf(a -> !a.pump().canRun(STEP));

        // --- 4. пути: кратчайшие по сопротивлению от насосов (или от верха столба без насоса) ----------------------------
        int zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (VesselBlockEntity m : members.values()) {
            zMin = Math.min(zMin, m.getBlockPos().getY());
            zMax = Math.max(zMax, m.getBlockPos().getY());
        }
        double fill = volume > 0 ? (double) pool / volume : 0;
        double zLevel = zMin - 0.5 + fill * (zMax - zMin + 1); // свободная поверхность при равномерном наливе
        Map<VesselBlockEntity, Double> dist = new HashMap<>();
        Map<VesselBlockEntity, Double> thr = new HashMap<>();
        Map<VesselBlockEntity, Double> bottleneck = new HashMap<>();
        Map<VesselBlockEntity, Double> zRef = new HashMap<>();
        Map<VesselBlockEntity, VesselBlockEntity> parent = new HashMap<>();
        List<VesselBlockEntity> order = new ArrayList<>();
        PriorityQueue<VesselBlockEntity> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        boolean pumped = !pumps.isEmpty();
        if (pumped) {
            for (Attach a : pumps) {
                VesselBlockEntity m = a.member();
                double r = mu * m.block().resistance(m.getBlockState());
                if (r < dist.getOrDefault(m, Double.POSITIVE_INFINITY)) {
                    dist.put(m, r);
                    thr.put(m, m.block().threshold(m.getBlockState()));
                    bottleneck.put(m, (double) m.spec().flow());
                    zRef.put(m, (double) m.getBlockPos().getY());
                    pq.add(m);
                }
            }
        } else {
            for (VesselBlockEntity m : members.values()) {
                if (blocked.contains(m) || m.getBlockPos().getY() < zMax) continue;
                dist.put(m, mu * m.block().resistance(m.getBlockState()));
                thr.put(m, 0.0);
                bottleneck.put(m, (double) m.spec().flow());
                zRef.put(m, (double) zMax); // течение без насоса — только вниз по высоте блоков
                pq.add(m);
            }
        }
        Set<VesselBlockEntity> done = new HashSet<>();
        while (!pq.isEmpty()) {
            VesselBlockEntity u = pq.poll();
            if (!done.add(u)) continue;
            order.add(u);
            BlockState us = u.getBlockState();
            for (Direction d : Direction.values()) {
                if (!u.block().exit(us, d)) continue;
                VesselBlockEntity v = members.get(u.getBlockPos().relative(d));
                if (v == null || blocked.contains(v) || done.contains(v) || !v.block().entry(v.getBlockState(), d.getOpposite())) continue;
                double nd = dist.get(u) + mu * v.block().resistance(v.getBlockState());
                if (nd < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    dist.put(v, nd);
                    thr.put(v, thr.get(u) + v.block().threshold(v.getBlockState()));
                    bottleneck.put(v, Math.min(bottleneck.get(u), v.spec().flow()));
                    zRef.put(v, zRef.get(u));
                    parent.put(v, u);
                    pq.add(v);
                }
            }
        }

        // --- 5. расход: H(Q) насосов = Σ ветвей ------------------------------------------------------------------------
        double h0 = 0, qFree = 0;
        for (Attach a : pumps) {
            h0 = Math.max(h0, a.pump().h0());
            qFree += a.pump().qFree();
        }
        List<Sink> reach = new ArrayList<>();
        List<Double> room = new ArrayList<>();
        for (Sink sk : sinks) {
            if (!dist.containsKey(sk.member())) continue;
            int r;
            try (Transaction tx = Transaction.openRoot()) {
                r = sk.handler().insert(FluidResource.of(fluid), (int) Math.ceil(sk.cap() * STEP), tx);
            }
            if (r <= 0) continue;
            reach.add(sk);
            room.add((double) r / STEP);
        }
        final double fh0 = h0, fq = qFree;
        double[] q = new double[reach.size()];
        double qTot = 0, head = 0;
        if (pool > 0 || pumps.stream().anyMatch(a -> a.pump().inlet() != null)) {
            if (pumped) {
                double lo = 0, hi = qFree;
                for (int it = 0; it < 40; it++) {
                    double mid = (lo + hi) / 2;
                    double hh = fh0 * Math.max(0, 1 - mid / fq);
                    double sum = branches(reach, room, dist, thr, bottleneck, zRef, hh, q);
                    if (sum > mid) lo = mid;
                    else hi = mid;
                }
                qTot = lo;
                head = fh0 * Math.max(0, 1 - qTot / fq);
                qTot = branches(reach, room, dist, thr, bottleneck, zRef, head, q);
            } else {
                qTot = branches(reach, room, dist, thr, bottleneck, zRef, 0, q);
            }
        }

        // --- 6. подача насосов с входом, ограничение пулом ----------------------------------------------------------------
        int fillRoom = volume - pool;
        for (Attach a : pumps) {
            ResourceHandler<FluidResource> in = a.pump().inlet();
            if (in == null) continue;
            int want = (int) Math.min(fillRoom + qTot * STEP, a.pump().qFree() * STEP);
            if (want <= 0) continue;
            int got = pullInto(in, members.values(), blocked, fluid, want);
            if (got > 0 && fluid == Fluids.EMPTY) fluid = firstFluid(members.values());
            pool += got;
            fillRoom -= got;
        }
        double deliverable = pool;
        if (qTot * STEP > deliverable && qTot > 0) {
            double k = deliverable / (qTot * STEP);
            for (int i = 0; i < q.length; i++) q[i] *= k;
            qTot *= k;
        }
        for (Attach a : pumps) a.pump().charge(head, qTot / Math.max(1, pumps.size()), STEP);

        // --- 7. давление и износ по дереву путей ----------------------------------------------------------------------
        Map<VesselBlockEntity, Double> through = new HashMap<>();
        for (int i = 0; i < reach.size(); i++) {
            VesselBlockEntity m = reach.get(i).member();
            while (m != null) {
                through.merge(m, q[i], Double::sum);
                m = parent.get(m);
            }
        }
        Map<VesselBlockEntity, Double> drop = new HashMap<>();
        Map<VesselBlockEntity, Double> pressure = new HashMap<>();
        double href = pumped ? (pool > 0 ? (qTot > 0 ? head : h0) : 0) : 0;
        for (VesselBlockEntity m : order) {
            double qm = through.getOrDefault(m, 0.0);
            double own = mu * m.block().resistance(m.getBlockState()) * qm;
            VesselBlockEntity par = parent.get(m);
            double dp = (par == null ? 0 : drop.get(par)) + own;
            drop.put(m, dp);
            double z = pumped ? zRef.get(m) : zLevel; // стенку нагружает столб от свободной поверхности
            pressure.put(m, pool > 0 ? href - dp + RHO_G * (z - m.getBlockPos().getY()) : 0);
        }
        List<VesselBlockEntity> burst = new ArrayList<>();
        double leak = 0;
        for (VesselBlockEntity m : members.values()) {
            double p = pressure.getOrDefault(m, pool > 0 && fill > 0 ? Math.max(0, RHO_G * (zLevel - m.getBlockPos().getY())) : 0);
            m.pressure = p;
            m.flow = through.getOrDefault(m, 0.0);
            if (m.load(p, STEP)) burst.add(m);
            leak += m.leak(p) * STEP;
            if (p > m.pSafe() && level.getRandom().nextInt(4) == 0) {
                BlockPos bp = m.getBlockPos();
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, bp.getX() + 0.5, bp.getY() + 0.3, bp.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0);
            }
            m.setChanged();
        }
        // переполненный живой бак тоже лопается
        for (Sink sk : reach) {
            if (sk.owner() instanceof LivingTankBlockEntity lt && pressure.getOrDefault(sk.member(), 0.0) >= LivingTankBlockEntity.P_BURST) lt.rupture();
        }

        // --- 8. перенос ----------------------------------------------------------------------------------------------
        FluidResource res = FluidResource.of(fluid);
        int moved = 0;
        for (int i = 0; i < reach.size(); i++) {
            Sink sk = reach.get(i);
            if (pressure.getOrDefault(sk.member(), 0.0) > sk.pLimit()) continue;
            int amount = (int) Math.round(q[i] * STEP);
            if (amount <= 0 || fluid == Fluids.EMPTY) continue;
            int have = pool(members.values(), fluid, blocked) ;
            amount = Math.min(amount, have);
            if (amount <= 0) break;
            int put;
            try (Transaction tx = Transaction.openRoot()) {
                put = sk.handler().insert(res, amount, tx);
                tx.commit();
            }
            if (put > 0) {
                take(members.values(), blocked, fluid, put);
                moved += put;
            }
        }
        if (leak >= 1) take(members.values(), blocked, fluid, (int) leak);
        spread(members.values(), blocked, fluid);

        // --- 9. итоги и разрывы ---------------------------------------------------------------------------------------
        List<Component> pumpNames = new ArrayList<>();
        for (Attach a : pumps) pumpNames.add(a.pump().name());
        Summary sum = new Summary(members.size(), volume, pool(members.values(), fluid, blocked), fluid, pumpNames, href, qTot, reach.size());
        for (VesselBlockEntity m : members.values()) m.summary = sum;
        for (VesselBlockEntity m : burst) {
            BlockPos bp = m.getBlockPos();
            level.playSound(null, bp, BioSounds.TEAR, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.SPLASH, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.2);
            level.destroyBlock(bp, false);
        }
    }

    /** Сумма ветвей при напоре h; заполняет q[i]. */
    private static double branches(List<Sink> reach, List<Double> room, Map<VesselBlockEntity, Double> dist, Map<VesselBlockEntity, Double> thr,
                                   Map<VesselBlockEntity, Double> bottleneck, Map<VesselBlockEntity, Double> zRef, double h, double[] q) {
        double sum = 0;
        for (int i = 0; i < reach.size(); i++) {
            Sink sk = reach.get(i);
            VesselBlockEntity m = sk.member();
            double drive = h + RHO_G * (zRef.get(m) - sk.y()) - thr.get(m);
            double r = Math.max(1e-9, dist.get(m));
            double qi = drive <= 0 ? 0 : drive / r;
            qi = Math.min(qi, Math.min(sk.cap(), Math.min(bottleneck.get(m), room.get(i))));
            q[i] = qi;
            sum += qi;
        }
        return sum;
    }

    private static Pump corePump(ColonyCoreBlockEntity core) {
        return new Pump() {
            @Override
            public double h0() {
                return 0.050;
            }

            @Override
            public double qFree() {
                return 16;
            }

            /** §20.3: c_N_work = 0,010 + 0,020 × (H×Q)/(0,0375×4) мБ N0/т из запаса колонии. */
            private double cost(double h, double q) {
                return 0.010 + 0.020 * (h * q) / (0.0375 * 4);
            }

            @Override
            public boolean canRun(int ticks) {
                return core.colony().nutrition() >= cost(0.0375, 8) * ticks;
            }

            @Override
            public void charge(double h, double q, int ticks) {
                core.drainN0(cost(h, q) * ticks);
            }

            @Override
            public Component name() {
                return Component.translatable("message.iis.bio.network.pump.core");
            }
        };
    }

    private static int pool(Collection<VesselBlockEntity> members, Fluid fluid, Set<VesselBlockEntity> blocked) {
        int n = 0;
        for (VesselBlockEntity m : members) if (m.tank().fluid() == fluid && !blocked.contains(m)) n += m.tank().amount();
        return n;
    }

    private static void take(Collection<VesselBlockEntity> members, Set<VesselBlockEntity> blocked, Fluid fluid, int amount) {
        for (VesselBlockEntity m : members) {
            if (amount <= 0) return;
            if (blocked.contains(m)) continue;
            amount -= m.tank().drain(fluid, amount, false);
        }
    }

    private static int pullInto(ResourceHandler<FluidResource> in, Collection<VesselBlockEntity> members, Set<VesselBlockEntity> blocked, Fluid fluid, int want) {
        int got = 0;
        for (int i = 0; i < in.size() && got < want; i++) {
            FluidResource r = in.getResource(i);
            if (r.isEmpty() || (fluid != Fluids.EMPTY && r.getFluid() != fluid)) continue;
            Fluid f = r.getFluid();
            int room = 0;
            for (VesselBlockEntity m : members) if (!blocked.contains(m)) room += m.tank().fill(f, Integer.MAX_VALUE, true);
            int n = Math.min(want - got, room);
            if (n <= 0) continue;
            int ex;
            try (Transaction tx = Transaction.openRoot()) {
                ex = in.extract(r, n, tx);
                tx.commit();
            }
            int left = ex;
            for (VesselBlockEntity m : members) {
                if (left <= 0) break;
                if (blocked.contains(m)) continue;
                left -= m.tank().fill(f, left, false);
            }
            got += ex - left;
            fluid = f;
        }
        return got;
    }

    private static Fluid firstFluid(Collection<VesselBlockEntity> members) {
        for (VesselBlockEntity m : members) if (m.tank().amount() > 0) return m.tank().fluid();
        return Fluids.EMPTY;
    }

    /** Разлить пул поровну по объёму участков (без пробок). */
    private static void spread(Collection<VesselBlockEntity> members, Set<VesselBlockEntity> blocked, Fluid fluid) {
        if (fluid == Fluids.EMPTY) return;
        int total = 0, vol = 0;
        List<VesselBlockEntity> live = new ArrayList<>();
        for (VesselBlockEntity m : members) {
            if (blocked.contains(m) || (m.tank().amount() > 0 && m.tank().fluid() != fluid)) continue;
            live.add(m);
            total += m.tank().amount();
            vol += m.tank().capacity();
        }
        if (vol == 0) return;
        int left = total;
        for (int i = 0; i < live.size(); i++) {
            VesselBlockEntity m = live.get(i);
            int share = i == live.size() - 1 ? left : (int) ((long) total * m.tank().capacity() / vol);
            share = Math.min(share, m.tank().capacity());
            m.tank().setRaw(fluid, share);
            left -= share;
        }
        // остаток (округление) — в первый, где есть место
        for (VesselBlockEntity m : live) {
            if (left <= 0) break;
            int add = Math.min(left, m.tank().capacity() - m.tank().amount());
            if (add > 0) {
                m.tank().setRaw(fluid, m.tank().amount() + add);
                left -= add;
            }
        }
    }
}
