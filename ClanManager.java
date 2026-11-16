package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager implements Listener {

    private final ClanPlugin plugin;
    private final File file;
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Clan> invites = new HashMap<>();
    private final Set<UUID> clanChat = ConcurrentHashMap.newKeySet();

    public ClanManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        load();
    }

    // ---------------------------------------------------------------- Speicher

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("clans");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String tag = sec.getString(key + ".tag", "???");
            String leaderStr = sec.getString(key + ".leader");
            if (leaderStr == null) continue;
            Clan clan = new Clan(key, tag, UUID.fromString(leaderStr));
            for (String m : sec.getStringList(key + ".members")) {
                clan.getMembers().add(UUID.fromString(m));
            }
            clans.put(key.toLowerCase(), clan);
            for (UUID id : clan.getMembers()) byPlayer.put(id, clan);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String p = "clans." + c.getName();
            cfg.set(p + ".tag", c.getTag());
            cfg.set(p + ".leader", c.getLeader().toString());
            List<String> list = new ArrayList<>();
            for (UUID id : c.getMembers()) list.add(id.toString());
            cfg.set(p + ".members", list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("clans.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Abfragen

    public Clan getClan(Player p) { return byPlayer.get(p.getUniqueId()); }
    public Clan getByName(String name) { return clans.get(name.toLowerCase()); }
    public Collection<Clan> all() { return clans.values(); }

    public boolean sameClan(Player a, Player b) {
        Clan ca = byPlayer.get(a.getUniqueId());
        return ca != null && ca == byPlayer.get(b.getUniqueId());
    }

    // ---------------------------------------------------------------- Aktionen
    // Rückgabe: null = Erfolg, sonst Fehlermeldung

    public String create(Player p, String name, String tag) {
        if (byPlayer.containsKey(p.getUniqueId())) return "Du bist bereits in einem Clan.";
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return "Clan-Name: 3-16 Zeichen (A-Z, 0-9, _).";
        if (!tag.matches("[A-Za-z0-9]{2,5}")) return "Clan-Tag: 2-5 Zeichen (A-Z, 0-9).";
        if (clans.containsKey(name.toLowerCase())) return "Dieser Clan-Name ist schon vergeben.";
        for (Clan c : clans.values()) {
            if (c.getTag().equalsIgnoreCase(tag)) return "Dieser Clan-Tag ist schon vergeben.";
        }
        Clan clan = new Clan(name, tag.toUpperCase(), p.getUniqueId());
        clans.put(name.toLowerCase(), clan);
        byPlayer.put(p.getUniqueId(), clan);
        refresh(p);
        save();
        return null;
    }

    public String invite(Player inviter, Player target) {
        Clan clan = getClan(inviter);
        if (clan == null) return "Du bist in keinem Clan.";
        if (!clan.getLeader().equals(inviter.getUniqueId())) return "Nur der Clan-Leader kann einladen.";
        if (byPlayer.containsKey(target.getUniqueId())) return target.getName() + " ist bereits in einem Clan.";
        if (clan.getMembers().size() >= plugin.getConfig().getInt("clan.max-members", 10)) return "Dein Clan ist voll.";
        invites.put(target.getUniqueId(), clan);
        target.sendMessage(ClanPlugin.PREFIX + "§e" + inviter.getName() + " §7lädt dich in den Clan §e" + clan.getName()
                + " §7ein. §a/clan accept §7(60 Sekunden gültig)");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (invites.get(target.getUniqueId()) == clan) invites.remove(target.getUniqueId());
        }, 20L * 60);
        return null;
    }

    public String accept(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) return "Du bist bereits in einem Clan.";
        Clan clan = invites.remove(p.getUniqueId());
        if (clan == null || clans.get(clan.getName().toLowerCase()) != clan) return "Du hast keine gültige Einladung.";
        if (clan.getMembers().size() >= plugin.getConfig().getInt("clan.max-members", 10)) return "Der Clan ist inzwischen voll.";
        clan.getMembers().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), clan);
        refresh(p);
        save();
        clan.broadcast(ClanPlugin.PREFIX + "§e" + p.getName() + " §7ist dem Clan beigetreten.");
        return null;
    }

    public String leave(Player p) {
        Clan clan = getClan(p);
        if (clan == null) return "Du bist in keinem Clan.";
        if (clan.getLeader().equals(p.getUniqueId())) return "Als Leader nutze /clan disband.";
        clan.broadcast(ClanPlugin.PREFIX + "§e" + p.getName() + " §7hat den Clan verlassen.");
        removeMember(clan, p.getUniqueId());
        save();
        return null;
    }

    public String kick(Player leader, String targetName) {
        Clan clan = getClan(leader);
        if (clan == null) return "Du bist in keinem Clan.";
        if (!clan.getLeader().equals(leader.getUniqueId())) return "Nur der Clan-Leader kann kicken.";
        UUID target = null;
        for (UUID id : clan.getMembers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) target = id;
        }
        if (target == null) return "Dieser Spieler ist nicht in deinem Clan.";
        if (target.equals(leader.getUniqueId())) return "Du kannst dich nicht selbst kicken.";
        clan.broadcast(ClanPlugin.PREFIX + "§e" + targetName + " §7wurde aus dem Clan geworfen.");
        removeMember(clan, target);
        save();
        return null;
    }

    public String disband(Player p) {
        Clan clan = getClan(p);
        if (clan == null) return "Du bist in keinem Clan.";
        if (!clan.getLeader().equals(p.getUniqueId())) return "Nur der Clan-Leader kann den Clan auflösen.";
        clan.broadcast(ClanPlugin.PREFIX + "§cDer Clan §e" + clan.getName() + " §cwurde aufgelöst.");
        for (UUID id : new ArrayList<>(clan.getMembers())) removeMember(clan, id);
        clans.remove(clan.getName().toLowerCase());
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(teamName(clan));
        if (t != null) t.unregister();
        save();
        return null;
    }

    private void removeMember(Clan clan, UUID id) {
        clan.getMembers().remove(id);
        byPlayer.remove(id);
        clanChat.remove(id);
        Player online = Bukkit.getPlayer(id);
        if (online != null) refresh(online);
    }

    // ---------------------------------------------------------------- Clan-Chat

    public boolean toggleChat(Player p) {
        if (!clanChat.remove(p.getUniqueId())) {
            clanChat.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public void sendClanChat(Player p, String message) {
        Clan clan = getClan(p);
        if (clan == null) return;
        clan.broadcast("§8[§bClan§8] §7" + p.getName() + "§8: §f" + message);
    }

    // ---------------------------------------------------------------- Tags / Namen

    private String teamName(Clan clan) {
        return "cp_" + Integer.toHexString(clan.getName().toLowerCase().hashCode());
    }

    /** Setzt Clan-Tag über dem Kopf, in der Tablist und im Chat-Namen. */
    public void refresh(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith("cp_")) t.removeEntry(p.getName());
        }
        Clan clan = byPlayer.get(p.getUniqueId());
        if (clan == null) {
            p.setDisplayName(p.getName());
            return;
        }
        String tn = teamName(clan);
        Team team = sb.getTeam(tn);
        if (team == null) team = sb.registerNewTeam(tn);
        team.setAllowFriendlyFire(true);
        team.setPrefix(clan.getPrefix());
        team.addEntry(p.getName());
        p.setDisplayName(clan.getPrefix() + p.getName() + "§r");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        invites.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (byPlayer.containsKey(p.getUniqueId()) && clanChat.contains(p.getUniqueId())) {
            e.setCancelled(true);
            String msg = e.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> sendClanChat(p, msg));
            return;
        }
        e.setFormat("%1$s §8» §f%2$s");
    }
}
