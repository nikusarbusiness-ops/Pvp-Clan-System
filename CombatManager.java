package de.clanpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatManager implements Listener {

    private final ClanPlugin plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, UUID> last = new HashMap<>();

    public CombatManager(ClanPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private long durationMillis() {
        return plugin.getConfig().getInt("combat.seconds", 20) * 1000L;
    }

    public boolean inCombat(Player p) {
        Long u = until.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    private void tag(Player p, Player other) {
        boolean was = inCombat(p);
        until.put(p.getUniqueId(), System.currentTimeMillis() + durationMillis());
        last.put(p.getUniqueId(), other.getUniqueId());
        if (!was) {
            p.sendMessage(ClanPlugin.PREFIX + "§cDu bist im Kampf! §7Nicht ausloggen (" + (durationMillis() / 1000) + "s).");
        }
    }

    private void release(UUID id) {
        until.remove(id);
        last.remove(id);
    }

    private void actionBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                last.remove(en.getKey());
                it.remove();
                continue;
            }
            long left = en.getValue() - now;
            if (left <= 0) {
                it.remove();
                last.remove(en.getKey());
                actionBar(p, "§aDu bist nicht mehr im Kampf.");
                p.sendMessage(ClanPlugin.PREFIX + "§aDu bist nicht mehr im Kampf.");
            } else {
                actionBar(p, "§c⚔ Im Kampf: §f" + (int) Math.ceil(left / 1000.0) + "s");
            }
        }
    }

    // ---------------------------------------------------------------- Events

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) return;
        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) return;
        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) return;

        if (!plugin.getConfig().getBoolean("clan.friendly-fire", false) && plugin.clans().sameClan(attacker, victim)) {
            e.setCancelled(true);
            return;
        }
        tag(victim, attacker);
        tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        UUID lastId = last.get(victim.getUniqueId());
        if (killer == null && inCombat(victim) && lastId != null) {
            killer = Bukkit.getPlayer(lastId);
        }
        release(victim.getUniqueId());
        if (killer == null || killer.equals(victim) || !killer.isOnline()) return;

        if (victim.getUniqueId().equals(last.get(killer.getUniqueId()))) release(killer.getUniqueId());

        // Opfer behält sein Inventar - der Killer nimmt sich 1 Item per Menü
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        plugin.loot().start(killer, victim, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inCombat(p)) {
            UUID attackerId = last.get(p.getUniqueId());
            Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
            if (attacker != null && attacker.isOnline() && !attacker.equals(p)) {
                Bukkit.broadcastMessage(ClanPlugin.PREFIX + "§e" + p.getName() + " §chat sich im Kampf ausgeloggt!");
                plugin.loot().start(attacker, p, true);
                if (p.getUniqueId().equals(last.get(attacker.getUniqueId()))) release(attacker.getUniqueId());
            }
        }
        release(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("combat.block-commands", true)) return;
        Player p = e.getPlayer();
        if (!inCombat(p)) return;
        String base = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (base.contains(":")) base = base.substring(base.indexOf(':') + 1);
        List<String> blocked = plugin.getConfig().getStringList("combat.blocked-commands");
        if (blocked.contains(base)) {
            e.setCancelled(true);
            p.sendMessage(ClanPlugin.PREFIX + "§cIm Kampf kannst du diesen Befehl nicht nutzen!");
        }
    }
}
