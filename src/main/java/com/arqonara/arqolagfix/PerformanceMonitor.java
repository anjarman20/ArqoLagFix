package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class PerformanceMonitor {
    private final ArqoLagfix plugin;
    private double[] tps = {20.0, 20.0, 20.0};
    private double mspt = 50.0;
    private double cpu = 0.0, ramGb = 0.0, ramPercent = 0.0;
    private long totalEntities = 0, totalChunks = 0, totalPlayers = 0;
    private Map<String, Long> worldEntityCounts = new HashMap<String, Long>();
    private Map<String, Integer> worldChunks = new HashMap<String, Integer>();
    private BukkitTask lightTask;
    private int emergencyLevel = 0;
    
    private long lastFullUpdate = 0;
    private long lastCpuUpdate = 0;
    private long fullUpdateInterval = 10000;
    private long cpuUpdateInterval = 20000;
    private Object nmsServer = null;
    private Field tpsField = null;
    private Method getTickTimeMethod = null;
    private boolean paperApi = false;
    private int activeGuiCount = 0;

    public PerformanceMonitor(ArqoLagfix plugin) { 
        this.plugin = plugin;
        initializeReflection();
    }
    
    private void initializeReflection() {
        try {
            nmsServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            tpsField = nmsServer.getClass().getField("recentTps");
            
            try {
                getTickTimeMethod = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
                paperApi = true;
            } catch (Exception e) {
                paperApi = false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Reflection init failed");
        }
    }

    public void start() {
        lightTask = new BukkitRunnable() {
            public void run() {
                updateLightMetrics();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L);
    }

    public void stop() { 
        if (lightTask != null) lightTask.cancel();
    }

    private void updateLightMetrics() {
        try {
            if (tpsField != null) {
                double[] recentTps = (double[]) tpsField.get(nmsServer);
                tps[0] = recentTps.length > 0 ? recentTps[0] : 20.0;
                tps[1] = recentTps.length > 1 ? recentTps[1] : 20.0;
                tps[2] = recentTps.length > 2 ? recentTps[2] : 20.0;
            }
            
            if (paperApi && getTickTimeMethod != null) {
                double tickTimeNanos = (double) getTickTimeMethod.invoke(Bukkit.getServer());
                mspt = tickTimeNanos / 1_000_000.0;
            } else {
                mspt = 50.0 / Math.max(tps[0] / 20.0, 0.01);
            }
            
            Runtime runtime = Runtime.getRuntime();
            long maxRam = runtime.maxMemory();
            long usedRam = runtime.totalMemory() - runtime.freeMemory();
            ramGb = usedRam / 1073741824.0;
            ramPercent = (usedRam * 100.0) / maxRam;
            
            updateEmergencyLevel();
            
        } catch (Exception e) {
            // Silent fail
        }
    }
    
    public void updateFullMetrics() {
        long now = System.currentTimeMillis();
        
        if (now - lastCpuUpdate > cpuUpdateInterval) {
            try {
                double loadAvg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
                cpu = loadAvg > 0 ? (loadAvg * 100.0 / Runtime.getRuntime().availableProcessors()) : 0.0;
            } catch (Exception e) {
                cpu = 0.0;
            }
            lastCpuUpdate = now;
        }
        
        if (now - lastFullUpdate > fullUpdateInterval) {
            updateEntityCountsSync();
            lastFullUpdate = now;
        }
    }
    
    // FIX: Run on MAIN THREAD (sync) instead of async
    private void updateEntityCountsSync() {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                final Map<String, Long> tempEntityCounts = new HashMap<String, Long>();
                final Map<String, Integer> tempChunkCounts = new HashMap<String, Integer>();
                long tempEntities = 0;
                long tempChunks = 0;
                long tempPlayers = Bukkit.getOnlinePlayers().size();
                
                // SAFE: This runs on main thread
                for (World w : Bukkit.getWorlds()) {
                    try {
                        long entities = w.getEntities().size();
                        int chunks = w.getLoadedChunks().length;
                        tempEntityCounts.put(w.getName(), entities);
                        tempChunkCounts.put(w.getName(), chunks);
                        tempEntities += entities;
                        tempChunks += chunks;
                    } catch (Exception e) {
                        // Skip world on error
                    }
                }
                
                worldEntityCounts = tempEntityCounts;
                worldChunks = tempChunkCounts;
                totalEntities = tempEntities;
                totalChunks = tempChunks;
                totalPlayers = tempPlayers;
            }
        });
    }

    private void updateEmergencyLevel() {
        double avg = (tps[0] + tps[1] + tps[2]) / 3.0;
        if (avg < 15.0 || mspt > 100) emergencyLevel = 3;
        else if (avg < 17.0 || mspt > 70) emergencyLevel = 2;
        else if (avg < 19.0 || mspt > 55) emergencyLevel = 1;
        else emergencyLevel = 0;
    }
    
    public void incrementGuiUsers() { 
        activeGuiCount++; 
        if (activeGuiCount == 1) {
            updateFullMetrics();
        }
    }
    
    public void decrementGuiUsers() { 
        activeGuiCount = Math.max(0, activeGuiCount - 1); 
    }

    public double[] getTps() { return tps; }
    public double getMspt() { return mspt; }
    public double getCpu() { updateFullMetrics(); return cpu; }
    public double getRam() { return ramGb; }
    public double getRamPercent() { return ramPercent; }
    public long getTotalEntities() { updateFullMetrics(); return totalEntities; }
    public long getTotalChunks() { updateFullMetrics(); return totalChunks; }
    public long getTotalPlayers() { return Bukkit.getOnlinePlayers().size(); }
    public Map<String, Long> getWorldCounts() { updateFullMetrics(); return worldEntityCounts; }
    public Map<String, Integer> getWorldChunks() { updateFullMetrics(); return worldChunks; }
    public int getEmergencyLevel() { return emergencyLevel; }
    
    public String getHealthStatus() {
        if (emergencyLevel == 0) return "§a✔ HEALTHY";
        if (emergencyLevel == 1) return "§e⚠ WARNING";
        if (emergencyLevel == 2) return "§6⚠ SLOW";
        return "§c✖ CRITICAL";
    }
}
