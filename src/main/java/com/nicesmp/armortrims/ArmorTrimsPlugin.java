package com.nicesmp.armortrims;

import org.bukkit.plugin.java.JavaPlugin;

public class ArmorTrimsPlugin extends JavaPlugin {

    private static ArmorTrimsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(new ArmorTrimsListener(this), this);
        getServer().getScheduler().runTaskTimer(this, new ArmorTrimsTask(this), 20L, 20L);
        getLogger().info("NiceSMP Functional Armor Trims enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NiceSMP Functional Armor Trims disabled!");
    }

    public static ArmorTrimsPlugin getInstance() {
        return instance;
    }
}
