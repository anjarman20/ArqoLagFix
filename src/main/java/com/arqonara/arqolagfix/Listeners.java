package com.arqonara.arqolagfix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.entity.EntityType;

public class Listeners implements Listener {
    private final ArqoLagfix plugin;

    public Listeners(ArqoLagfix plugin) { this.plugin = plugin; }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        int level = plugin.getMonitor().getEmergencyLevel();
        if (level > 1) {
            e.setCancelled(true); // AI freeze
        }
    }
}
