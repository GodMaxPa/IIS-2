package ru.iis.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import ru.iis.equipment.Weapon;
import ru.iis.net.IISNetwork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Трассеры и лучи: короткие светящиеся отрезки, вспышка у дула, отдача своего оружия. */
public final class ShotFxClient {
    private ShotFxClient() {}

    private record Trace(Vec3 from, Vec3 to, int color, float width, int life, long start) {}

    private static final List<Trace> TRACES = new ArrayList<>();
    /** Откат ствола в руке, 1 в момент выстрела. */
    public static float kick, kickO;
    private static long ticks;

    public static void accept(IISNetwork.ShotFx fx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Weapon w = Weapon.values()[Math.floorMod(fx.weapon(), Weapon.values().length)];
        Vec3 from = new Vec3(fx.x0(), fx.y0(), fx.z0()), to = new Vec3(fx.x1(), fx.y1(), fx.z1());
        Entity shooter = mc.level.getEntity(fx.shooter());
        boolean mine = mc.player != null && shooter == mc.player;
        boolean hasTrace = from.distanceToSqr(to) >= 0.01;
        // свой выстрел: от дула там, где оно сейчас на экране (пакет приходит на тик позже, и сервер мог ещё не знать о прицеливании)
        if (mine) from = ru.iis.equipment.GunActions.muzzle(mc.player);
        if (fx.kick()) {
            if (mine) {
                kick = 1F;
                mc.player.setXRot(mc.player.getXRot() - w.recoil * (mc.player.isShiftKeyDown() ? 0.5F : 1F));
                mc.player.setYRot(mc.player.getYRot() + (mc.level.getRandom().nextFloat() - 0.5F) * w.recoil * 0.4F);
            }
            flash(mc, from, w);
        }
        if (!hasTrace) return;
        switch (w.kind) {
            case LASER -> {
                int c = w == Weapon.LASER_PISTOL ? 0xFFFF4030 : w == Weapon.LASER_RIFLE ? 0xFF50FF70 : 0xFF5090FF;
                float width = w == Weapon.LASER_CANNON ? 0.14F : 0.05F;
                TRACES.add(new Trace(from, to, c, width, w == Weapon.LASER_CANNON ? 8 : 4, ticks));
            }
            default -> TRACES.add(new Trace(from.add(to.subtract(from).normalize().scale(0.5)), to, 0xFFFFE6A0, 0.018F, 2, ticks));
        }
    }

    private static void flash(Minecraft mc, Vec3 at, Weapon w) {
        switch (w.kind) {
            case BULLET -> mc.level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
            case LASER -> mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0.02, 0);
            case PLASMA -> mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0, 0.02, 0);
            default -> mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        ticks++;
        kickO = kick;
        kick *= 0.6F;
        for (Iterator<Trace> it = TRACES.iterator(); it.hasNext(); ) {
            Trace t = it.next();
            if (ticks - t.start > t.life) it.remove();
        }
    }

    public static void submit(SubmitCustomGeometryEvent e) {
        if (TRACES.isEmpty()) return;
        PoseStack stack = e.getPoseStack();
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        List<Trace> copy = List.copyOf(TRACES);
        e.getSubmitNodeCollector().submitCustomGeometry(stack, RenderTypes.lightning(), (pose, vc) -> {
            for (Trace t : copy) {
                float fade = 1F - (ticks - t.start) / (float) (t.life + 1);
                beam(pose, vc, t.from.subtract(cam), t.to.subtract(cam), t.width, t.color, fade);
                if (t.width > 0.03F) beam(pose, vc, t.from.subtract(cam), t.to.subtract(cam), t.width * 0.4F, 0xFFFFFFFF, fade);
            }
        });
    }

    /** Два скрещённых четырёхугольника вдоль отрезка. */
    private static void beam(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float width, int color, float fade) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        d = d.normalize();
        Vec3 up = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(up).normalize().scale(width), s2 = d.cross(s1).normalize().scale(width);
        int alpha = (int) (((color >>> 24) & 255) * fade);
        int c = (alpha << 24) | (color & 0xFFFFFF);
        for (Vec3 s : new Vec3[]{s1, s2}) {
            vertex(pose, vc, a.add(s), c);
            vertex(pose, vc, a.subtract(s), c);
            vertex(pose, vc, b.subtract(s), c);
            vertex(pose, vc, b.add(s), c);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, int color) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }
}
