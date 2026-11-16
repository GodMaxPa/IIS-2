package ru.iis.implant;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.implant.ImplantLogic.Kind;
import ru.iis.implant.ImplantLogic.Slot;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.machine.block.MachineBlock;

import java.util.UUID;

/**
 * Хирургический стол (D157): машина HV. Ставит и снимает киберимпланты за EU и время; пациент должен оставаться рядом.
 * Операция причиняет боль и на минуту ослабляет (ответ владельца 16.11). Тело игрока рядом со столом заряжается из его запаса.
 * Окна нет: ПКМ имплантом — установка, ПКМ пустой рукой — состояние тела со строками «снять».
 */
public class SurgeryTableBlockEntity extends ElectricBlockEntity {
    public static final long CAPACITY = 1_600_000L, VOLTAGE = 512L;
    public static final double REACH = 4.0;

    private @Nullable UUID patient;
    /** Сам пациент, пока стол загружен; после перезагрузки мира ищется по UUID. */
    private @Nullable Player patientRef;
    /** Что ставим (id импланта) или пусто — тогда снимаем область {@link #slot}. */
    private String installing = "";
    private String slot = "";
    private int progress;
    private long spent;

    public SurgeryTableBlockEntity(BlockPos pos, BlockState state) {
        super(ImplantContent.SURGERY_TABLE_BE.get(), pos, state, 0, CAPACITY, VOLTAGE);
        energy.maxAmperesIn = 4;
    }

    public boolean busy() {
        return patient != null;
    }

    public int progress() {
        return progress;
    }

