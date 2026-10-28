package ru.iis.rail.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.iis.rail.RailSpeed;

/**
 * Предел скорости вагонетки по пути ИИС (D102): в NeoForge 26.1 нет хука Forge {@code getRailMaxSpeed},
 * а OldMinecartBehavior#getMaxSpeed всегда 0.4 (в воде 0.2).
 */
@Mixin(OldMinecartBehavior.class)
public abstract class OldMinecartBehaviorMixin extends MinecartBehavior {
    protected OldMinecartBehaviorMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void iis$trackMaxSpeed(ServerLevel level, CallbackInfoReturnable<Double> cir) {
        Double speed = RailSpeed.maxSpeed(level, this.minecart);
        if (speed != null) cir.setReturnValue(this.minecart.isInWater() ? Math.min(0.2, speed) : speed);
    }
}
