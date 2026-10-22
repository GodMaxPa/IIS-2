package ru.iis.bio;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

/** События звуков Био: озвучены записями ванили через assets/iis/sounds.json (tools/gen/bio.py, D68). */
public final class BioSounds {
    private BioSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IIS.MODID);

    private static SoundEvent reg(String name) {
        SoundEvent e = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(IIS.MODID, "bio." + name));
        SOUNDS.register("bio." + name, () -> e);
        return e;
    }

    /** Удар сердца ядра: частота зависит от состояния. */
    public static final SoundEvent HEARTBEAT = reg("heartbeat");
    /** Голодное урчание. */
    public static final SoundEvent GROWL = reg("growl");
    public static final SoundEvent SQUELCH = reg("squelch");
    public static final SoundEvent TEAR = reg("tear");
    /** Глоток пасти. */
    public static final SoundEvent GULP = reg("gulp");
    public static final SoundEvent CHEW = reg("chew");
    /** Рост новой клетки ткани. */
    public static final SoundEvent GROW = reg("grow");
    /** Отступление ткани / тревога. */
    public static final SoundEvent HISS = reg("hiss");

    /** Живая плоть: ядро, пасть, ткань. */
    public static final SoundType FLESH = new SoundType(1.0f, 1.0f, TEAR, SQUELCH, GROW, SQUELCH, SQUELCH);
}
