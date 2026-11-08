package ru.iis.crop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Культура IC2 (CropCard) и культуры GT (GT_BaseCrop, D139). Данные — iis/crops.json (генератор tools/gen/crops.py): числа GT из GT_CropLoader,
 * культуры IC2 по памяти (исходников IC2 в папке ИИС 1 нет).
 *
 * @param stats  химия, еда, защита, цвет, сорность (IC2 CropCard#stat 0..4)
 * @param block  материал руды или блока, нужного под грядкой для последней стадии (GT_BaseCrop#isBlockBelow), или null
 * @param speed  GT mGrowthSpeed: от 200 и выше стадия длится tier × speed очков, иначе tier × 200 (CropCard#growthDuration)
 */
public record CropCard(int index, String id, String discoveredBy, String mod, int tier, int maxSize, int harvestSize, int afterHarvest, int speed,
                       int[] stats, List<String> attributes, @Nullable Identifier drop, List<Identifier> special, @Nullable String block,
                       @Nullable Identifier baseSeed, int baseSeedCount) {

    public static final List<CropCard> ALL;
    private static final Map<String, CropCard> BY_ID = new HashMap<>();

    static {
        List<CropCard> list = new ArrayList<>();
        try (InputStream in = CropCard.class.getResourceAsStream("/iis/crops.json")) {
            if (in != null) {
                JsonArray arr = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : arr) list.add(read(list.size(), e.getAsJsonObject()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read iis/crops.json", e);
        }
        ALL = Collections.unmodifiableList(list);
        for (CropCard c : ALL) BY_ID.put(c.id, c);
    }

    private static CropCard read(int index, JsonObject o) {
        JsonArray st = o.getAsJsonArray("stats");
        int[] stats = new int[5];
        for (int i = 0; i < 5; i++) stats[i] = st.get(i).getAsInt();
        List<String> attrs = new ArrayList<>();
        o.getAsJsonArray("attrs").forEach(a -> attrs.add(a.getAsString()));
        List<Identifier> special = new ArrayList<>();
        o.getAsJsonArray("special").forEach(a -> special.add(Identifier.parse(a.getAsString())));
        Identifier seed = null;
        int seedCount = 0;
        if (o.has("seed") && o.get("seed").isJsonArray()) {
            JsonArray s = o.getAsJsonArray("seed");
            seed = Identifier.parse(s.get(0).getAsString());
            seedCount = s.get(1).getAsInt();
        }
        return new CropCard(index, o.get("id").getAsString(), o.get("by").getAsString(), o.get("mod").getAsString(), o.get("tier").getAsInt(),
                o.get("max").getAsInt(), o.get("harvest").getAsInt(), o.get("after").getAsInt(), o.get("speed").getAsInt(), stats, List.copyOf(attrs),
                o.has("drop") && !o.get("drop").isJsonNull() ? Identifier.parse(o.get("drop").getAsString()) : null, List.copyOf(special),
                o.has("block") && !o.get("block").isJsonNull() ? o.get("block").getAsString() : null, seed, seedCount);
    }

    public static @Nullable CropCard byId(String id) {
        return BY_ID.get(id);
    }

    public static CropCard weed() {
        return BY_ID.get("weed");
    }

    public boolean isWeedCard() {
        return id.equals("weed");
    }

    public Component displayName() {
        return Component.translatable("crop.iis." + id);
    }

    public int stat(int n) {
        return n < 0 || n >= stats.length ? 0 : stats[n];
    }

    /** CropCard#growthDuration; GT_BaseCrop: tier × mGrowthSpeed, если скорость от 200. */
    public int growthDuration() {
        return speed >= 200 ? tier * speed : tier * 200;
    }

    /** CropCard#canBeHarvested (GT: размер от mHarvestSize). */
    public boolean canBeHarvested(int size) {
        return !isWeedCard() && size >= harvestSize;
    }

    /** GT_BaseCrop#canCross: созревшая (размер + 2 больше максимума). */
    public boolean canCross(int size) {
        return size + 2 > maxSize;
    }

    /** CropCard#dropGainChance: 0,95^уровень. */
    public double dropGainChance() {
        return Math.pow(0.95, tier);
    }

    /** CropCard#dropSeedChance. */
    public float dropSeedChance(int size) {
        if (size <= 1) return 0;
        float base = 0.5F;
        if (size == 2) base /= 2F;
        for (int i = 0; i < tier; i++) base *= 0.8F;
        return base;
    }

    /** GT_BaseCrop#getGain: особый плод с шансом n / (2n + 2), иначе основной. */
    public ItemStack gain(net.minecraft.util.RandomSource random) {
        if (!special.isEmpty()) {
            int i = random.nextInt(special.size() * 2 + 2);
            if (i < special.size()) return stack(special.get(i));
        }
        return drop == null ? ItemStack.EMPTY : stack(drop);
    }

    public ItemStack displayItem() {
        if (!special.isEmpty()) return stack(special.getLast());
        return drop == null ? new ItemStack(Items.SHORT_GRASS) : stack(drop);
    }

    private static ItemStack stack(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * TileEntityCrop#calculateRatioFor (по памяти): похожесть новой культуры a на родителя b — 500 для той же культуры; иначе по 2 очка за каждое
     * свойство минус разница, 5 за каждый общий признак, штраф за разницу уровней.
     */
    public static int ratio(CropCard a, CropCard b) {
        if (a == b) return 500;
        int value = 0;
        for (int i = 0; i < 5; i++) value += -Math.abs(a.stat(i) - b.stat(i)) + 2;
        for (String x : a.attributes) for (String y : b.attributes) if (x.equalsIgnoreCase(y)) value += 5;
        int diff = b.tier - a.tier;
        if (diff > 1) value -= 2 * diff;
        if (diff < -3) value -= -diff;
        return Math.max(value, 0);
    }
}
