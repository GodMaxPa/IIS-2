package ru.iis.bio.mutant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.bio.BioContent;
import ru.iis.bio.BioSounds;
import ru.iis.bio.item.BioItems;

import java.util.function.Supplier;

/**
 * Мутанты ванильных существ, первый слой (спецификация v0.7 §17.4, §19.8–19.9; черновик Б-2, D148).
 * Животное остаётся обычной сущностью мира; линия, стадия и запас продукта записаны в её данных.
 *
 * <p>Ход: наблюдение щупом (стадия 0, фиксирует исходное состояние) → агент воздействия у площадки вивария (стадия 1, конечный цикл,
 * агент расходуется; повторное воздействие невозможно) → период проявления {@link #MANIFEST_TICKS} → рабочая линия (стадия 2):
 * изменённое сложение, имя линии, продукт. Продукт собирается скребком и восстанавливается за {@link #REGROW_TICKS}; у мясистой свиньи
 * продукт — туша при разделке ножом. Числа — черновик, не утверждены.
 */
public final class Mutants {
    private Mutants() {}

    /** Период проявления профиля: 2 минуты (черновик). */
    public static final int MANIFEST_TICKS = 2400;
    /** Восстановление запаса продукта после сбора: 5 минут (черновик). */
    public static final int REGROW_TICKS = 6000;
    /** Площадка вивария должна быть не дальше стольких блоков от животного. */
    public static final int PAD_RANGE = 4;

    public enum Profile {
        PIG_FLESHY("pig_fleshy", EntityType.PIG, 0.25),
        COW_BINDING("cow_binding", EntityType.COW, 0.10),
        SHEEP_LONGFIBER("sheep_longfiber", EntityType.SHEEP, 0.10),
        CHICKEN_SHELL("chicken_shell", EntityType.CHICKEN, 0.20);

        public final String id;
        public final EntityType<?> species;
        /** Прибавка к размеру рабочей линии (видимое изменение сложения). */
        public final double scale;

        Profile(String id, EntityType<?> species, double scale) {
            this.id = id;
            this.species = species;
            this.scale = scale;
        }

        public static @Nullable Profile byId(String id) {
            for (Profile p : values()) if (p.id.equals(id)) return p;
            return null;
        }
    }

    /** @param stage 0 — наблюдалось, 1 — проявляется, 2 — рабочая линия; {@code readyAt} — игровое время конца проявления или восстановления */
    public record Data(String profile, int stage, long readyAt) {
        public static final Data NONE = new Data("", -1, 0);
        public static final MapCodec<Data> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("profile").forGetter(Data::profile),
                Codec.INT.fieldOf("stage").forGetter(Data::stage),
                Codec.LONG.fieldOf("ready_at").forGetter(Data::readyAt)).apply(i, Data::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IIS.MODID);
    public static final Supplier<AttachmentType<Data>> DATA = ATTACHMENTS.register("bio_mutant",
            () -> AttachmentType.builder(() -> Data.NONE).serialize(Data.CODEC, d -> d.stage() >= 0).build());

