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
    private final Set<UUID> chargedPlayers = new HashSet<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Long> resinCooldown = new HashMap<>();

    public ArmorTrimsListener(ArmorTrimsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkStillness, 5L, 5L);
    }

    // -----------------------------------------
    // IRON TRIM - Knockback immunity
    // -----------------------------------------
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
        Vector current = player.getVelocity();
        Vector newVel = event.getVelocity();
        event.setVelocity(new Vector(
            current.getX(),
            newVel.getY() < 0 ? newVel.getY() : current.getY(),
            current.getZ()
        ));
    }

    // -----------------------------------------
    // GOLD TRIM - Piglin truce
    // -----------------------------------------
    @EventHandler
    public void onPiglinTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Piglin || event.getEntity() instanceof PiglinBrute)) return;
        if (!"gold".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        event.setCancelled(true);
    }

    // -----------------------------------------
    // DIAMOND TRIM - Fatal shatter explosion
    // -----------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!"diamond".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) return;

        event.setCancelled(true);
        player.setHealth(4.0);

        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5);

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

        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.sendMessage(ChatColor.AQUA + "Your diamond-trimmed armor shattered, saving your life!");
    }

    // -----------------------------------------
    // NETHERITE TRIM - 50% projectile deflect + reduced crits
    // Replaces Chain Trim completely
    // -----------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onNetheriteProjectile(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!"netherite".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        // 50% chance to deflect projectiles
        if (event.getDamager() instanceof Projectile) {
            if (Math.random() < 0.5) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Arrow) {
                    player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        new org.bukkit.inventory.ItemStack(Material.ARROW)
                    );
                }
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
                player.sendActionBar(ChatColor.GRAY + "Projectile deflected!");
                return;
            }
        }

        // Reduce incoming critical hit damage by 40%
        if (event.getDamager() instanceof Player attacker) {
            if (attacker.getFallDistance() > 0) {
                event.setDamage(event.getDamage() * 0.6);
            }
        }
    }

    // -----------------------------------------
    // COPPER TRIM - Lightning charge attack
    // -----------------------------------------
    @EventHandler
    public void onLightningStrike(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof LightningStrike)) return;
        if (!"copper".equals(TrimHelper.getFullSetTrimMaterial(player))) return;

        chargedPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1, false, true, true));
        player.sendMessage(ChatColor.YELLOW + "You are charged! Your next attack will call lightning!");

        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> chargedPlayers.remove(player.getUniqueId()), 1200L);
    }

    @EventHandler
    public void onChargedAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!chargedPlayers.contains(attacker.getUniqueId())) return;
        if (!"copper".equals(TrimHelper.getFullSetTrimMaterial(attacker))) return;

        chargedPlayers.remove(attacker.getUniqueId());
        event.setDamage(event.getDamage() * 2.0);

        if (event.getEntity() instanceof LivingEntity target) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.setFireTicks(100);
        }
        attacker.sendMessage(ChatColor.YELLOW + "Lightning strike!");
    }

    // -----------------------------------------
    // AMETHYST TRIM - See entities through walls
    // NOTE: setGlowing works server-side on Paper 1.21
    // -----------------------------------------
    private void checkStillness() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!"amethyst".equals(TrimHelper.getFullSetTrimMaterial(player))) {
                if (stillTicks.containsKey(player.getUniqueId())) {
                    stillTicks.remove(player.getUniqueId());
                    lastLocation.remove(player.getUniqueId());
                    // Remove glowing from nearby entities
                    for (Entity e : player.getNearbyEntities(30, 30, 30)) {
                        if (e instanceof LivingEntity) e.setGlowing(false);
                    }
                }
                continue;
            }

            Location current = player.getLocation();
            Location last = lastLocation.get(player.getUniqueId());

            if (last != null && current.distanceSquared(last) < 0.01) {
                int ticks = stillTicks.getOrDefault(player.getUniqueId(), 0) + 5;
                stillTicks.put(player.getUniqueId(), ticks);

                // 1.5 seconds crouching OR 3 seconds standing still
                int threshold = player.isSneaking() ? 30 : 60;
                if (ticks >= threshold) {
                    for (Entity e : player.getNearbyEntities(25, 25, 25)) {
                        if (e instanceof LivingEntity && e != player) {
                            e.setGlowing(true);
                        }
                    }
                    player.sendActionBar(ChatColor.WHITE + "Entity vision active");
                }
            } else {
                // Player moved - reset
                stillTicks.put(player.getUniqueId(), 0);
                for (Entity e : player.getNearbyEntities(30, 30, 30)) {
                    if (e instanceof LivingEntity) e.setGlowing(false);
                }
            }
            lastLocation.put(player.getUniqueId(), current.clone());
        }
    }

    // -----------------------------------------
    // LAPIS TRIM - +50% XP gain
    // -----------------------------------------
    @EventHandler
    public void onExpPickup(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!"lapis".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        event.setAmount((int) (event.getAmount() * 1.5));
    }

    // -----------------------------------------
    // RESIN TRIM - Thorns reflection + low health regen burst
    // -----------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onResinHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!"resin".equals(TrimHelper.getFullSetTrimMaterial(player))) return;
        if (event.isCancelled()) return;

        // Reflect 30% of melee damage back to attacker
        if (event.getDamager() instanceof LivingEntity attacker
                && !(event.getDamager() instanceof Projectile)) {
            double reflect = event.getFinalDamage() * 0.3;
            if (reflect > 0.5) {
                attacker.damage(reflect);
                player.getWorld().spawnParticle(
                    Particle.CRIT, player.getLocation().add(0, 1, 0),
                    8, 0.3, 0.5, 0.3, 0.05
                );
            }
        }

        // If player falls below 6 hearts after this hit, trigger regen (30s cooldown)
        double newHealth = player.getHealth() - event.getFinalDamage();
        if (newHealth > 0 && newHealth <= 12.0) {
            long now = System.currentTimeMillis();
            long last = resinCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now - last > 30000) {
                resinCooldown.put(player.getUniqueId(), now);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, 100, 1, false, true, true
                    ));
                    player.sendActionBar(ChatColor.RED + "Resin regeneration activated!");
                    player.getWorld().spawnParticle(
                        Particle.HEART, player.getLocation().add(0, 1, 0),
                        6, 0.4, 0.4, 0.4
                    );
                }, 1L);
            }
        }
    }
}
