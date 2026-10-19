package ru.iis.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.RegistryEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.OptionCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.world.vein.OreGridLayout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Слой JourneyMap «сетка рудных чанков GT5»: квадраты 3×3 чанка и залитый рудный (центральный) чанк,
 * где генерируется жила. Обычная сетка чанков — встроенная в JourneyMap. Включается в настройках JourneyMap → ИИС.
 * Загружается только при установленном JourneyMap (класс находит сам JourneyMap по аннотации).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class IisJourneyMapPlugin implements IClientPlugin {
    /** Радиус в квадратах 3×3 (±10 квадратов = ±30 чанков). */
    static final int RADIUS = 10;
    static final int GRID_COLOR = 0xFF8C1A, ORE_COLOR = 0xFFE000;

    private @Nullable IClientAPI api;
    private @Nullable BooleanOption enabled;
    private final Map<OreGridLayout.Region, PolygonOverlay[]> shown = new HashMap<>();
    private @Nullable ResourceKey<Level> dimension;
    private OreGridLayout.@Nullable Region playerRegion;
    private boolean lastEnabled = true;

    @Override
    public String getModId() {
        return IIS.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT.subscribe(IIS.MODID, this::registerOptions);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerOptions(RegistryEvent.OptionsRegistryEvent e) {
        OptionCategory cat = new OptionCategory(IIS.MODID, Component.translatable("jm.iis.category").getString());
        enabled = new BooleanOption(cat, "oreGrid", Component.translatable("jm.iis.ore_grid").getString(), true);
    }

    private void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (api == null || mc.player == null || mc.level == null) {
            clear();
            return;
        }
        if (mc.level.getGameTime() % 20 != 0) return;
        boolean on = optionOn();
        if (!on) {
            if (lastEnabled) clear();
            lastEnabled = false;
            return;
        }
        lastEnabled = true;
        ResourceKey<Level> dim = mc.level.dimension();
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        List<OreGridLayout.Region> want = OreGridLayout.around(cx, cz, RADIUS);
        OreGridLayout.Region here = want.get(want.size() / 2);
        if (dim.equals(dimension) && here.equals(playerRegion)) return;
        if (!dim.equals(dimension)) clear();
        dimension = dim;
        playerRegion = here;
        Set<OreGridLayout.Region> keep = new HashSet<>(want);
        shown.entrySet().removeIf(en -> {
            if (keep.contains(en.getKey())) return false;
            for (PolygonOverlay o : en.getValue()) api.remove(o);
            return true;
        });
        for (OreGridLayout.Region r : want) {
            if (shown.containsKey(r)) continue;
            PolygonOverlay grid = overlay(dim, square(r.minChunkX(), r.minChunkZ(), r.maxChunkX(), r.maxChunkZ()),
                    new ShapeProperties().setStrokeColor(GRID_COLOR).setStrokeOpacity(0.8f).setStrokeWidth(1.5f).setFillOpacity(0f));
            PolygonOverlay ore = overlay(dim, square(r.centerX(), r.centerZ(), r.centerX(), r.centerZ()),
                    new ShapeProperties().setFillColor(ORE_COLOR).setFillOpacity(0.35f).setStrokeColor(ORE_COLOR).setStrokeOpacity(0.9f).setStrokeWidth(1.0f));
            ore.setTitle(Component.translatable("jm.iis.ore_chunk").getString());
            try {
                api.show(grid);
                api.show(ore);
                shown.put(r, new PolygonOverlay[]{grid, ore});
            } catch (Exception ex) {
                IIS.LOGGER.warn("JourneyMap ore grid: {}", ex.toString());
                return;
            }
        }
    }

    /** Опция JourneyMap; пока JourneyMap не привязал её к своему конфигу, get() падает — считаем включённой. */
    private boolean optionOn() {
        if (enabled == null) return true;
        try {
            Boolean v = enabled.get();
            return v == null || v;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static PolygonOverlay overlay(ResourceKey<Level> dim, MapPolygon poly, ShapeProperties shape) {
        PolygonOverlay o = new PolygonOverlay(IIS.MODID, dim, shape, poly);
        o.setOverlayGroupName("ore_grid");
        o.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
        o.setActiveMapTypes(Context.MapType.all());
        return o;
    }

    /** Прямоугольник по чанкам (включительно) в координатах блоков. */
    private static MapPolygon square(int minCx, int minCz, int maxCx, int maxCz) {
        int x0 = minCx * 16, z0 = minCz * 16, x1 = (maxCx + 1) * 16, z1 = (maxCz + 1) * 16;
        return new MapPolygon(new BlockPos(x0, 64, z0), new BlockPos(x0, 64, z1), new BlockPos(x1, 64, z1), new BlockPos(x1, 64, z0));
    }

    private void clear() {
        if (api != null && !shown.isEmpty()) api.removeAll(IIS.MODID);
        shown.clear();
        dimension = null;
        playerRegion = null;
    }
}
