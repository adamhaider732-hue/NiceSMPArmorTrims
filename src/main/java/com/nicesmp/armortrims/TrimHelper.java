package com.nicesmp.armortrims;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;

public class TrimHelper {

    public static String getFullSetTrimMaterial(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        if (!isArmorPiece(helmet) || !isArmorPiece(chestplate) ||
            !isArmorPiece(leggings) || !isArmorPiece(boots)) {
            return null;
        }

        String h = getTrimMaterial(helmet);
        String c = getTrimMaterial(chestplate);
        String l = getTrimMaterial(leggings);
        String b = getTrimMaterial(boots);

        if (h == null || c == null || l == null || b == null) return null;
        if (h.equals(c) && c.equals(l) && l.equals(b)) return h;
        return null;
    }

    private static boolean isArmorPiece(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getItemMeta() instanceof ArmorMeta;
    }

    private static String getTrimMaterial(ItemStack item) {
        if (item == null) return null;
        if (!(item.getItemMeta() instanceof ArmorMeta meta)) return null;
        ArmorTrim trim = meta.getTrim();
        if (trim == null) return null;
        TrimMaterial mat = trim.getMaterial().value();
        if (mat == null) return null;
        return mat.getKey().getKey();
    }
}
