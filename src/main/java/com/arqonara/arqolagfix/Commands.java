package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Commands implements CommandExecutor, TabCompleter {
    private final ArqoLagfix plugin;

    public Commands(ArqoLagfix plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            sendCompactStatus(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "gui": 
                if (s instanceof Player) plugin.getGui().openGui((Player) s);
                else s.sendMessage("§c[ArqoLagfix] Console tidak bisa buka GUI!");
                break;
            case "status": 
            case "s":
                sendCompactStatus(s); 
                break;
            case "full":
            case "detail":
                sendDetailedStatus(s);
                break;
            case "reload": 
                if (!s.hasPermission("arqolagfix.admin")) {
                    s.sendMessage("§c[ArqoLagfix] No permission!");
                    return true;
                }
                plugin.reloadCustomConfigs();
                s.sendMessage("§a[ArqoLagfix] Configs reloaded!");
                break;
            case "optimize": 
                if (!s.hasPermission("arqolagfix.admin")) {
                    s.sendMessage("§c[ArqoLagfix] No permission!");
                    return true;
                }
                s.sendMessage("§e[ArqoLagfix] Starting optimization...");
                plugin.getEntityOpt().manualClear();
                s.sendMessage("§a[ArqoLagfix] Optimization complete!");
                break;
            case "clear":
            case "clearlag":
                if (!s.hasPermission("arqolagfix.admin")) {
                    s.sendMessage("§c[ArqoLagfix] No permission!");
                    return true;
                }
                if (s instanceof Player) {
                    plugin.getAutoClearLag().manualClear((Player) s);
                } else {
                    s.sendMessage("§e[ArqoLagfix] Clearing entities...");
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        public void run() {
                            plugin.getAutoClearLag().manualClear(null);
                        }
                    });
                }
                break;
            case "gc":
                if (!s.hasPermission("arqolagfix.admin")) return true;
                System.gc();
                s.sendMessage("§a[ArqoLagfix] Garbage collection triggered!");
                break;
            case "help":
            case "?":
                sendHelp(s);
                break;
            default:
                sendHelp(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<String>();
        
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                "gui", "status", "full", "clear", "reload", "gc", "help"
            );
            
            String input = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(input)) {
                    suggestions.add(sub);
                }
            }
        }
        
        return suggestions;
    }

    private void sendCompactStatus(CommandSender s) {
        PerformanceMonitor m = plugin.getMonitor();
        
        s.sendMessage("§6§m                §r §a§lArqoLagfix §6§m                ");
        s.sendMessage("§eStatus: " + m.getHealthStatus() + " §7| §aTPS: §f" + 
            String.format("%.1f", m.getTps()[0]));
        s.sendMessage("§cCPU: §f" + String.format("%.0f%%", m.getCpu()) + 
            " §7| §6RAM: §f" + String.format("%.1fGB", m.getRam()) + 
            " §7(" + String.format("%.0f%%", m.getRamPercent()) + ")");
        s.sendMessage("§bPlayers: §f" + m.getTotalPlayers() + 
            " §7| §dEntities: §f" + m.getTotalEntities() + 
            " §7| §7Chunks: §f" + m.getTotalChunks());
        s.sendMessage("§aAutoClear: " + (plugin.getAutoClearLag().isEnabled() ? "§aON" : "§cOFF") + 
            " §7| §7Last: §f" + plugin.getAutoClearLag().getLastCleared());
        s.sendMessage("§cExplosion: " + (plugin.getExplosionOpt().isEnabled() ? "§aON" : "§cOFF") + 
            " §7| §7" + plugin.getExplosionOpt().getStats());
        s.sendMessage("§6§m                                        ");
        s.sendMessage("§7Tip: §e/alf gui §7atau §e/alf help §7untuk lebih");
    }

    private void sendDetailedStatus(CommandSender s) {
        PerformanceMonitor m = plugin.getMonitor();
        s.sendMessage("§6╔════════════════════════════════════╗");
        s.sendMessage("§6║  §a§lArqoLagfix §7Performance Monitor  §6║");
        s.sendMessage("§6╠════════════════════════════════════╣");
        s.sendMessage("§6║ §eServer Status: " + m.getHealthStatus() + "              §6║");
        s.sendMessage("§6╠════════════════════════════════════╣");
        s.sendMessage("§6║ §aTPS:  §f%.2f §7/ §f%.2f §7/ §f%.2f §7(1m/5m/15m) §6║".formatted(
            m.getTps()[0], m.getTps()[1], m.getTps()[2]));
        s.sendMessage("§6║ §cCPU:  §f%.1f%%                        §6║".formatted(m.getCpu()));
        s.sendMessage("§6║ §6RAM:  §f%.1fGB §7(%.1f%%)                §6║".formatted(
            m.getRam(), m.getRamPercent()));
        s.sendMessage("§6╠════════════════════════════════════╣");
        s.sendMessage("§6║ §bPlayers:  §f%-22d §6║".formatted(m.getTotalPlayers()));
        s.sendMessage("§6║ §dEntities: §f%-22d §6║".formatted(m.getTotalEntities()));
        s.sendMessage("§6║ §7Chunks:   §f%-22d §6║".formatted(m.getTotalChunks()));
        s.sendMessage("§6╠════════════════════════════════════╣");
        s.sendMessage("§6║ §aAutoClearLag: " + (plugin.getAutoClearLag().isEnabled() ? "§aON " : "§cOFF") + "             §6║");
        s.sendMessage("§6║ §7Last cleared: §f%-17d §6║".formatted(plugin.getAutoClearLag().getLastCleared()));
        s.sendMessage("§6║ §cExplosion Opt: " + (plugin.getExplosionOpt().isEnabled() ? "§aON" : "§cOFF") + "            §6║");
        s.sendMessage("§6╠════════════════════════════════════╣");
        int worldCount = 0;
        for (String world : m.getWorldCounts().keySet()) {
            s.sendMessage("§6║ §7" + String.format("%-10s", world) + " §f" + 
                String.format("%5d", m.getWorldCounts().get(world)) + " ents §7/ §f" +
                String.format("%4d", m.getWorldChunks().get(world)) + " chunks §6║");
            worldCount++;
            if (worldCount >= 3) break;
        }
        s.sendMessage("§6╚════════════════════════════════════╝");
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6╔═══════ §a§lArqoLagfix Commands §6═══════╗");
        s.sendMessage("§6║ §e/alf §7- Compact status             §6║");
        s.sendMessage("§6║ §e/alf gui §7- Dashboard GUI          §6║");
        s.sendMessage("§6║ §e/alf full §7- Detailed status       §6║");
        s.sendMessage("§6║ §e/alf clear §7- Clear entities       §6║");
        s.sendMessage("§6║ §e/alf reload §7- Reload configs      §6║");
        s.sendMessage("§6║ §e/alf gc §7- Garbage collection      §6║");
        s.sendMessage("§6║ §e/alf help §7- Show this help        §6║");
        s.sendMessage("§6╚═══════════════════════════════════════╝");
        s.sendMessage("§7Tab-complete available for all commands!");
    }
}