    private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath(IIS.MODID, "bio_mutant_scale");

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(Mutants::onInteract);
        NeoForge.EVENT_BUS.addListener(Mutants::onTick);
    }

    public static Data data(LivingEntity mob) {
        return mob.getData(DATA);
    }

    /** Рабочая линия этого животного (стадия 2) или null. */
    public static @Nullable Profile working(LivingEntity mob) {
        Data d = data(mob);
        return d.stage() == 2 ? Profile.byId(d.profile()) : null;
    }

    public static @Nullable Profile profileOfAgent(ItemStack stack) {
        for (Profile p : Profile.values()) if (stack.is(agent(p))) return p;
        return null;
    }

    public static Item agent(Profile p) {
        return BioItems.AGENTS.get(p.ordinal()).get();
    }

    /** Есть ли рядом площадка вивария. */
    public static boolean padNear(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-PAD_RANGE, -2, -PAD_RANGE), at.offset(PAD_RANGE, 1, PAD_RANGE))) {
            if (level.getBlockState(p).is(BioContent.VIVARIUM_PAD.get())) return true;
        }
        return false;
    }

    /**
     * Воздействие агентом. Проверки идут до расхода (§17.4 п. 3): вид, возраст, отсутствие прежнего профиля, виварий.
     * @return ключ сообщения; {@code message.iis.bio.mutant.exposed} — успех
     */
    public static String expose(ServerLevel level, LivingEntity mob, Profile profile) {
        if (mob.getType() != profile.species) return "message.iis.bio.mutant.wrong_species";
        if (mob.isBaby()) return "message.iis.bio.mutant.baby";
        Data d = data(mob);
        if (d.stage() >= 1) return "message.iis.bio.mutant.already";
        if (d.stage() < 0) return "message.iis.bio.mutant.observe_first";
        if (!padNear(level, mob.blockPosition())) return "message.iis.bio.mutant.no_pad";
        mob.setData(DATA, new Data(profile.id, 1, level.getGameTime() + MANIFEST_TICKS));
        if (mob instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
        return "message.iis.bio.mutant.exposed";
    }

    /** Проявление: стадия 1 → 2 по истечении срока; рабочая линия получает сложение и имя. */
    public static void advance(ServerLevel level, LivingEntity mob) {
        Data d = data(mob);
        Profile p = Profile.byId(d.profile());
        if (p == null) return;
        if (d.stage() == 1 && level.getGameTime() >= d.readyAt()) {
            mob.setData(DATA, new Data(p.id, 2, level.getGameTime()));
            mob.setCustomName(Component.translatable("entity.iis.mutant." + p.id));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 12, 0.4, 0.4, 0.4, 0);
            level.playSound(null, mob.blockPosition(), BioSounds.HEARTBEAT, SoundSource.NEUTRAL, 0.8f, 1.4f);
            d = data(mob);
        }
        if (d.stage() == 2) {
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null && !scale.hasModifier(SCALE_ID)) {
                scale.addPermanentModifier(new AttributeModifier(SCALE_ID, p.scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    /** Продукт сбора рабочей линии (у мясистой свиньи сбора нет — её продукт в туше). */
    public static ItemStack product(Profile p) {
        return switch (p) {
            case COW_BINDING -> new ItemStack(BioItems.BINDING_SECRETION.get(), 2);
            case SHEEP_LONGFIBER -> new ItemStack(BioItems.LONG_FIBER.get(), 3);
            case CHICKEN_SHELL -> new ItemStack(BioItems.DENSE_SHELL.get(), 1);
            case PIG_FLESHY -> ItemStack.EMPTY;
        };
    }

    /** Сбор продукта: один раз за {@link #REGROW_TICKS}; пустая стопка — собирать нечего. */
    public static ItemStack collect(ServerLevel level, LivingEntity mob) {
        Data d = data(mob);
        Profile p = Profile.byId(d.profile());
        if (p == null || d.stage() != 2 || level.getGameTime() < d.readyAt()) return ItemStack.EMPTY;
        ItemStack out = product(p);
        if (out.isEmpty()) return out;
        mob.setData(DATA, new Data(p.id, 2, level.getGameTime() + REGROW_TICKS));
        return out;
    }

    /** Строка наблюдения для щупа. */
    public static Component describe(ServerLevel level, LivingEntity mob) {
        Data d = data(mob);
        Profile p = Profile.byId(d.profile());
        long left = Math.max(0, d.readyAt() - level.getGameTime()) / 20;
        if (d.stage() <= 0 || p == null) return Component.translatable("message.iis.bio.mutant.baseline", mob.getType().getDescription());
        Component line = Component.translatable("entity.iis.mutant." + p.id);
        if (d.stage() == 1) return Component.translatable("message.iis.bio.mutant.manifesting", line, left);
        if (p == Profile.PIG_FLESHY) return Component.translatable("message.iis.bio.mutant.working_carcass", line);
        return left > 0 ? Component.translatable("message.iis.bio.mutant.regrowing", line, left) : Component.translatable("message.iis.bio.mutant.ready", line);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Animal mob)) return;
        ItemStack stack = event.getItemStack();
        Profile agent = profileOfAgent(stack);
        boolean probe = stack.is(BioItems.PROBE.get()), scraper = stack.is(BioItems.SCRAPER.get());
        if (agent == null && !probe && !scraper) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        if (probe) {
            if (data(mob).stage() < 0 && !mob.isBaby()) mob.setData(DATA, new Data("", 0, 0));
            advance(level, mob);
            player.sendOverlayMessage(describe(level, mob));
        } else if (agent != null) {
            String msg = expose(level, mob, agent);
            if (msg.endsWith(".exposed")) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 16, 0.4, 0.4, 0.4, 0);
            }
            player.sendOverlayMessage(Component.translatable(msg));
        } else {
            advance(level, mob);
            ItemStack out = collect(level, mob);
            if (out.isEmpty()) {
                player.sendOverlayMessage(describe(level, mob));
            } else {
                level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), out));
                if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, event.getHand());
            }
        }
    }

    /** Раз в 5 секунд у помеченных животных: проявление и сложение (после загрузки мира модификатор уже сохранён). */
    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal mob) || mob.tickCount % 100 != 0 || !(mob.level() instanceof ServerLevel level)) return;
        if (!mob.hasData(DATA) || data(mob).stage() < 1) return;
        advance(level, mob);
    }
}
