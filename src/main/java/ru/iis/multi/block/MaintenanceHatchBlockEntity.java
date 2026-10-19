package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.multi.MultiContent;
import ru.iis.multi.SolderingIronItem;
import ru.iis.multi.logic.Maintenance;

/**
 * Люк обслуживания (GT_MetaTileEntity_Hatch_Maintenance, ручной). Инструментом по люку устраняется соответствующая
 * неисправность (инструмент теряет 1000 GT = 20 единиц прочности); контроллер забирает исправления на следующем тике.
 */
public class MaintenanceHatchBlockEntity extends BlockEntity {
    /** Исправления, ещё не переданные контроллеру. */
    private int pendingFixes;
    /** Неисправности машины, которую обслуживает люк (пишет контроллер); −1 — люк не в машине. */
    public int machineMask = -1;
    public long machineSeen = Long.MIN_VALUE;

    public MaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.MAINTENANCE_HATCH_BE.get(), pos, state);
    }

    private static TagKey<Item> tool(String t) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "tools/" + t));
    }

    public static Maintenance.@Nullable Problem problemFor(ItemStack stack) {
        if (stack.getItem() instanceof SolderingIronItem) return Maintenance.Problem.SOLDERING;
        if (stack.is(tool("wrench"))) return Maintenance.Problem.WRENCH;
        if (stack.is(tool("screwdriver"))) return Maintenance.Problem.SCREWDRIVER;
        if (stack.is(tool("soft_hammer"))) return Maintenance.Problem.SOFT_HAMMER;
        if (stack.is(tool("hammer"))) return Maintenance.Problem.HARD_HAMMER;
        if (stack.is(tool("crowbar"))) return Maintenance.Problem.CROWBAR;
        return null;
    }

    public static MutableComponent problemName(Maintenance.Problem p) {
        return Component.translatable("maintenance.iis." + switch (p) {
            case WRENCH -> "wrench";
            case SCREWDRIVER -> "screwdriver";
            case SOFT_HAMMER -> "soft_hammer";
            case HARD_HAMMER -> "hard_hammer";
            case SOLDERING -> "soldering";
            case CROWBAR -> "crowbar";
        });
    }

    public void useTool(ItemStack stack, Player player, InteractionHand hand) {
        Maintenance.Problem p = problemFor(stack);
        if (p == null) {
            report(player);
            return;
        }
        if (p == Maintenance.Problem.SOLDERING) {
            if (!SolderingIronItem.use(stack, player)) {
                player.sendOverlayMessage(Component.translatable("message.iis.maintenance.no_solder"));
                return;
            }
        } else if (!player.isCreative()) {
            stack.hurtAndBreak(20, player, hand);
        }
        pendingFixes |= 1 << p.ordinal();
        setChanged();
        if (level != null) level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
        player.sendOverlayMessage(Component.translatable("message.iis.maintenance.fixed", problemName(p)));
    }

    /** Контроллер забирает исправления. */
    public int takeFixes() {
        int f = pendingFixes;
        pendingFixes = 0;
        if (f != 0) setChanged();
        return f;
    }

    public void report(Player player) {
        if (machineMask < 0 || level == null || level.getGameTime() - machineSeen > 40) {
            player.sendSystemMessage(Component.translatable("gui.iis.ebf.structure_bad"));
            return;
        }
        MutableComponent list = Component.empty();
        boolean any = false;
        for (Maintenance.Problem p : Maintenance.Problem.values()) {
            if ((machineMask & (1 << p.ordinal())) == 0 && (pendingFixes & (1 << p.ordinal())) == 0) {
                if (any) list.append(", ");
                list.append(problemName(p));
                any = true;
            }
        }
        player.sendSystemMessage(any ? Component.translatable("message.iis.maintenance.list", list) : Component.translatable("message.iis.maintenance.none"));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("fixes", pendingFixes);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        pendingFixes = in.getIntOr("fixes", 0);
    }
}
