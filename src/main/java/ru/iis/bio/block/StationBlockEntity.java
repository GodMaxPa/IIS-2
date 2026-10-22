package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;
import ru.iis.bio.production.BioStationMenu;
import ru.iis.bio.production.Station;
import ru.iis.bio.production.StationLogic;

/** Ручное место или орган Био: обёртка над {@link StationLogic} (жар от очага снизу, питание от колонии). */
public class StationBlockEntity extends BlockEntity implements StationLogic.Host, MenuProvider {
    public final StationLogic logic;

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.STATION_BE.get(), pos, state);
        Station st = state.getBlock() instanceof StationBlock sb ? sb.station : Station.BUTCHER;
        this.logic = new StationLogic(st, this, null);
        this.fluids = new ru.iis.bio.production.BioFluidHandler(logic.tanks, this::setChanged)
                .accepting(f -> ru.iis.bio.production.BioRecipe.usesFluid(st.id, f));
    }

    /** Баки места как общий обработчик жидкостей (сосуды Био, трубы, капсулы). */
    public final ru.iis.bio.production.BioFluidHandler fluids;

    public void serverTick() {
        logic.tick();
        if (level != null && level.getGameTime() % 5 == 0) pushToVessels();
    }

    /** Выходные баки сами сливаются в прилегающие сосуды Био (приём — сосуды раздают в место сами). */
    private void pushToVessels() {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof VesselBlockEntity v)) continue;
            var target = v.fluidHandler(d.getOpposite());
            for (ru.iis.bio.production.BioTank t : logic.tanks) {
                if (!t.isOutput() || t.amount() <= 0 || t.dedicated()) continue;
                var res = net.neoforged.neoforge.transfer.fluid.FluidResource.of(t.fluid());
                try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    int moved = target.insert(res, t.amount(), tx);
                    if (moved > 0) {
                        tx.commit();
                        v.markSource(d.getOpposite());
                        t.drain(t.fluid(), moved, false);
                        setChanged();
                    }
                }
            }
        }
    }

    // --- Host -----------------------------------------------------------------------------------------------------
    @Override
    public @Nullable ServerLevel serverLevel() {
        return level instanceof ServerLevel sl ? sl : null;
    }

    @Override
    public void changed() {
        setChanged();
    }

    @Override
    public boolean takeHeat() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof HearthBlockEntity h && h.takeHeat();
    }

    private @Nullable ColonyCoreBlockEntity core() {
        return level == null ? null : ColonyCoreBlockEntity.find(level, worldPosition);
    }

    @Override
    public boolean colonyConnected() {
        return core() != null;
    }

    @Override
    public boolean takeUpkeep(double mb) {
        ColonyCoreBlockEntity c = core();
        return c != null && c.drainN0(mb);
    }

    // --- меню -----------------------------------------------------------------------------------------------------
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BioStationMenu(id, inventory, logic, logic.data, logic.station, worldPosition);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) for (var s : logic.drops()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        logic.save(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        logic.load(in);
    }
}
