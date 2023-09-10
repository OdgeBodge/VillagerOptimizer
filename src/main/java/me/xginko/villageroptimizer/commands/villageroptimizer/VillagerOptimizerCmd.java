package me.xginko.villageroptimizer.commands.villageroptimizer;

import me.xginko.villageroptimizer.commands.SubCommand;
import me.xginko.villageroptimizer.commands.VillagerOptimizerCommand;
import me.xginko.villageroptimizer.commands.villageroptimizer.subcommands.ReloadSubCmd;
import me.xginko.villageroptimizer.commands.villageroptimizer.subcommands.VersionSubCmd;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VillagerOptimizerCmd implements TabCompleter, VillagerOptimizerCommand {

    private final List<SubCommand> subCommands = new ArrayList<>(7);
    private final List<String> tabCompleter = new ArrayList<>(7);

    public VillagerOptimizerCmd() {
        subCommands.add(new ReloadSubCmd());
        subCommands.add(new VersionSubCmd());
        for (SubCommand subcommand : subCommands) {
            tabCompleter.add(subcommand.getLabel());
        }
    }

    @Override
    public String label() {
        return "villageroptimizer";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return args.length == 1 ? tabCompleter : Collections.emptyList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0) {
            boolean cmdExists = false;
            for (SubCommand subCommand : subCommands) {
                if (args[0].equalsIgnoreCase(subCommand.getLabel())) {
                    subCommand.perform(sender, args);
                    cmdExists = true;
                    break;
                }
            }
            if (!cmdExists) sendCommandOverview(sender);
        } else {
            sendCommandOverview(sender);
        }
        return true;
    }

    private void sendCommandOverview(CommandSender sender) {
        if (!sender.hasPermission("anarchyexploitfixes.cmd.*")) return;
        sender.sendMessage(Component.text("-----------------------------------------------------").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("VillagerOptimizer Commands").color(NamedTextColor.BLUE));
        sender.sendMessage(Component.text("-----------------------------------------------------").color(NamedTextColor.GRAY));
        for (SubCommand subCommand : subCommands) {
            sender.sendMessage(
                    subCommand.getSyntax()
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(subCommand.getDescription())
            );
        }
        sender.sendMessage(
                Component.text("/optimizevillagers <message>").color(NamedTextColor.BLUE)
                .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Optimize villagers in a radius").color(NamedTextColor.GRAY))
        );
        sender.sendMessage(
                Component.text("/unoptmizevillagers").color(NamedTextColor.BLUE)
                .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Unoptimize villagers in a radius").color(NamedTextColor.GRAY))
        );
        sender.sendMessage(Component.text("-----------------------------------------------------").color(NamedTextColor.GRAY));
    }
}
