package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class ChunkOptimizer {
    private final ArqoLagfix plugin;
    private final PerformanceMonitor monitor;

    public ChunkOptimizer(ArqoLagfix plugin, PerformanceMonitor monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
        startCleanup();
    }

    private void startCleanup() {
        new BukkitRunnable() {
            public void run() {
                if (monitor.getEmergencyLevel() > 1) {
                    for (World w : Bukkit.getWorlds()) {
                        Chunk[] chunks = w.getLoadedChunks();
                        for (Chunk c : chunks) {
                            if (c.getEntities().length == 0) {
                                w.unloadChunk(c.getX(), c.getZ(), true);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 12000L, 12000L);
    }
}
