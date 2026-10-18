package ru.iis.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.iis.IIS;
import ru.iis.energy.VoltageTier;

/**
 * /iis — служебная команда: версия мода и таблица напряжений.
 * Нужна, чтобы владелец и автотест могли убедиться, что мод загружен.
 */
public final class IISCommand {
    private IISCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("iis")
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("voltages").executes(ctx -> voltages(ctx.getSource()))));
    }

    private static int info(CommandSourceStack src) {
        String version = ModList.get().getModContainerById(IIS.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        src.sendSuccess(() -> Component.translatable("iis.command.info", version), false);
        return 1;
    }

    private static int voltages(CommandSourceStack src) {
        for (VoltageTier t : VoltageTier.values()) {
            src.sendSuccess(() -> Component.literal(t.shortName() + " — " + t.voltage() + " EU/t"), false);
        }
        return VoltageTier.values().length;
    }
}
