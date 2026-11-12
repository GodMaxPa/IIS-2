package ru.iis.rail.mixin;

import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.iis.IISConfig;

/**
 * Пустой выделенный сервер не встаёт на паузу (D147): по умолчанию Minecraft останавливает тики через
 * {@code pause-when-empty-seconds} (60 с) без игроков — заводы, посевы и резервные копии мира ИИС при этом стоят.
 * Выключается настройкой {@code world.runWhenEmpty} в iis-server.toml.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @Inject(method = "pauseWhenEmptySeconds", at = @At("HEAD"), cancellable = true)
    private void iis$runWhenEmpty(CallbackInfoReturnable<Integer> cir) {
        if (IISConfig.SPEC.isLoaded() && IISConfig.RUN_WHEN_EMPTY.get()) cir.setReturnValue(0);
    }
}
