package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContent;
import ru.iis.machine.block.MachineBlock;
import ru.iis.world.UndergroundFluids;

/**
 * Колодец (GT_MetaTileEntity_Well.onPostTick, D133): раз в 60 тиков берёт жидкость месторождения чанка, как буровая с коэффициентом 1,
 * пока не наберёт 1000 л. Разрешены вода и нефть (well.allowedFluids = "water|oil" в конфиге GT).
 */
public class WellBlockEntity extends BlockEntity implements MachineBlock.TickingMachine {
    public static final int CAPACITY = 1000, PERIOD = 60;
    @Nullable Fluid fluid;
    int amount;
    private int progress;

    public WellBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.WELL_BE.get(), pos, state);
    }

    public static boolean allowed(Fluid f) {
        return f == Fluids.WATER || BuiltInRegistries.FLUID.getKey(f).getPath().equals("oil");
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || amount >= CAPACITY) return;
        if (++progress < PERIOD) return;
        progress = 0;
        UndergroundFluids.Sample s = UndergroundFluids.drain(sl, ChunkPos.containing(worldPosition), 1F);
        if (s == null || s.amount() <= 0 || !allowed(s.fluid()) || (fluid != null && fluid != s.fluid())) return;
        fluid = s.fluid();
        amount = Math.min(CAPACITY, amount + s.amount());
        setChanged();
        boolean full = amount >= CAPACITY;
        if (getBlockState().getValue(MachineBlock.ACTIVE) != full) sl.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ACTIVE, full), 3);
    }

    public static boolean isContainer(ItemStack s) {
        return s.is(Items.BUCKET) || (s.getItem() instanceof CapsuleItem && CapsuleItem.content(s).isEmpty());
    }

    /** «Выжать жидкость в ведро или одну ячейку». */
    void fill(Player player, InteractionHand hand, ItemStack held) {
        if (fluid == null || amount < CAPACITY) {
            player.sendOverlayMessage(Component.translatable("message.iis.well.not_full"));
            return;
        }
        ItemStack result;
        if (held.is(Items.BUCKET)) {
            if (fluid.getBucket() == Items.AIR) return;
            result = new ItemStack(fluid.getBucket());
        } else {
            result = CapsuleItem.withContent(held.copyWithCount(1), FluidContent.of(fluid, CAPACITY));
        }
        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(hand, result);
        else if (!player.getInventory().add(result)) player.drop(result, false);
        level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 1F);
        amount = 0;
        fluid = null;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ACTIVE, false), 3);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Amount", amount);
        out.putInt("Progress", progress);
        if (fluid != null) out.putString("Fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        amount = in.getIntOr("Amount", 0);
        progress = in.getIntOr("Progress", 0);
        fluid = in.getString("Fluid").map(id -> BuiltInRegistries.FLUID.getValue(Identifier.parse(id))).orElse(null);
        if (fluid == Fluids.EMPTY) fluid = null;
    }
}
