package ru.iis.implant;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

/** Блок хирургического стола (D157). Предметы имплантов и данные тела — в {@link Implants}. */
public final class ImplantContent {
    private ImplantContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);

    public static final DeferredBlock<SurgeryTableBlock> SURGERY_TABLE = BLOCKS.registerBlock("surgery_table", SurgeryTableBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SURGERY_TABLE_ITEM = Implants.ITEMS.registerSimpleBlockItem(SURGERY_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurgeryTableBlockEntity>> SURGERY_TABLE_BE = BLOCK_ENTITIES.register("surgery_table",
            () -> new BlockEntityType<>(SurgeryTableBlockEntity::new, SURGERY_TABLE.get()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        Implants.register(modBus);
    }
}
