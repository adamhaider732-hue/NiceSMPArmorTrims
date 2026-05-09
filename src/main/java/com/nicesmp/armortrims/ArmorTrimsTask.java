package com.nicesmp.armortrims;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ArmorTrimsTask implements Runnable {

    private final ArmorTrimsPlugin plugin;

    public ArmorTrimsTask(ArmorTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String trim = TrimHelper.getFullSetTrimMaterial(player);
            if (trim == null) continue;

            switch (trim) {
                case "iron" -> applyKnockbackImmunity(player);
                case "emerald" -> applyEmeraldEffect(player);
                // All others handled in event listener
            }
        }
    }

    private void applyKnockbackImmunity(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false, false));
    }

    private void applyEmeraldEffect(Player player) {
        // Slow hunger drain by applying saturation
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false, false));
    }
}
