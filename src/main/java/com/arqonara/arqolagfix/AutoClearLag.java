package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;

public class AutoClearLag {
    private final ArqoLagfix plugin;
    private final PerformanceMonitor monitor;
    private int interval = 300;
    private int warningTime = 60;
    private boolean enabled = true;
    private int lastCleared = 0;
    private BukkitTask countdownTask;

    public AutoClearLag(ArqoLagfix plugin, PerformanceMonitor monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
        loadConfig();
        startClearTask();
    }

    private void loadConfig() {
        interval = plugin.getConfig().getInt("clearlag.interval", 300);
        warningTime = plugin.getConfig().getInt("clearlag.warning-time", 60);
        enabled = plugin.getConfig().getBoolean("clearlag.enabled", true);
    }

    private void startClearTask() {
        if (!enabled) return;
        
        countdownTask = new BukkitRunnable() {
            int countdown = interval;
            
            public void run() {
                countdown--;
                
                if (countdown == warningTime || countdown == 30 || countdown == 10 || countdown == 5 || countdown == 3 || countdown == 1) {
                    broadcastWarning(countdown);
                }
                
                if (countdown <= 0) {
                    executeClear(null, false);
                    countdown = interval;
                }
            }
        }.runTaskTimer(plugin, 20L, 100L); // Every 5 seconds // Every 2 seconds
    }

