package com.arqonara.arqolagfix;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class ExplosionOptimizer implements Listener {
    private final ArqoLagfix plugin;
    private final PerformanceMonitor monitor;
    private Map<Location, Long> recentExplosions = new HashMap<Location, Long>(128);
    private Map<Location, Integer> explosionCounter = new HashMap<Location, Integer>(64);
    private int totalOptimized = 0;
    private int totalPrevented = 0;
    private boolean enabled = true;
    private boolean verboseLogging = false; // DISABLE VERBOSE LOGS
    
    // Configuration
    private boolean limitRadius = true;
    private boolean preventChains = true;
    private boolean limitBlocks = true;
    private boolean reduceDamage = false;
    private float maxRadius = 8.0f;
    private int maxExplosionsPerArea = 3;
    private int chainDetectionRadius = 10;
    private long chainDetectionWindow = 500;
    
    public ExplosionOptimizer(ArqoLagfix plugin, PerformanceMonitor monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanupTask();
        plugin.getLogger().info("ExplosionOptimizer loaded - Silent mode");
    }
    
    private void loadConfig() {
        limitRadius = plugin.getConfig().getBoolean("explosion.limit-radius", true);
        preventChains = plugin.getConfig().getBoolean("explosion.prevent-chains", true);
        limitBlocks = plugin.getConfig().getBoolean("explosion.limit-blocks", true);
        reduceDamage = plugin.getConfig().getBoolean("explosion.reduce-damage", false);
        maxRadius = (float) plugin.getConfig().getDouble("explosion.max-radius", 8.0);
        maxExplosionsPerArea = plugin.getConfig().getInt("explosion.max-per-area", 3);
        chainDetectionRadius = plugin.getConfig().getInt("explosion.chain-radius", 10);
        chainDetectionWindow = plugin.getConfig().getLong("explosion.chain-window-ms", 500);
        verboseLogging = plugin.getConfig().getBoolean("explosion.verbose-logging", false);
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        if (!enabled) return;
        
        Entity entity = e.getEntity();
        Location loc = entity.getLocation();
        long now = System.currentTimeMillis();
        int emergencyLevel = monitor.getEmergencyLevel();
        
        // Check for chain reactions
        if (preventChains && isChainExplosion(loc, now)) {
            e.setCancelled(true);
            totalPrevented++;
            totalOptimized++;
            
            // Only log chain prevention (important)
            if (verboseLogging) {
                plugin.getLogger().warning("Chain explosion prevented at " + formatLocation(loc));
            }
            return;
        }
        
        float originalRadius = e.getRadius();
        float newRadius = originalRadius;
        
        // Limit radius based on emergency level or config
        if (limitRadius) {
            if (emergencyLevel > 1) {
                newRadius = originalRadius * 0.5f;
                totalOptimized++;
            } else if (originalRadius > maxRadius) {
                newRadius = maxRadius;
                totalOptimized++;
            }
        }
        
        // Specific entity handling
        if (entity instanceof TNTPrimed) {
            if (emergencyLevel >= 2) {
                newRadius = Math.min(newRadius, 4.0f);
                totalOptimized++;
            }
        }
        else if (entity instanceof Creeper) {
            Creeper creeper = (Creeper) entity;
            if (emergencyLevel >= 2) {
                newRadius *= 0.6f;
                totalOptimized++;
            }
            if (creeper.isPowered()) {
                newRadius = Math.min(newRadius, 6.0f);
                totalOptimized++;
            }
        }
        else if (entity instanceof EnderCrystal) {
            if (emergencyLevel >= 1) {
                newRadius = Math.min(newRadius, 5.0f);
                totalOptimized++;
            }
        }
        
        // Apply new radius
        if (newRadius != originalRadius) {
            e.setRadius(newRadius);
        }
        
        // Track explosion
        Location blockLoc = loc.getBlock().getLocation();
        recentExplosions.put(blockLoc, now);
        explosionCounter.put(blockLoc, explosionCounter.getOrDefault(blockLoc, 0) + 1);
        
        e.setFire(false);
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!enabled) return;
        
        int emergencyLevel = monitor.getEmergencyLevel();
        List<Block> blocks = e.blockList();
        int originalSize = blocks.size();
        
        // Limit block destruction
        if (limitBlocks && blocks.size() > 0) {
            int maxBlocks = getMaxBlocksForLevel(emergencyLevel);
            
            if (blocks.size() > maxBlocks) {
                Location center = e.getLocation();
                
                blocks.sort(new Comparator<Block>() {
                    public int compare(Block b1, Block b2) {
                        return Double.compare(
                            b1.getLocation().distanceSquared(center),
                            b2.getLocation().distanceSquared(center)
                        );
                    }
                });
                
                while (blocks.size() > maxBlocks) {
                    blocks.remove(blocks.size() - 1);
                }
                
                totalOptimized++;
                
                // Only log VERY large explosions (100+ blocks)
                if (verboseLogging && originalSize > 100) {
                    plugin.getLogger().info("Large explosion limited: " + originalSize + " -> " + blocks.size() + " blocks");
                }
            }
        }
        
        // Reduce yield during lag
        if (emergencyLevel >= 2) {
            float originalYield = e.getYield();
            e.setYield(originalYield * 0.3f);
            totalOptimized++;
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onExplosionDamage(EntityDamageByEntityEvent e) {
        if (!enabled || !reduceDamage) return;
        
        if (e.getCause() == EntityDamageByEntityEvent.DamageCause.ENTITY_EXPLOSION) {
            int emergencyLevel = monitor.getEmergencyLevel();
            if (emergencyLevel >= 2) {
                e.setDamage(e.getDamage() * 0.7);
            }
        }
    }
    
    private boolean isChainExplosion(Location loc, long now) {
        int nearbyExplosions = 0;
        
        for (Map.Entry<Location, Long> entry : recentExplosions.entrySet()) {
            long timeDiff = now - entry.getValue();
            
            if (timeDiff <= chainDetectionWindow) {
                double distance = entry.getKey().distance(loc);
                
                if (distance <= chainDetectionRadius) {
                    nearbyExplosions++;
                }
            }
        }
        
        return nearbyExplosions >= maxExplosionsPerArea;
    }
    
    private int getMaxBlocksForLevel(int level) {
        switch (level) {
            case 0: return 500;
            case 1: return 200;
            case 2: return 100;
            case 3: return 50;
            default: return 300;
        }
    }
    
    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d", 
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ());
    }
    
    private void startCleanupTask() {
        new BukkitRunnable() {
            public void run() {
                long now = System.currentTimeMillis();
                
                recentExplosions.entrySet().removeIf(entry -> 
                    now - entry.getValue() > 5000);
                
                explosionCounter.entrySet().removeIf(entry ->
                    !recentExplosions.containsKey(entry.getKey()));
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }
    
    public int getTotalOptimized() { return totalOptimized; }
    public int getTotalPrevented() { return totalPrevented; }
    public boolean isEnabled() { return enabled; }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        plugin.getLogger().info("ExplosionOptimizer " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    public void resetCounter() { 
        totalOptimized = 0;
        totalPrevented = 0;
    }
    
    public String getStats() {
        return String.format("Opt: %d | Prevented: %d", totalOptimized, totalPrevented);
    }
}
