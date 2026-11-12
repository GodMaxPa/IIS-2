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

    /**
     * D150: машина берёт самый требовательный из подходящих рецептов. Четыре стальных стержня — рама; те же стержни с кольцами
     * и пружинами — подвеска (раньше рама закрывала её навсегда). Пластины в гибочном станке различает схема.
     */
    static void mostDemandingRecipeWins(GameTestHelper h) {
        var level = h.getLevel();
        ItemStack rods = new ItemStack(SelfTest.item("rod_steel"), 4);
        var frame = ru.iis.machine.recipe.GtRecipes.find(level, GtRecipeMap.ASSEMBLER, List.of(rods.copy()), e -> true);
        h.assertTrue(frame.isPresent() && frame.get().recipe().outputs().getFirst().create().is(SelfTest.item("frame_steel")), "4 rods: frame");
        var susp = ru.iis.machine.recipe.GtRecipes.find(level, GtRecipeMap.ASSEMBLER,
                List.of(rods.copy(), new ItemStack(SelfTest.item("ring_steel"), 2), new ItemStack(SelfTest.item("gun_spring"), 4)), e -> true);
        h.assertTrue(susp.isPresent() && susp.get().recipe().outputs().getFirst().create().is(SelfTest.item("vehicle_suspension")),
                "rods, rings and springs: suspension, got " + susp.map(x -> x.recipe().outputs().getFirst().create().toString()).orElse("nothing"));
        ItemStack plates = new ItemStack(SelfTest.item("plate_steel"), 64);
        for (int[] c : new int[][] {{2, 0}, {4, 1}, {12, 2}}) {
            var m = ru.iis.machine.recipe.GtRecipes.find(level, GtRecipeMap.BENDER, List.of(plates.copy(), circuit(c[0])), e -> true);
            String want = new String[] {"double_plate_steel", "foil_steel", "capsule"}[c[1]];
            h.assertTrue(m.isPresent() && m.get().recipe().outputs().getFirst().create().is(SelfTest.item(want)), "bender circuit " + c[0] + ": " + want);
        }
        h.assertTrue(ru.iis.machine.recipe.GtRecipes.find(level, GtRecipeMap.BENDER, List.of(plates.copy()), e -> true).isEmpty(), "a stack of plates without a circuit bends into nothing");
        h.succeed();
    }

    /** Гибочный станок LV: 12 стальных пластин → 6 капсул (GT_MachineRecipeLoader:1212). */
    static void benderSteelCapsules(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.BENDER, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(SelfTest.item("plate_steel"), 12));
        m.setItem(1, circuit(12));   // D150: схема по числу пластин, как в GT5 (иначе 12 пластин — это и фольга, и двойная пластина)
        h.succeedWhen(() -> {
            ItemStack out = m.getItem(LvMachineType.BENDER.inputs);
            h.assertTrue(out.is(FluidContainers.CAPSULE.get()) && out.getCount() == 6, "expected 6 capsules, got " + out);
        });
    }
}
