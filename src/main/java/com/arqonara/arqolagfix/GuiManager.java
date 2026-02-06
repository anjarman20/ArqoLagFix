package com.arqonara.arqolagfix;

import java.lang.management.ManagementFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class GuiManager implements Listener {
    private final ArqoLagfix plugin;
    private boolean entityCleaner = true;
    private boolean hopperLimit = true;
    private boolean redstoneLimit = true;
    private boolean chunkOpt = true;
    private boolean aiController = true;
    private boolean explosionOpt = true;
    private Map<UUID, BukkitTask> updateTasks = new HashMap<UUID, BukkitTask>();

    public GuiManager(ArqoLagfix plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lArqoLagfix §8| §aDashboard");
        updateGui(inv);
        p.openInventory(inv);
        startRealtimeUpdate(p, inv);
        plugin.getMonitor().incrementGuiUsers();
    }

    private void startRealtimeUpdate(Player p, Inventory inv) {
        if (updateTasks.containsKey(p.getUniqueId())) {
            updateTasks.get(p.getUniqueId()).cancel();
        }
        
        // Update every 3 seconds instead of 1 second
        BukkitTask task = new BukkitRunnable() {
            public void run() {
                if (p.getOpenInventory().getTopInventory().equals(inv)) {
                    updateGui(inv);
                } else {
                    cancel();
                    updateTasks.remove(p.getUniqueId());
                    plugin.getMonitor().decrementGuiUsers();
                }
            }
        }.runTaskTimer(plugin, 60L, 60L); // Every 3 seconds
        
        updateTasks.put(p.getUniqueId(), task);
    }

    private void updateGui(Inventory inv) {
        PerformanceMonitor m = plugin.getMonitor();
        
        ItemStack border = createBorder();
        int[] borderSlots = {0,1,7,8,9,17,36,44,45,52,53};
        for (int slot : borderSlots) {
            inv.setItem(slot, border);
        }
        
        inv.setItem(4, createItem(Material.NETHER_STAR, "§6§l⚡ ArqoLagfix Dashboard ⚡", 
            "§7Real-time Performance Monitor",
            "§7Status: " + m.getHealthStatus(),
            "",
            "§8» §7Auto-refresh every §e3 seconds",
            "§8» §7Lightweight mode active"));
        
        String tpsColor = m.getTps()[0] > 19.5 ? "§a" : m.getTps()[0] > 18 ? "§e" : m.getTps()[0] > 16 ? "§6" : "§c";
        inv.setItem(10, createItem(Material.CLOCK, "§a§l⏱ TPS Monitor", 
            "§71 min:  " + tpsColor + "▌▌▌▌▌▌▌▌▌▌ §f" + String.format("%.2f", m.getTps()[0]),
            "§75 min:  " + tpsColor + "▌▌▌▌▌▌▌▌▌  §f" + String.format("%.2f", m.getTps()[1]),
            "§715 min: " + tpsColor + "▌▌▌▌▌▌▌▌   §f" + String.format("%.2f", m.getTps()[2]),
            "",
            "§8» §7Target: §a20.00 TPS"));
        
        long uptimeMillis = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        long uptimeSeconds = uptimeMillis / 1000;
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        String uptimeStr = String.format("%dd %dh %dm", days, hours, minutes);
        
        inv.setItem(12, createItem(Material.CLOCK, "§e§l⏰ Server Uptime", 
            "§f" + uptimeStr,
            "",
            "§8» §7Total: §f" + String.format("%.1f hours", uptimeSeconds / 3600.0)));
        
        String cpuColor = m.getCpu() < 50 ? "§a" : m.getCpu() < 70 ? "§e" : "§c";
        inv.setItem(14, createItem(Material.REDSTONE, "§c§l⚙ CPU Usage", 
            cpuColor + "§f" + String.format("%.1f%%", m.getCpu()),
            "",
            "§8» §7Cores: §f" + MachineInfo.getCpuCores()));
        
        String ramColor = m.getRamPercent() < 60 ? "§a" : m.getRamPercent() < 75 ? "§e" : "§c";
        inv.setItem(16, createItem(Material.ENDER_CHEST, "§6§l💾 RAM", 
            ramColor + "§f" + String.format("%.2f", m.getRam()) + " GB",
            "",
            "§8» §f" + String.format("%.1f%%", m.getRamPercent())));
        
        inv.setItem(22, createItem(Material.COMMAND_BLOCK, "§b§l🖥 Machine Info", 
            "§7OS: §f" + MachineInfo.getOsName(),
            "§7CPU: §f" + MachineInfo.getCpuModelShort(),
            "§7Cores: §f" + MachineInfo.getCpuCores() + " vCPU",
            "§7RAM: §f" + String.format("%.1f GB", MachineInfo.getTotalMemory() / 1073741824.0)));
        
        inv.setItem(19, createItem(Material.PLAYER_HEAD, "§b§lPlayers", 
            "§f" + m.getTotalPlayers()));
        
        inv.setItem(21, createItem(Material.ZOMBIE_HEAD, "§d§lEntities", 
            "§f" + m.getTotalEntities()));
        
        inv.setItem(23, createItem(Material.GRASS_BLOCK, "§7§lChunks", 
            "§f" + m.getTotalChunks()));
        
        inv.setItem(25, createItem(Material.REDSTONE_TORCH, "§c§lEmergency", 
            "§fLevel: " + m.getEmergencyLevel()));
        
        inv.setItem(37, createToggle("§b§lEntity Cleaner", entityCleaner, Material.DIAMOND_SWORD));
        inv.setItem(39, createToggle("§e§lHopper Limiter", hopperLimit, Material.HOPPER));
        inv.setItem(41, createToggle("§c§lRedstone Limiter", redstoneLimit, Material.REDSTONE_TORCH));
        inv.setItem(43, createToggle("§7§lChunk Optimizer", chunkOpt, Material.CHEST_MINECART));
        inv.setItem(46, createToggle("§a§lAI Controller", aiController, Material.ZOMBIE_SPAWN_EGG));
        inv.setItem(47, createToggle("§c§l💥 Explosion", explosionOpt, Material.TNT));
        
        inv.setItem(48, createItem(Material.ANVIL, "§6§l🧹 Clear", "§7Clear entities"));
        inv.setItem(49, createItem(Material.BOOK, "§a§l📖 Reload", "§7Reload configs"));
        inv.setItem(50, createItem(Material.TNT, "§4§l💣 GC", "§7Garbage collect"));
        inv.setItem(51, createItem(Material.BARRIER, "§c§l❌ Close", "§7Close menu"));
    }

    private ItemStack createBorder() {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8");
            border.setItemMeta(meta);
        }
        return border;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggle(String name, boolean on, Material icon, String... extraLore) {
        Material dye = on ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(dye);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + (on ? " §a[ON]" : " §c[OFF]"));
            List<String> loreList = new ArrayList<String>(Arrays.asList(extraLore));
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().contains("ArqoLagfix")) return;
        e.setCancelled(true);
        
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("arqolagfix.admin")) {
            p.closeInventory();
            return;
        }

        switch (e.getSlot()) {
            case 37: entityCleaner = !entityCleaner; break;
            case 39: hopperLimit = !hopperLimit; break;
            case 41: redstoneLimit = !redstoneLimit; break;
            case 43: chunkOpt = !chunkOpt; break;
            case 46: aiController = !aiController; break;
            case 47: 
                explosionOpt = !explosionOpt;
                plugin.getExplosionOpt().setEnabled(explosionOpt);
                break;
            case 48: 
                p.closeInventory();
                plugin.getAutoClearLag().manualClear(p);
                return;
            case 49: 
                plugin.reloadCustomConfigs();
                p.sendMessage("§a[ArqoLagfix] Reloaded!");
                break;
            case 50: 
                System.gc();
                p.sendMessage("§a[ArqoLagfix] GC done!");
                break;
            case 51: 
                p.closeInventory();
                return;
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (updateTasks.containsKey(uuid)) {
            updateTasks.get(uuid).cancel();
            updateTasks.remove(uuid);
            plugin.getMonitor().decrementGuiUsers();
        }
    }
}
