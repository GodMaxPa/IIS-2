package ru.iis.fluid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Вязкая жидкость (креозот): движение как в лаве, но чуть медленнее, с выпрыгиванием на берег.
 * В NeoForge 26.1 жидкость без move() не даёт сущности двигаться вовсе — отсюда «невозможно выбраться».
 */
public class ViscousFluidType extends FluidType {
    private final double drag;

    public ViscousFluidType(Properties properties, double drag) {
        super(properties);
        this.drag = drag;
    }

    @Override
    public boolean move(LivingEntity entity, Vec3 input, double gravity) {
        boolean falling = entity.getDeltaMovement().y <= 0.0;
        double oldY = entity.getY();
        entity.moveRelative(0.02f, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        Vec3 m = entity.getDeltaMovement().multiply(drag, 0.8, drag);
        m = entity.getFluidFallingAdjustedMovement(gravity, falling, m);
        if (gravity != 0.0) m = m.add(0.0, -gravity / 4.0, 0.0);
        entity.setDeltaMovement(m);
        if (entity.horizontalCollision && entity.isFree(m.x, m.y + 0.6 - entity.getY() + oldY, m.z)) {
            entity.setDeltaMovement(m.x, 0.3, m.z);
        }
        return true;
    }
}
