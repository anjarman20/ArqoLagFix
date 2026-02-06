package com.arqonara.arqolagfix;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;
import java.util.HashMap;
import java.util.Map;

public class TileRedstoneManager implements Listener {
    private final PerformanceMonitor monitor;
    private final FileConfiguration worldsCfg;
    private final Map<Location, Integer> hopperCooldowns = new HashMap<Location, Integer>();
    private final Map<Location, Integer> redstoneTicks = new HashMap<Location, Integer>();

    public TileRedstoneManager(ArqoLagfix plugin, PerformanceMonitor monitor, FileConfiguration cfg) {
        this.monitor = monitor; 
        this.worldsCfg = cfg;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onHopper(InventoryMoveItemEvent e) {
        int level = monitor.getEmergencyLevel();
        if (level < 1 || !(e.getSource().getHolder() instanceof Hopper)) return;
        
        Hopper h = (Hopper) e.getSource().getHolder();
        Location loc = h.getLocation();
        int throttle = worldsCfg.getInt(loc.getWorld().getName() + ".hopper-throttle", 3);
        
        Integer cd = hopperCooldowns.get(loc);
        if (cd == null) cd = 0;
        cd++;
        hopperCooldowns.put(loc, cd);
        
        if (cd % throttle != 0 || level > 2) {
            e.setCancelled(true);
        }
        if (cd > 100) hopperCooldowns.remove(loc);
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent e) {
        int level = monitor.getEmergencyLevel();
        if (level < 1) return;
        
        Block b = e.getBlock();
        Location loc = b.getLocation();
        Integer ticks = redstoneTicks.get(loc);
        if (ticks == null) ticks = 0;
        ticks++;
        redstoneTicks.put(loc, ticks);
        
        boolean isClock = ticks > 10 || b.getType().toString().contains("REPEATER") || b.getType().toString().contains("COMPARATOR");
        if (isClock && level > 1) {
            e.setNewCurrent(0);
        } else if (level > 2) {
            e.setNewCurrent(e.getOldCurrent());
        }
        
        if (ticks > 20) redstoneTicks.remove(loc);
    }
}