    /** Ближайший стол в пределах досягаемости игрока. */
    public static @Nullable SurgeryTableBlockEntity near(Player p) {
        BlockPos c = p.blockPosition();
        SurgeryTableBlockEntity best = null;
        double bestD = REACH * REACH;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-4, -3, -4), c.offset(4, 3, 4))) {
            BlockEntity be = p.level().getBlockEntity(q);
            if (be instanceof SurgeryTableBlockEntity t) {
                double d = q.distToCenterSqr(p.position());
                if (d <= bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return best;
    }

    // --- запуск операций --------------------------------------------------------------------------------------------
    /** ПКМ имплантом: начать установку. Предмет забирается сразу и возвращается, если операция сорвана. */
    public boolean beginInstall(Player p, ItemStack stack) {
        Kind k = Implants.kindOf(stack);
        if (k == null) return false;
        if (!ready(p, k.tier().surgeryEu)) return false;
        installing = k.id();
        slot = k.slot().id;
        stack.shrink(1);
        start(p);
        return true;
    }

    /** Команда снятия: вынуть имплант из области. */
    public boolean beginRemove(Player p, Slot s) {
        Kind k = Implants.body(p)[s.ordinal()];
        if (k == null) {
            p.sendOverlayMessage(Component.translatable("message.iis.implant.empty_slot"));
            return false;
        }
        if (!ready(p, k.tier().surgeryEu)) return false;
        installing = "";
        slot = s.id;
        start(p);
        return true;
    }

    private boolean ready(Player p, long eu) {
        if (busy()) {
            p.sendOverlayMessage(Component.translatable("message.iis.implant.busy"));
            return false;
        }
        if (energy.stored < eu) {
            p.sendOverlayMessage(Component.translatable("message.iis.implant.no_power", energy.stored, eu));
            return false;
        }
        return true;
    }

    private void start(Player p) {
        patient = p.getUUID();
        patientRef = p;
        progress = 0;
        spent = 0;
        setActive(true);
        p.sendOverlayMessage(Component.translatable("message.iis.implant.started"));
        setChanged();
    }

    private void setActive(boolean active) {
        if (level != null && getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ACTIVE, active), 3);
        }
    }

    // --- работа -----------------------------------------------------------------------------------------------------
    @Override
    protected void work(ServerLevel sl) {
        if (tickTimer % 20 == 0) chargeBodies(sl);
        if (patient == null) return;
        Player p = patientRef != null && !patientRef.isRemoved() ? patientRef : sl.getPlayerByUUID(patient);
        if (p == null || !p.isAlive() || p.level() != sl || worldPosition.distToCenterSqr(p.position()) > REACH * REACH) {
            abort(sl, p);
            return;
        }
        Kind k = installing.isEmpty() ? Implants.body(p)[Slot.byId(slot).ordinal()] : ImplantLogic.byId(installing);
        if (k == null) {
            abort(sl, p);
            return;
        }
        long perTick = k.tier().surgeryEu / ImplantLogic.SURGERY_TICKS;
        if (energy.stored < perTick) return;   // нет энергии — операция ждёт
        energy.stored -= perTick;
        spent += perTick;
        progress++;
        if (progress % 20 == 0) {
            sl.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 1.6f);
            p.sendOverlayMessage(Component.translatable("message.iis.implant.progress", progress * 100 / ImplantLogic.SURGERY_TICKS));
        }
        if (progress >= ImplantLogic.SURGERY_TICKS) finish(sl, p, k);
        setChanged();
    }

    private void finish(ServerLevel sl, Player p, Kind k) {
        Kind[] body = Implants.body(p);
        int i = k.slot().ordinal();
        if (installing.isEmpty()) {
            body[i] = null;
            give(p, new ItemStack(Implants.ITEM_OF.get(k).get()));
            p.sendOverlayMessage(Component.translatable("message.iis.implant.removed"));
        } else {
            Kind old = body[i];
            body[i] = k;
            if (old != null) give(p, new ItemStack(Implants.ITEM_OF.get(old).get()));
            p.sendOverlayMessage(Component.translatable("message.iis.implant.installed"));
        }
        Implants.setBody(p, body);
        Implants.setCharge(p, Math.min(Implants.charge(p), ImplantLogic.capacity(body)));
        // боль: операция не убивает — остаётся не меньше половины сердца
        float hurt = Math.min(ImplantLogic.SURGERY_DAMAGE, p.getHealth() - 1.0F);
        if (hurt > 0 && !p.getAbilities().instabuild) p.hurtServer(sl, p.damageSources().generic(), hurt);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ImplantLogic.WEAKNESS_TICKS, 0));
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ImplantLogic.WEAKNESS_TICKS, 0));
        sl.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        reset();
    }

    /** Пациент ушёл или умер: имплант, который ставили, возвращается; потраченная энергия пропадает. */
    private void abort(ServerLevel sl, @Nullable Player p) {
        Kind k = ImplantLogic.byId(installing);
        if (k != null) {
            ItemStack back = new ItemStack(Implants.ITEM_OF.get(k).get());
            if (p != null && p.isAlive()) give(p, back);
            else net.minecraft.world.Containers.dropItemStack(sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, back);
        }
        if (p != null) p.sendOverlayMessage(Component.translatable("message.iis.implant.aborted"));
        reset();
    }

    private void reset() {
        patient = null;
        patientRef = null;
        installing = "";
        slot = "";
        progress = 0;
        spent = 0;
        setActive(false);
        setChanged();
    }

    private static void give(Player p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    /** Раз в секунду: тела игроков рядом заряжаются из запаса стола. */
    private void chargeBodies(ServerLevel sl) {
        for (ServerPlayer p : sl.players()) {
            if (energy.stored <= 0) return;
            if (worldPosition.distToCenterSqr(p.position()) > REACH * REACH) continue;
            long n = Implants.addCharge(p, Math.min(energy.stored, ImplantLogic.TABLE_CHARGE * 20));
            if (n > 0) {
                energy.stored -= n;
                setChanged();
            }
        }
    }

    /** ПКМ пустой рукой: состояние тела; у занятых областей — строка «снять». */
    public void report(ServerPlayer p) {
        Kind[] body = Implants.body(p);
        p.sendSystemMessage(Component.translatable("message.iis.implant.header", Implants.charge(p), ImplantLogic.capacity(body), ImplantLogic.drain(body),
                energy.stored, CAPACITY).withStyle(ChatFormatting.AQUA));
        for (Slot s : Slot.values()) {
            Kind k = body[s.ordinal()];
            MutableComponent line = Component.translatable("implant.iis.slot." + s.id).append(": ");
            if (k == null) {
                line.append(Component.translatable("message.iis.implant.free").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                line.append(Component.translatable("item.iis." + k.id())).append(" ");
                line.append(Component.translatable("message.iis.implant.remove").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/iis_implant remove " + s.id))));
            }
            p.sendSystemMessage(line);
        }
    }

    // --- ElectricBlockEntity ----------------------------------------------------------------------------------------
    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return true;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return null;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // сломанный посреди операции стол возвращает имплант, который ставил
        Kind k = ImplantLogic.byId(installing);
        if (k != null && level != null) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Implants.ITEM_OF.get(k).get()));
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (patient != null) out.putString("patient", patient.toString());
        out.putString("installing", installing);
        out.putString("slot", slot);
        out.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        String u = in.getStringOr("patient", "");
        patient = u.isEmpty() ? null : UUID.fromString(u);
        installing = in.getStringOr("installing", "");
        slot = in.getStringOr("slot", "");
        progress = in.getIntOr("progress", 0);
    }
}
