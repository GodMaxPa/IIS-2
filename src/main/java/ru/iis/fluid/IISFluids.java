package ru.iis.fluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.iis.IIS;

/**
 * Жидкости мода. Пар (GT5: Materials.Water.mGas / "steam") — газ без блока и ведра:
 * его нельзя вылить в мир, только передавать по трубам и хранить в машинах.
 */
public final class IISFluids {
    private IISFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, IIS.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, IIS.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.iis.steam")
            .density(-1000).viscosity(200).temperature(375)
            .canSwim(false).canDrown(false).canPushEntity(false).canExtinguish(false).canHydrate(false).supportsBoating(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam", () -> new BaseFlowingFluid.Source(steamProps()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> STEAM_FLOWING = FLUIDS.register("steam_flowing", () -> new BaseFlowingFluid.Flowing(steamProps()));

    private static BaseFlowingFluid.Properties steamProps() {
        return new BaseFlowingFluid.Properties(STEAM_TYPE, STEAM, STEAM_FLOWING);
    }

    // --- креозот (Railcraft, побочный продукт коксовой печи; GT Materials.Creosote — топливо полужидкостного генератора) ---
    public static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS = net.neoforged.neoforge.registries.DeferredRegister.createBlocks(IIS.MODID);
    public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(IIS.MODID);

    public static final DeferredHolder<FluidType, FluidType> CREOSOTE_TYPE = TYPES.register("creosote", () -> new ViscousFluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.iis.creosote").density(1100).viscosity(1500).canConvertToSource(false)
            .canSwim(true).canDrown(true).fallDistanceModifier(0.0f), 0.6));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREOSOTE = FLUIDS.register("creosote", () -> new BaseFlowingFluid.Source(creosoteProps()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CREOSOTE_FLOWING = FLUIDS.register("creosote_flowing", () -> new BaseFlowingFluid.Flowing(creosoteProps()));
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.LiquidBlock> CREOSOTE_BLOCK = BLOCKS.registerBlock("creosote",
            p -> new net.minecraft.world.level.block.LiquidBlock(CREOSOTE.get(), p),
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN).replaceable().noCollision().strength(100.0f).noLootTable()
                    .liquid().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem> CREOSOTE_BUCKET = ITEMS.registerItem("creosote_bucket",
            p -> new net.minecraft.world.item.BucketItem(CREOSOTE.get(), p), p -> p.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

    private static BaseFlowingFluid.Properties creosoteProps() {
        return new BaseFlowingFluid.Properties(CREOSOTE_TYPE, CREOSOTE, CREOSOTE_FLOWING).block(CREOSOTE_BLOCK).bucket(CREOSOTE_BUCKET)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(20);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
