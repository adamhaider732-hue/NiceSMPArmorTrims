package com.nicesmp.armortrims;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

public class ArmorTrimsListener implements Listener {

    private final ArmorTrimsPlugin plugin;
    // Tracks charged players (copper trim)
    private final Set<UUID> chargedPlayers = new HashSet<>();
    // Tracks still time for quartz trim (UUID -> ticks stood still)
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();

    public ArmorTrimsListener(ArmorTrimsPlugin plugin) {
        this.plugin = plugin;
        // Tick-based still detection for quartz
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkStillness, 5L, 5L);
    }

    // ─────────────────────────────────────────────
    // IRON TRIM — Knockback immunity
    // ─────────────────────────────────────────────
    @SuppressWarnings("deprecation")
@EventHandler(priority = EventPriority.HIGH)
public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!"iron".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (!"iron".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        // Cancel any velocity change from explosions/projectiles
        Vector current = player.getVelocity();
        Vector newVel = event.getVelocity();
        // Only cancel upward/horizontal knockback, not gravity
        event.setVelocity(new Vector(current.getX(), newVel.getY() < 0 ? newVel.getY() : current.getY(), current.getZ()));
    }

    // ─────────────────────────────────────────────
    // GOLD TRIM — Piglin truce
    // ─────────────────────────────────────────────
    @EventHandler
    public void onPiglinTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Piglin || event.getEntity() instanceof PiglinBrute)) return;
        if (!"gold".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        event.setCancelled(true);
    }

    // ─────────────────────────────────────────────
    // DIAMOND TRIM — Fatal shatter explosion
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!"diamond".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) return; // Not fatal

        // Cancel death, trigger shatter
        event.setCancelled(true);
        player.setHealth(4.0); // Leave them on 2 hearts after shatter

        // Knockback explosion effect — no block damage
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5);

        // Launch nearby entities away
        for (Entity nearby : player.getNearbyEntities(5, 5, 5)) {
            if (nearby == player) continue;
            Vector dir = nearby.getLocation().toVector()
                    .subtract(loc.toVector()).normalize().multiply(2.5);
            dir.setY(0.5);
            nearby.setVelocity(dir);
            if (nearby instanceof LivingEntity le) {
                le.damage(6.0, player);
            }
        }

        // Remove armor (it shatters)
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.sendMessage(ChatColor.AQUA + "Your diamond-trimmed armor shattered, saving your life!");
    }

    // ─────────────────────────────────────────────
    // CHAIN TRIM — 50% projectile nullify + shield knockback
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof Projectile)) return;
        if (!"chain".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        // 50% chance to nullify
        if (Math.random() < 0.5) {
            event.setCancelled(true);
            // Drop the projectile as an item if it's an arrow
            if (event.getDamager() instanceof Arrow) {
                player.getWorld().dropItemNaturally(player.getLocation(),
                        new org.bukkit.inventory.ItemStack(Material.ARROW));
            }
            player.sendActionBar(ChatColor.AQUA + "Projectile deflected!");
        }

        // Critical hits deal less damage
        if (event.getDamager() instanceof Player attacker) {
            if (attacker.getFallDistance() > 0) {
                event.setDamage(event.getDamage() * 0.5);
            }
        }
    }

    // ─────────────────────────────────────────────
    // COPPER TRIM — Lightning charge
    // ─────────────────────────────────────────────
    @EventHandler
    public void onLightningStrike(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof LightningStrike)) return;
        if (!"copper".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        // Become charged for 60 seconds
        chargedPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1, false, true, true));
        player.sendMessage(ChatColor.YELLOW + "⚡ You are charged! Your next attack will call lightning!");

        // Remove charge after 60 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                chargedPlayers.remove(player.getUniqueId()), 1200L);
    }

    @EventHandler
    public void onChargedAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!chargedPlayers.contains(attacker.getUniqueId())) return;
        if (!"copper".equals(TrimHelper.getFullSetTrimMaterial(attacker))) return;

        chargedPlayers.remove(attacker.getUniqueId());

        // Double damage
        event.setDamage(event.getDamage() * 2.0);

        // Summon lightning on target
        if (event.getEntity() instanceof LivingEntity target) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.setFireTicks(100);
        }
        attacker.sendMessage(ChatColor.YELLOW + "⚡ Lightning strike!");
    }

    // ─────────────────────────────────────────────
    // QUARTZ TRIM — See entities through walls
    // ─────────────────────────────────────────────
    private void checkStillness() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!"quartz".equals(TrimHelper.getFullSetTrimMaterial(player))) {
                stillTicks.remove(player.getUniqueId());
                lastLocation.remove(player.getUniqueId());
                // Remove glowing from all entities if they stop wearing quartz
                for (Entity e : player.getNearbyEntities(25, 25, 25)) {
                    if (e instanceof LivingEntity) e.setGlowing(false);
                }
                continue;
            }

            Location current = player.getLocation();
            Location last = lastLocation.get(player.getUniqueId());

            if (last != null && current.distanceSquared(last) < 0.01) {
                // Player is still
                int ticks = stillTicks.getOrDefault(player.getUniqueId(), 0) + 5;
                stillTicks.put(player.getUniqueId(), ticks);

                // After 60 ticks (3 seconds) standing still OR 30 ticks (1.5s) crouching
                int threshold = player.isSneaking() ? 30 : 60;
                if (ticks >= threshold) {
                    // Apply glowing to nearby entities
                    for (Entity e : player.getNearbyEntities(25, 25, 25)) {
                        if (e instanceof LivingEntity && e != player) {
                            e.setGlowing(true);
                        }
                    }
                    player.sendActionBar(ChatColor.WHITE + "👁 Entity vision active");
                }
            } else {
                // Player moved — reset and remove glowing
                stillTicks.put(player.getUniqueId(), 0);
                for (Entity e : player.getNearbyEntities(25, 25, 25)) {
                    if (e instanceof LivingEntity) e.setGlowing(false);
                }
            }
            lastLocation.put(player.getUniqueId(), current.clone());
        }
    }

    // ─────────────────────────────────────────────
    // LAPIS TRIM — +50% XP from orbs
    // ─────────────────────────────────────────────
    @EventHandler
    public void onExpPickup(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!"lapis".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        event.setAmount((int) (event.getAmount() * 1.5));
    }
}
