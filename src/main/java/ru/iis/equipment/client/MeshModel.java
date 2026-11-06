package ru.iis.equipment.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ru.iis.IIS;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель из четырёхугольников с цветом и материалом (D128): tools/gen/mesh_art.py пишет JSON, здесь он читается и рисуется.
 * Цвет на вершинах, поверх лежит серая текстура материала: так одним атласом закрашены все машины.
 */
public final class MeshModel {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(IIS.MODID, "textures/entity/vehicle/atlas.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(ATLAS), GLASS = RenderTypes.entityTranslucent(ATLAS);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int CELL = 64, ATLAS_SIZE = 256;
    private static final Map<String, MeshModel> CACHE = new HashMap<>();

    /** Положение анимированных частей на этот кадр. */
    public static final class Pose {
        /** Пройденный путь, блоков. */
        public float distance;
        /** Руль в градусах, башня и ствол в градусах, угол винтов в радианах. */
        public float steer, turretYaw, barrelPitch, spin;
    }

    /** Часть одного прохода: 13 float на вершину не нужны, вершины лежат подряд. */
    private record Batch(float[] pos, float[] uv, float[] normal, int[] color) {
        void emit(PoseStack.Pose pose, VertexConsumer out, int overlay, int light) {
            int quads = pos.length / 12;
            for (int q = 0; q < quads; q++) {
                float nx = normal[q * 3], ny = normal[q * 3 + 1], nz = normal[q * 3 + 2];
                for (int v = 0; v < 4; v++) {
                    int p = q * 12 + v * 3, t = q * 8 + v * 2;
                    out.addVertex(pose, pos[p], pos[p + 1], pos[p + 2]).setColor(color[q * 4 + v]).setUv(uv[t], uv[t + 1]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
                }
            }
        }
    }

    public static final class Part {
        final String name, anim, parent;
        final float px, py, pz, rate;
        final Batch solid, glow, glass;

        Part(String name, String anim, String parent, float[] pivot, float rate, Batch solid, Batch glow, Batch glass) {
            this.name = name;
            this.anim = anim;
            this.parent = parent;
            px = pivot[0];
            py = pivot[1];
            pz = pivot[2];
            this.rate = rate;
            this.solid = solid;
            this.glow = glow;
            this.glass = glass;
        }
    }

    public final List<Part> parts = new ArrayList<>();
    private final Map<String, Part> byName = new HashMap<>();
    public final float[] bounds = new float[6];
    public final float[] muzzle;

    private MeshModel(JsonObject root) {
        JsonArray b = root.getAsJsonArray("bounds");
        for (int i = 0; i < 6; i++) bounds[i] = b.get(i).getAsFloat();
        float[] mz = null;
        if (root.has("muzzle")) {
            JsonArray m = root.getAsJsonArray("muzzle");
            mz = new float[]{m.get(0).getAsFloat(), m.get(1).getAsFloat(), m.get(2).getAsFloat()};
        }
        muzzle = mz;
        for (JsonElement e : root.getAsJsonArray("parts")) {
            Part p = part(e.getAsJsonObject(), bounds[1], bounds[4]);
            parts.add(p);
            byName.put(p.name, p);
        }
    }

    /** Загруженная модель; ошибка чтения — пустая модель и запись в журнал, игра не падает. */
    public static synchronized MeshModel get(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            try (var in = IIS.class.getResourceAsStream("/assets/iis/meshes/" + k + ".json")) {
                if (in == null) throw new IllegalStateException("no such mesh");
                return new MeshModel(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                IIS.LOGGER.error("[IIS] mesh {}", k, e);
                JsonObject empty = new JsonObject();
                JsonArray zero = new JsonArray();
                for (int i = 0; i < 6; i++) zero.add(0);
                empty.add("bounds", zero);
                empty.add("parts", new JsonArray());
                return new MeshModel(empty);
            }
        });
    }

    private static final class Builder {
        final List<float[]> pos = new ArrayList<>(), uv = new ArrayList<>(), normal = new ArrayList<>();
        final List<int[]> color = new ArrayList<>();

        Batch build() {
            float[] p = new float[pos.size() * 12], t = new float[uv.size() * 8], n = new float[normal.size() * 3];
            int[] c = new int[color.size() * 4];
            for (int i = 0; i < pos.size(); i++) {
                System.arraycopy(pos.get(i), 0, p, i * 12, 12);
                System.arraycopy(uv.get(i), 0, t, i * 8, 8);
                System.arraycopy(normal.get(i), 0, n, i * 3, 3);
                System.arraycopy(color.get(i), 0, c, i * 4, 4);
            }
            return new Batch(p, t, n, c);
        }
    }

    private static Part part(JsonObject o, float minY, float maxY) {
        JsonArray q = o.getAsJsonArray("q");
        Builder solid = new Builder(), glow = new Builder(), glass = new Builder();
        float h = Math.max(1F, maxY - minY);
        int stride = 15;
        for (int i = 0; i + stride <= q.size(); i += stride) {
            float[] v = new float[12];
            for (int k = 0; k < 12; k++) v[k] = q.get(i + k).getAsFloat();
            int rgb = q.get(i + 12).getAsInt(), mat = q.get(i + 13).getAsInt(), flags = q.get(i + 14).getAsInt();
            boolean isGlow = (flags & 1) != 0, isGlass = (flags & 2) != 0;
            Builder b = isGlow ? glow : isGlass ? glass : solid;
            b.pos.add(v);
            b.normal.add(normal(v));
            b.uv.add(uv(v, mat, i));
            int[] c = new int[4];
            for (int k = 0; k < 4; k++) {
                // низ чуть темнее: корпус садится на землю
                float g = isGlow ? 1F : 0.82F + 0.18F * Math.min(1F, (v[k * 3 + 1] - minY) / h * 1.4F);
                int a = isGlass ? 110 : 255;
                c[k] = (a << 24) | ((int) (((rgb >> 16) & 255) * g) << 16) | ((int) (((rgb >> 8) & 255) * g) << 8) | (int) ((rgb & 255) * g);
            }
            b.color.add(c);
        }
        JsonArray pv = o.getAsJsonArray("pivot");
        return new Part(o.get("name").getAsString(), o.has("anim") ? o.get("anim").getAsString() : null, o.has("parent") ? o.get("parent").getAsString() : null,
                new float[]{pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()}, o.has("rate") ? o.get("rate").getAsFloat() : 1F,
                solid.build(), glow.build(), glass.build());
    }

    private static float[] normal(float[] v) {
        // диагонали дают нормаль и у вырожденного в треугольник четырёхугольника
        float ax = v[6] - v[0], ay = v[7] - v[1], az = v[8] - v[2], bx = v[9] - v[3], by = v[10] - v[4], bz = v[11] - v[5];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-6F ? new float[]{0, 1, 0} : new float[]{nx / l, ny / l, nz / l};
    }

    private static float length(float[] v, int a, int b) {
        float x = v[a * 3] - v[b * 3], y = v[a * 3 + 1] - v[b * 3 + 1], z = v[a * 3 + 2] - v[b * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float[] uv(float[] v, int mat, int seed) {
        int cx = (mat % 4) * CELL, cy = (mat / 4) * CELL;
        float w = Math.max(1F, Math.min(CELL - 2F, length(v, 0, 1) * 1.5F)), h = Math.max(1F, Math.min(CELL - 2F, length(v, 0, 3) * 1.5F));
        // разные места клетки, чтобы одинаковые панели не повторялись точка в точку
        int span = (int) (CELL - 1 - Math.max(w, h));
        int ox = span > 0 ? (seed * 7) % span : 0, oy = span > 0 ? (seed * 13) % span : 0;
        float u0 = (cx + ox + 0.5F) / ATLAS_SIZE, v0 = (cy + oy + 0.5F) / ATLAS_SIZE, u1 = (cx + ox + w) / ATLAS_SIZE, v1 = (cy + oy + h) / ATLAS_SIZE;
        return new float[]{u0, v0, u1, v0, u1, v1, u0, v1};
    }

    /** Нарисовать всю модель: единицы модели — пиксели, масштаб задаёт вызывающий. */
    public void submit(PoseStack stack, SubmitNodeCollector out, int light, int overlay, Pose pose) {
        for (Part p : parts) {
            stack.pushPose();
            if (p.parent != null) {
                Part parent = byName.get(p.parent);
                if (parent != null) transform(stack, parent, pose);
            }
            transform(stack, p, pose);
            if (p.solid.pos.length > 0) out.submitCustomGeometry(stack, SOLID, (m, vc) -> p.solid.emit(m, vc, overlay, light));
            if (p.glow.pos.length > 0) out.submitCustomGeometry(stack, SOLID, (m, vc) -> p.glow.emit(m, vc, OverlayTexture.NO_OVERLAY, FULL_BRIGHT));
            if (p.glass.pos.length > 0) out.submitCustomGeometry(stack, GLASS, (m, vc) -> p.glass.emit(m, vc, OverlayTexture.NO_OVERLAY, light));
            stack.popPose();
        }
    }

    private static void transform(PoseStack s, Part p, Pose a) {
        if (p.anim == null) return;
        switch (p.anim) {
            case "wheel" -> around(s, p, Axis.XP.rotation(a.distance * p.rate));
            case "steer" -> {
                s.translate(p.px, p.py, p.pz);
                s.mulPose(Axis.YP.rotationDegrees(a.steer));
                s.mulPose(Axis.XP.rotation(a.distance * p.rate));
                s.translate(-p.px, -p.py, -p.pz);
            }
            case "steerbar" -> around(s, p, Axis.YP.rotationDegrees(a.steer));
            case "prop", "drill" -> around(s, p, Axis.ZP.rotation(a.spin * p.rate));
            case "rotor" -> around(s, p, Axis.YP.rotation(a.spin * p.rate));
            case "tail" -> around(s, p, Axis.XP.rotation(a.spin * p.rate));
            case "turret" -> around(s, p, Axis.YP.rotationDegrees(a.turretYaw));
            case "barrel" -> around(s, p, Axis.XP.rotationDegrees(a.barrelPitch));
            case "rod" -> {
                float t = a.distance * 4.85F;
                s.translate(0F, (float) -Math.sin(t) * p.rate, (float) Math.cos(t) * p.rate);
            }
            case "piston" -> s.translate(0F, 0F, (float) Math.cos(a.distance * 4.85F) * p.rate);
            default -> {}
        }
    }

    private static void around(PoseStack s, Part p, org.joml.Quaternionf q) {
        s.translate(p.px, p.py, p.pz);
        s.mulPose(q);
        s.translate(-p.px, -p.py, -p.pz);
    }
}