    private void broadcastWarning(int seconds) {
        String message = "§e[AutoClearLag] §7Ground items & hostile mobs akan dibersihkan dalam §c" + seconds + " detik§7!";
        Bukkit.broadcastMessage(message);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void executeClear(Player executor, boolean isManual) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                clearEntities(executor, isManual);
            }
        });
    }

    private void clearEntities(Player executor, boolean isManual) {
        if (!isManual && monitor.getEmergencyLevel() >= 3) {
            Bukkit.broadcastMessage("§c[AutoClearLag] Skipped: Server sedang overload!");
            return;
        }

        int totalDroppedItems = 0;
        int totalProjectiles = 0;
        int totalHostileMobs = 0;
        int totalExpOrbs = 0;
        int totalAnimals = 0;
        
        List<String> clearTypes = plugin.getConfig().getStringList("clearlag.clear-types");
        if (clearTypes.isEmpty()) {
            clearTypes = new ArrayList<String>();
            clearTypes.add("DROPPED_ITEM");
            clearTypes.add("ARROW");
            clearTypes.add("TRIDENT");
            clearTypes.add("EXPERIENCE_ORB");
            clearTypes.add("HOSTILE_MOBS_NO_NAMETAG");
        }
        
        boolean clearHostiles = clearTypes.contains("HOSTILE_MOBS_NO_NAMETAG") || clearTypes.contains("HOSTILE_MOBS");
        boolean clearAnimals = clearTypes.contains("ANIMALS");

        for (World world : Bukkit.getWorlds()) {
            if (plugin.getConfig().getStringList("clearlag.skip-worlds").contains(world.getName())) {
                continue;
            }

            List<Entity> toRemove = new ArrayList<Entity>();
            
            for (Entity entity : world.getEntities()) {
                // Skip players
                if (entity instanceof Player) continue;
                
                // Skip protected entities
                if (entity.hasMetadata("NPC")) continue;
                if (entity.getScoreboardTags().contains("persistent")) continue;
                if (entity.getScoreboardTags().contains("arqolagfix.bypass")) continue;
                if (entity.hasMetadata("protected")) continue;
                
                // Skip entities with custom name (nametag)
                if (entity.customName() != null || entity.getCustomName() != null) {
                    continue;
                }
                
                // Dropped items
                if (entity instanceof Item && clearTypes.contains("DROPPED_ITEM")) {
                    Item item = (Item) entity;
                    if (item.getPickupDelay() < 32767) {
                        toRemove.add(entity);
                        totalDroppedItems++;
                    }
                }
                
                // Arrows
                else if (entity instanceof Arrow && clearTypes.contains("ARROW")) {
                    toRemove.add(entity);
                    totalProjectiles++;
                }
                
                // Tridents
                else if (entity instanceof Trident && clearTypes.contains("TRIDENT")) {
                    toRemove.add(entity);
                    totalProjectiles++;
                }
                
                // All projectiles
                else if (entity instanceof Projectile && clearTypes.contains("PROJECTILE")) {
                    toRemove.add(entity);
                    totalProjectiles++;
                }
                
                // Experience orbs
                else if (entity instanceof ExperienceOrb && clearTypes.contains("EXPERIENCE_ORB")) {
                    toRemove.add(entity);
                    totalExpOrbs++;
                }
                
                // Hostile mobs WITHOUT nametag
                else if (clearHostiles && entity instanceof Monster) {
                    Monster mob = (Monster) entity;
                    
                    // Skip if has custom name/nametag
                    if (mob.getCustomName() != null || mob.customName() != null) {
                        continue;
                    }
                    
                    // Skip if from spawner (has metadata)
                    if (mob.hasMetadata("spawner")) {
                        continue;
                    }
                    
                    // Clear immediately if manual, or if old (30+ seconds) for auto
                    if (isManual || mob.getTicksLived() > 600) {
                        toRemove.add(entity);
                        totalHostileMobs++;
                    }
                }
                
                // Animals (optional, also check nametag)
                else if (clearAnimals && (entity instanceof Animals || entity instanceof WaterMob)) {
                    if (entity.getCustomName() == null && entity.customName() == null) {
                        if (isManual || entity.getTicksLived() > 1200) { // 1 minute old
                            toRemove.add(entity);
                            totalAnimals++;
                        }
                    }
                }
            }
            
            // Remove all
            for (Entity e : toRemove) {
                e.remove();
            }
        }
        
        int totalEntities = totalDroppedItems + totalProjectiles + totalHostileMobs + totalExpOrbs + totalAnimals;
        lastCleared = totalEntities;
        
        // Broadcast results
        if (isManual && executor != null) {
            executor.sendMessage("§a§l[AutoClearLag] §7Manual pembersihan selesai!");
        } else {
            Bukkit.broadcastMessage("§a§l[AutoClearLag] §7Pembersihan selesai!");
        }
        
        Bukkit.broadcastMessage("§7├ §eTotal dibersihkan: §f" + totalEntities + " entities");
        
        if (totalDroppedItems > 0) {
            Bukkit.broadcastMessage("§7├ §6Items: §f" + totalDroppedItems);
        }
        if (totalHostileMobs > 0) {
            Bukkit.broadcastMessage("§7├ §cHostile Mobs: §f" + totalHostileMobs);
        }
        if (totalProjectiles > 0) {
            Bukkit.broadcastMessage("§7├ §bProjectiles: §f" + totalProjectiles);
        }
        if (totalExpOrbs > 0) {
            Bukkit.broadcastMessage("§7├ §aExp Orbs: §f" + totalExpOrbs);
        }
        if (totalAnimals > 0) {
            Bukkit.broadcastMessage("§7├ §2Animals: §f" + totalAnimals);
        }
        
        Bukkit.broadcastMessage("§7└ §aTPS: §f" + String.format("%.2f", monitor.getTps()[0]));
        
        plugin.getLogger().info("AutoClearLag cleared " + totalEntities + " entities (" + 
            totalDroppedItems + " items, " + totalHostileMobs + " hostile mobs, " +
            totalProjectiles + " projectiles, " + totalExpOrbs + " exp orbs)");
    }

    public void manualClear(Player executor) {
        if (executor != null) {
            executor.sendMessage("§e[AutoClearLag] Memulai pembersihan manual...");
        }
        executeClear(executor, true);
    }

    public int getLastCleared() { return lastCleared; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (enabled) {
            startClearTask();
        }
    }
}
