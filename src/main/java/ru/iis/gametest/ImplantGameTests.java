package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import ru.iis.implant.ImplantContent;
import ru.iis.implant.ImplantLogic;
import ru.iis.implant.ImplantLogic.Kind;
import ru.iis.implant.ImplantLogic.Slot;
import ru.iis.implant.ImplantLogic.Tier;
import ru.iis.implant.Implants;
import ru.iis.implant.SurgeryTableBlockEntity;

/** Киберимпланты (D157): операция на столе, бонусы и их выключение без заряда. */
final class ImplantGameTests {
    private ImplantGameTests() {}

    private static SurgeryTableBlockEntity table(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, ImplantContent.SURGERY_TABLE.get().defaultBlockState(), 3);
        return (SurgeryTableBlockEntity) h.getLevel().getBlockEntity(abs);
    }

    private static Player patient(GameTestHelper h, BlockPos rel) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(rel);
        p.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        return p;
    }

    /** Стол с энергией ставит батарею за 10 секунд: предмет уходит, область занята, игроку больно, энергия потрачена. */
    static void surgeryInstalls(GameTestHelper h) {
        SurgeryTableBlockEntity t = table(h, new BlockPos(4, 1, 4));
        t.energy.stored = 500_000;
        Player p = patient(h, new BlockPos(4, 1, 3));
        Kind cell = new Kind(Slot.CELL, Tier.HV);
        ItemStack stack = new ItemStack(Implants.ITEM_OF.get(cell).get());
        float before = p.getHealth();
        h.assertTrue(t.beginInstall(p, stack), "the operation must start");
        h.assertTrue(stack.isEmpty() && t.busy(), "the implant is taken, the table is busy");
        h.succeedWhen(() -> {
            h.assertTrue(!t.busy(), "operation in progress: " + t.progress());
            h.assertTrue(cell.equals(Implants.body(p)[Slot.CELL.ordinal()]), "the cell is not installed");
            h.assertTrue(p.getHealth() < before, "surgery must hurt");
            // 100 000 EU на операцию; остальное стол начал переливать в тело
            h.assertTrue(t.energy.stored <= 400_000 && t.energy.stored + Implants.charge(p) == 400_000, "table " + t.energy.stored + ", body " + Implants.charge(p));
            p.discard();
        });
    }

    /** Без энергии операция не начинается, предмет остаётся у игрока. */
    static void surgeryNeedsPower(GameTestHelper h) {
        SurgeryTableBlockEntity t = table(h, new BlockPos(4, 1, 4));
        Player p = patient(h, new BlockPos(4, 1, 3));
        ItemStack stack = new ItemStack(Implants.ITEM_OF.get(new Kind(Slot.HEART, Tier.HV)).get());
        h.assertTrue(!t.beginInstall(p, stack) && stack.getCount() == 1 && !t.busy(), "no power: no operation");
        p.discard();
        h.succeed();
    }

    /** Сердце HV с заряженной батареей даёт +6 здоровья; без заряда бонус снимается, имплант остаётся. */
    static void bonusNeedsCharge(GameTestHelper h) {
        Player p = patient(h, new BlockPos(4, 1, 4));
        Kind[] body = new Kind[Slot.values().length];
        body[Slot.CELL.ordinal()] = new Kind(Slot.CELL, Tier.HV);
        body[Slot.HEART.ordinal()] = new Kind(Slot.HEART, Tier.HV);
        body[Slot.SKIN.ordinal()] = new Kind(Slot.SKIN, Tier.EV);
        double base = p.getAttributeValue(Attributes.MAX_HEALTH), armor = p.getAttributeValue(Attributes.ARMOR);
        Implants.apply(p, body, true);
        h.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH) == base + 6, "heart: " + p.getAttributeValue(Attributes.MAX_HEALTH));
        h.assertTrue(p.getAttributeValue(Attributes.ARMOR) == armor + 8, "skin EV: " + p.getAttributeValue(Attributes.ARMOR));
        Implants.apply(p, body, false);
        h.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH) == base && p.getAttributeValue(Attributes.ARMOR) == armor, "bonuses must switch off without charge");
        h.assertTrue(ImplantLogic.capacity(body) == Tier.HV.capacity, "the implants stay");
        p.discard();
        h.succeed();
    }
}
