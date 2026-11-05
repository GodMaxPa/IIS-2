package ru.iis.fluid;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ёмкости жидкостей, общие для Кибера и Био (D72): обычная и универсальная капсулы ИИС 1, универсальные резервуары.
 * Работают со всем, у чего есть обработчик жидкостей NeoForge: машины и трубы Кибера, органы, места и сосуды Био.
 */
public final class FluidContainers {
    private FluidContainers() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidContent>> CONTENT = COMPONENTS.registerComponentType("fluid_content",
            b -> b.persistent(FluidContent.CODEC).networkSynchronized(FluidContent.STREAM_CODEC));

    /** Капсула (GT Cell_Empty / «Капсула с X»): ровно 1 000 мБ. */
    public static final DeferredItem<Item> CAPSULE = ITEMS.registerItem("capsule", p -> new CapsuleItem(false, p));
    /** Универсальная капсула (IC2 Cell_Universal_Fluid): любые порции до 1 000 мБ. */
    public static final DeferredItem<Item> UNIVERSAL_CAPSULE = ITEMS.registerItem("universal_capsule", p -> new CapsuleItem(true, p.stacksTo(16)));

    /** Исполнения резервуара: id, ёмкость мБ (временные числа, D72). */
    public static final Map<String, DeferredBlock<UniversalTankBlock>> TANKS = new LinkedHashMap<>();
    private static final List<DeferredItem<? extends Item>> TANK_ITEMS = new ArrayList<>();

    static {
        for (Object[] t : new Object[][]{{"universal_tank_bronze", 32000}, {"universal_tank_steel", 128000}}) {
            String id = (String) t[0];
            int cap = (Integer) t[1];
            DeferredBlock<UniversalTankBlock> b = BLOCKS.registerBlock(id, p -> new UniversalTankBlock(cap, p),
                    p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion());
            TANKS.put(id, b);
            TANK_ITEMS.add(ITEMS.registerItem(id, p -> new UniversalTankBlock.TankItem(b.get(), p.stacksTo(1))));
        }
        // D108: GT_MetaTileEntity_QuantumTank — 6^T × 267000 л, стальной корпус машины своего уровня
        int[] quantum = {1602000, 9612000, 57672000, 346032000, 2076192000};
        String[] tiers = {"lv", "mv", "hv", "ev", "iv"};
        for (int i = 0; i < 5; i++) {
            String id = "quantum_tank_" + tiers[i];
            int cap = quantum[i];
            DeferredBlock<UniversalTankBlock> b = BLOCKS.registerBlock(id, p -> new UniversalTankBlock(cap, false, p),
                    p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops());
            TANKS.put(id, b);
            TANK_ITEMS.add(ITEMS.registerItem(id, p -> new UniversalTankBlock.TankItem(b.get(), p.stacksTo(1))));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UniversalTankBlockEntity>> TANK_BE = BLOCK_ENTITIES.register("universal_tank",
            () -> new BlockEntityType<>(UniversalTankBlockEntity::new, TANKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("fluids", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.fluids"))
            .icon(() -> new ItemStack(CAPSULE.get()))
            .displayItems((params, out) -> {
                out.accept(CAPSULE.get());
                out.accept(UNIVERSAL_CAPSULE.get());
                TANK_ITEMS.forEach(i -> out.accept(i.get()));
                // полные капсулы всех жидкостей — как GT «Капсула с X»
                ItemStack empty = new ItemStack(CAPSULE.get());
                out.accept(CapsuleItem.withContent(empty, FluidContent.of(Fluids.WATER, CapsuleItem.CAPACITY)));
                out.accept(CapsuleItem.withContent(empty, FluidContent.of(Fluids.LAVA, CapsuleItem.CAPACITY)));
                for (GtFluids.Def d : GtFluids.all().values()) out.accept(CapsuleItem.withContent(empty, FluidContent.of(d.source().get(), CapsuleItem.CAPACITY)));
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(FluidContainers::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(FluidContainers::onRightClickBlock);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TANK_BE.get(), UniversalTankBlockEntity::fluidHandler);
    }

    /**
     * ПКМ капсулой по любому блоку с обработчиком жидкостей — перелить (раньше, чем блок откроет свой интерфейс).
     * ПКМ ведром по резервуару — налить или набрать 1 000 мБ.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        Level level = event.getLevel();
        boolean capsule = stack.getItem() instanceof CapsuleItem;
        boolean bucket = stack.getItem() instanceof BucketItem && level.getBlockState(event.getPos()).getBlock() instanceof UniversalTankBlock;
        if (!capsule && !bucket) return;
        ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, event.getPos(), event.getFace());
        if (h == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        if (capsule) {
            ((CapsuleItem) stack.getItem()).transfer(level, player, event.getHand(), stack, h);
            return;
        }
        Fluid f = ((BucketItem) stack.getItem()).getContent();
        if (f != Fluids.EMPTY) {
            try (Transaction tx = Transaction.openRoot()) {
                if (h.insert(FluidResource.of(f), 1000, tx) == 1000) {
                    tx.commit();
                    if (!player.getAbilities().instabuild) player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
                    level.playSound(null, event.getPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                }
            }
            return;
        }
        for (int i = 0; i < h.size(); i++) {
            FluidResource r = h.getResource(i);
            if (r.isEmpty() || r.getFluid().getBucket() == Items.AIR) continue;
            try (Transaction tx = Transaction.openRoot()) {
                if (h.extract(r, 1000, tx) == 1000) {
                    tx.commit();
                    CapsuleItem.replaceOne(player, event.getHand(), stack, new ItemStack(r.getFluid().getBucket()));
                    level.playSound(null, event.getPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 1.0f);
                    return;
                }
            }
        }
    }
}
