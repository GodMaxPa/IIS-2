package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import ru.iis.command.SelfTest;
import ru.iis.fluid.FluidContainers;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;

import java.util.List;

/** Игровые тесты программной схемы GT (D75) и стальных капсул GT. */
final class CircuitGameTests {
    private CircuitGameTests() {}

    private static ItemStack circuit(int n) {
        return ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), n);
    }

    /** Рецепт со схемой 2: без схемы и со схемой 3 не подходит; со схемой 2 подходит, схема не забирается. */
    static void circuitSelectsRecipe(GameTestHelper h) {
        GtRecipe r = new GtRecipe(GtRecipeMap.BENDER, List.of(SizedIngredient.of(Items.IRON_INGOT, 1)), List.of(), 10, 8, List.of(), List.of(), 0, 2);
        h.assertTrue(r.allocate(List.of(new ItemStack(Items.IRON_INGOT))) == null, "no circuit — no match");
        h.assertTrue(r.allocate(List.of(new ItemStack(Items.IRON_INGOT), circuit(3))) == null, "wrong circuit — no match");
        int[] take = r.allocate(List.of(new ItemStack(Items.IRON_INGOT), circuit(2)));
        h.assertTrue(take != null && take[0] == 1 && take[1] == 0, "circuit 2 matches and is not consumed");
        h.assertTrue(ProgrammedCircuitItem.config(circuit(25)) == 0, "configuration wraps 0..24");
        h.succeed();
    }

    /** Гибочный станок LV: 12 стальных пластин → 6 капсул (GT_MachineRecipeLoader:1212). */
    static void benderSteelCapsules(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.BENDER, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(SelfTest.item("plate_steel"), 12));
        h.succeedWhen(() -> {
            ItemStack out = m.getItem(LvMachineType.BENDER.inputs);
            h.assertTrue(out.is(FluidContainers.CAPSULE.get()) && out.getCount() == 6, "expected 6 capsules, got " + out);
        });
    }
}
