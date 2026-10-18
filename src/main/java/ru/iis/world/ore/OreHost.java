package ru.iis.world.ore;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Порода, в которой лежит руда. Определяет внешний вид блока руды и что можно заменять при генерации. */
public enum OreHost {
    STONE("stone", "", 0.0f, SoundType.STONE, MapColor.STONE),
    DEEPSLATE("deepslate", "deepslate_", 1.5f, SoundType.DEEPSLATE, MapColor.DEEPSLATE),
    NETHERRACK("netherrack", "nether_", 0.0f, SoundType.NETHERRACK, MapColor.NETHER),
    END_STONE("end_stone", "end_", 0.0f, SoundType.STONE, MapColor.SAND);

    private final String id;
    private final String prefix;
    private final float extraHardness;
    private final SoundType sound;
    private final MapColor mapColor;

    OreHost(String id, String prefix, float extraHardness, SoundType sound, MapColor mapColor) {
        this.id = id;
        this.prefix = prefix;
        this.extraHardness = extraHardness;
        this.sound = sound;
        this.mapColor = mapColor;
    }

    public String id() { return id; }
    public float extraHardness() { return extraHardness; }
    public SoundType sound() { return sound; }
    public MapColor mapColor() { return mapColor; }

    /** ID блока руды: ore_iron, deepslate_ore_iron, nether_ore_iron, end_ore_iron. */
    public String blockId(String materialId) { return prefix + "ore_" + materialId; }

    public static OreHost byId(String id) {
        for (OreHost h : values()) if (h.id.equals(id)) return h;
        throw new IllegalArgumentException("Unknown ore host " + id);
    }

    /** Какую руду поставить вместо этого блока, или null, если блок заменять нельзя (воздух, вода, руда и т.п.). */
    public static OreHost forReplacing(BlockState state) {
        if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) return DEEPSLATE;
        if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) return STONE;
        if (state.is(BlockTags.BASE_STONE_NETHER)) return NETHERRACK;
        if (state.is(Blocks.END_STONE)) return END_STONE;
        return null;
    }
}
