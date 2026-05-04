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
                case "lapis" -> {} // handled in event listener
                case "quartz" -> {} // handled in task below with stillness check
                case "gold" -> {} // handled in event listener
                case "diamond" -> {} // handled in event listener
                case "chain" -> {} // handled in event listener
                case "copper" -> {} // handled in event listener
            }
        }
    }

    private void applyKnockbackImmunity(Player player) {
        // Apply a high resistance effect to simulate knockback immunity
        // We handle actual knockback cancel in the listener
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false, false));
    }

    private void applyEmeraldEffect(Player player) {
        // Saturation so food lasts longer (slow hunger drain)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false, false));
    }
}
