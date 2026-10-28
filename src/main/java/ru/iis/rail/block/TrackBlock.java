package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.rail.IisRail;
import ru.iis.rail.RailSpeed;
import ru.iis.rail.TrackKind;

/** Простой путь Railcraft: деревянный, усиленный, скоростной, электрический (повороты как у ванильного). */
public class TrackBlock extends RailBlock implements IisRail {
    private final TrackKind kind;
    private final MapCodec<RailBlock> codec;

    public TrackBlock(TrackKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new TrackBlock(kind, p));
    }

    @Override
    public MapCodec<RailBlock> codec() {
        return codec;
    }

    @Override
    public TrackKind kind() {
        return kind;
    }

    @Override
    public void onCartPass(BlockState state, ServerLevel level, BlockPos pos, AbstractMinecart cart) {
        if (kind == TrackKind.HIGH_SPEED) RailSpeed.basicPass(level, pos, getRailDirection(state, level, pos, cart), cart);
    }
}
