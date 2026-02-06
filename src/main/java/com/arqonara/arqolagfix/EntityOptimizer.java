package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class EntityOptimizer {
    private final ArqoLagfix plugin;
    private final PerformanceMonitor monitor;
    private final FileConfiguration entitiesCfg;
    private final Set<String> whitelist = new HashSet<String>();
    private final Map<EntityType, Boolean> mobAi = new HashMap<EntityType, Boolean>();
    private int processedChunks = 0;
    private int maxChunksPerTick = 5; // Process only 5 chunks per tick

    public EntityOptimizer(ArqoLagfix plugin, PerformanceMonitor monitor, FileConfiguration cfg) {
        this.plugin = plugin; 
        this.monitor = monitor; 
        this.entitiesCfg = cfg;
        loadConfig();
        startOptimizedTasks();
    }

    private void loadConfig() {
        List<?> wl = entitiesCfg.getList("whitelist", new ArrayList<Object>());
        for (Object s : wl) {
            whitelist.add(s.toString().toUpperCase());
        }
        mobAi.put(EntityType.ZOMBIE, true);
        mobAi.put(EntityType.SKELETON, true);
        mobAi.put(EntityType.CREEPER, true);
    }

    private void startOptimizedTasks() {
        // Staggered execution - run every 10 minutes
        new BukkitRunnable() {
            public void run() {
                if (monitor.getEmergencyLevel() < 3) {
                    optimizedCleanupLoop();
                }
            }
        }.runTaskTimer(plugin, 12000L, 12000L); // Every 10 minutes
    }

    private void optimizedCleanupLoop() {
        new BukkitRunnable() {
            Iterator<World> worldIterator = Bukkit.getWorlds().iterator();
            Iterator<Chunk> chunkIterator = null;
            int removed = 0;
            
            public void run() {
                int processed = 0;
                
                while (processed < maxChunksPerTick) {
                    if (chunkIterator == null || !chunkIterator.hasNext()) {
                        if (!worldIterator.hasNext()) {
                            if (removed > 0) {
                                plugin.getLogger().info("EntityOptimizer: cleaned " + removed + " entities");
                            }
                            cancel();
                            return;
                        }
                        World world = worldIterator.next();
                        chunkIterator = Arrays.asList(world.getLoadedChunks()).iterator();
                    }
                    
                    if (chunkIterator.hasNext()) {
                        Chunk chunk = chunkIterator.next();
                        removed += processChunk(chunk);
                        processed++;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1 tick interval for smooth processing
    }

    private int processChunk(Chunk chunk) {
        int removed = 0;
        Entity[] entities = chunk.getEntities();
        int maxPerChunk = plugin.getWorldsConfig().getInt(chunk.getWorld().getName() + ".entity-limit", 50);
        
        if (entities.length > maxPerChunk) {
            List<Entity> toRemove = new ArrayList<Entity>();
            for (Entity e : entities) {
                if (shouldRemove(e)) {
                    toRemove.add(e);
                }
            }
            for (Entity e : toRemove) {
                e.remove();
                removed++;
            }
            mergeItems(chunk);
        }
        
        return removed;
    }

    private boolean shouldRemove(Entity e) {
        if (e instanceof Player || e.getScoreboardTags().contains("arqolagfix.bypass")) return false;
        if (whitelist.contains(e.getType().name())) return false;
        
        int level = monitor.getEmergencyLevel();
        if (e instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) e;
            Boolean isAggressive = mobAi.get(le.getType());
            if (isAggressive != null) {
                int threshold = isAggressive ? 3600 : 7200;
                return level > 1 && le.getTicksLived() > threshold;
            }
        }
        return level > 2 && e.getTicksLived() > 3600;
    }

    private void mergeItems(Chunk c) {
        Map<ItemStack, List<Item>> groups = new HashMap<ItemStack, List<Item>>();
        for (Entity ent : c.getEntities()) {
            if (ent instanceof Item) {
                Item item = (Item) ent;
                ItemStack key = item.getItemStack().clone();
                key.setAmount(1);
                if (!groups.containsKey(key)) {
                    groups.put(key, new ArrayList<Item>());
                }
                groups.get(key).add(item);
            }
        }
        for (List<Item> list : groups.values()) {
            if (list.size() > 1) {
                Item target = list.get(0);
                int total = 0;
                for (Item i : list) {
                    total += i.getItemStack().getAmount();
                }
                target.getItemStack().setAmount(Math.min(total, target.getItemStack().getMaxStackSize()));
                for (int j = 1; j < list.size(); j++) {
                    list.get(j).remove();
                }
            }
        }
    }

    public void manualClear() {
        // Run async to avoid blocking
        plugin.getAsyncScheduler().runAsync(() -> {
            int removed = 0;
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (shouldRemove(e)) {
                        Bukkit.getScheduler().runTask(plugin, () -> e.remove());
                        removed++;
                    }
                }
            }
            int finalRemoved = removed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Manual clear: removed " + finalRemoved + " entities");
            });
        });
    }
}
