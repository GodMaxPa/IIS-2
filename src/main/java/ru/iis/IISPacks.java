package ru.iis;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Встроенный датапак "packs/iis_overrides" (генерирует tools/gen/gen_content.py): переопределения ванильных файлов —
 * отключённые рецепты металлических инструментов (ИИС ToolFix.zs), настройки шума без больших рудных жил.
 * Всегда включён и стоит выше остальных, иначе ванильные файлы из общего пакета модов могут перекрыть наши.
 */
final class IISPacks {
    private IISPacks() {}

    static void addPacks(AddPackFindersEvent event) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(IIS.MODID, "packs/iis_overrides"), PackType.SERVER_DATA,
                Component.literal("IIS 2.0: overrides"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
