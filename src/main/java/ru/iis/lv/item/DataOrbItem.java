package ru.iis.lv.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Сфера данных (GT Tool_DataOrb, Behaviour_DataOrb, D122): хранит «Elemental-Scan» — какой элемент отсканирован, для репликатора.
 * Список элементов и их масс — iis/elements.json (генератор tools/gen/gtlast.py). Чертежи сборочной линии сфера не хранит: для них флешка.
 */
public class DataOrbItem extends Item {
    public record ElementScan(Identifier dust, int mass) {
        public static final Codec<ElementScan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("dust").forGetter(ElementScan::dust),
                Codec.INT.fieldOf("mass").forGetter(ElementScan::mass)).apply(i, ElementScan::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ElementScan> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, ElementScan::dust, ByteBufCodecs.VAR_INT, ElementScan::mass, ElementScan::new);
    }

    private static Map<Identifier, Integer> elements;

    private static synchronized Map<Identifier, Integer> elements() {
        if (elements == null) {
            Map<Identifier, Integer> m = new HashMap<>();
            try (InputStream in = DataOrbItem.class.getResourceAsStream("/iis/elements.json")) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> m.put(Identifier.parse(e.getKey()), e.getValue().getAsInt()));
            } catch (IOException e) {
                throw new IllegalStateException("cannot read iis/elements.json", e);
            }
            elements = m;
        }
        return elements;
    }

    /** Скан этого предмета, если он — пыль элемента (GT mElement != null), иначе null. */
    public static @Nullable ElementScan scanOf(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer mass = elements().get(id);
        return mass == null ? null : new ElementScan(id, mass);
    }

    public DataOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ElementScan s = stack.get(LvContent.ELEMENT_SCAN.get());
        if (s == null) builder.accept(Component.translatable("item.iis.data_orb.empty").withStyle(net.minecraft.ChatFormatting.GRAY));
        else builder.accept(Component.translatable("item.iis.data_orb.element", new ItemStack(BuiltInRegistries.ITEM.getValue(s.dust())).getHoverName()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
