package com.arqonara.arqolagfix;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class ArqoLagfix extends JavaPlugin {
    private PerformanceMonitor monitor;
    private EntityOptimizer entityOpt;
    private TileRedstoneManager tileManager;
    private ChunkOptimizer chunkOpt;
    private ExplosionOptimizer explosionOpt;
    private GuiManager guiManager;
    private AutoClearLag autoClearLag;
    private AsyncTaskScheduler asyncScheduler;
    private FileConfiguration worldsConfig;
    private FileConfiguration entitiesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadCustomConfigs();
        
        asyncScheduler = new AsyncTaskScheduler(this);
        monitor = new PerformanceMonitor(this);
        entityOpt = new EntityOptimizer(this, monitor, entitiesConfig);
        tileManager = new TileRedstoneManager(this, monitor, worldsConfig);
        chunkOpt = new ChunkOptimizer(this, monitor);
        explosionOpt = new ExplosionOptimizer(this, monitor);
        guiManager = new GuiManager(this);
        autoClearLag = new AutoClearLag(this, monitor);
        
        getCommand("arqolagfix").setExecutor(new Commands(this));
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        
        monitor.start();
        getLogger().info("§6ArqoLagfix v1.0 §aONLINE - Optimized Performance!");
    }

    @Override
    public void onDisable() {
        if (monitor != null) monitor.stop();
        if (asyncScheduler != null) asyncScheduler.shutdown();
        getLogger().info("§cArqoLagfix shutdown safely.");
    }

    public void reloadCustomConfigs() {
        File wcFile = new File(getDataFolder(), "worlds.yml");
        if (!wcFile.exists()) {
            saveResource("worlds.yml", false);
        }
        worldsConfig = YamlConfiguration.loadConfiguration(wcFile);
        
        File ecFile = new File(getDataFolder(), "entities.yml");
        if (!ecFile.exists()) {
            saveResource("entities.yml", false);
        }
        entitiesConfig = YamlConfiguration.loadConfiguration(ecFile);
        
        reloadConfig();
    }

    public PerformanceMonitor getMonitor() { return monitor; }
    public EntityOptimizer getEntityOpt() { return entityOpt; }
    public ExplosionOptimizer getExplosionOpt() { return explosionOpt; }
    public GuiManager getGui() { return guiManager; }
    public AutoClearLag getAutoClearLag() { return autoClearLag; }
    public AsyncTaskScheduler getAsyncScheduler() { return asyncScheduler; }
    public FileConfiguration getWorldsConfig() { return worldsConfig; }
    public FileConfiguration getEntitiesConfig() { return entitiesConfig; }
}
