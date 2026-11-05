package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.FreezerLogic;
import ru.iis.multi.menu.EbfMenu;

import java.util.List;

/**
 * Вакуумный морозильник (GT_MetaTileEntity_VacuumFreezer, D81): охлаждает горячие слитки и капсулы.
 * Форма и разгон — {@link FreezerLogic}; загрязнения нет (getPollutionPerTick = 0).
 */
public class VacuumFreezerBlockEntity extends MultiblockControllerBlockEntity {
    public VacuumFreezerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.VACUUM_FREEZER_BE.get(), pos, state);
    }

    /** GT checkMachine: середина пустая, на прочих местах куба люки или морозостойкие корпуса (не меньше 16). */
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Direction back = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        BlockPos center = worldPosition.relative(back);
        if (!sl.getBlockState(center).isAir()) return false;
        int casings = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos b = center.offset(dx, dy, dz);
                    if (b.equals(center) || b.equals(worldPosition)) continue;
                    HatchType t = hatchAt(sl, b);
                    if (t == null) {
                        if (!sl.getBlockState(b).is(MultiContent.FROST_PROOF_CASING.get())) return false;
                        casings++;
                        continue;
                    }
                    switch (t) {
                        case MAINTENANCE -> parts.maintenance.add(b);
                        case INPUT_BUS -> parts.inputBuses.add(b);
                        case INPUT_HATCH -> parts.inputHatches.add(b);
                        case OUTPUT_BUS -> parts.outputBuses.add(b);
                        case OUTPUT_HATCH -> parts.outputHatches.add(b);
                        case ENERGY, ENERGY_MV, ENERGY_HV, ENERGY_EV, ENERGY_IV, ENERGY_LUV, ENERGY_ZPM -> parts.energy.add(b);
                        default -> {
                            return false;
                        }
                    }
                }
            }
        }
        return FreezerLogic.enoughCasings(casings);
    }

    /** GT checkRecipe: каждый предмет входных шин по отдельности. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        int tier = BlastLogic.machineTier(maxInputVoltage(sl));
        long vmax = BlastLogic.V[tier];
        var recipes = GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.VACUUM_FREEZER);
        for (ItemStack st : inputSlots(sl)) {
            if (st.isEmpty()) continue;
            for (RecipeHolder<GtRecipe> h : recipes) {
                GtRecipe r = h.value();
                if (r.eut() > vmax) continue;
                int[] take = r.allocate(List.of(st));
                if (take == null) continue;
                st.shrink(take[0]);
                inputBusesChanged(sl);
                efficiency = maintenance.maxEfficiency();
                efficiencyIncrease = 10000;
                BlastLogic.Run run = FreezerLogic.overclock(r.eut(), r.duration(), tier);
                eut = run.eut();
                maxProgress = run.duration();
                progress = 0;
                pendingItems = r.rollOutputs(sl.getRandom());
                pendingFluids = List.of();
                setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.freezer(id, inventory, data);
    }
}
