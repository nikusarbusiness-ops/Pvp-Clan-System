package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS =
            List.of("create", "invite", "accept", "leave", "kick", "disband", "info", "list", "chat");

    private final ClanManager m;

    public ClanCommand(ClanPlugin plugin) {
        this.m = plugin.clans();
    }

    private void msg(Player p, String s) {
        p.sendMessage(ClanPlugin.PREFIX + s);
    }

    private void result(Player p, String error, String ok) {
        if (error != null) msg(p, "§c" + error);
        else msg(p, ok);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("cc")) {
            chat(p, a, 0);
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "create" -> {
                if (a.length < 3) msg(p, "§cNutzung: /clan create <Name> <Tag>");
                else result(p, m.create(p, a[1], a[2]), "§aClan §e" + a[1] + " §amit Tag §e[" + a[2].toUpperCase() + "] §aerstellt!");
            }
            case "invite" -> {
                if (a.length < 2) { msg(p, "§cNutzung: /clan invite <Spieler>"); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(p, "§cSpieler nicht online."); break; }
                result(p, m.invite(p, t), "§aEinladung an §e" + t.getName() + " §agesendet.");
            }
            case "accept" -> result(p, m.accept(p), "§aDu bist dem Clan beigetreten!");
            case "leave" -> result(p, m.leave(p), "§aDu hast den Clan verlassen.");
            case "kick" -> {
                if (a.length < 2) msg(p, "§cNutzung: /clan kick <Spieler>");
                else result(p, m.kick(p, a[1]), "§aSpieler gekickt.");
            }
            case "disband" -> result(p, m.disband(p), "§aClan aufgelöst.");
            case "info" -> info(p, a.length > 1 ? m.getByName(a[1]) : m.getClan(p));
            case "list" -> list(p);
            case "chat" -> chat(p, a, 1);
            default -> help(p);
        }
        return true;
    }

    private void chat(Player p, String[] a, int from) {
        if (m.getClan(p) == null) {
            msg(p, "§cDu bist in keinem Clan.");
            return;
        }
        if (a.length <= from) {
            boolean on = m.toggleChat(p);
            msg(p, on ? "§aClan-Chat §2an§a. Alle Nachrichten gehen jetzt an deinen Clan." : "§cClan-Chat aus.");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(a, from, a.length));
        m.sendClanChat(p, text);
    }

    private void info(Player p, Clan c) {
        if (c == null) {
            msg(p, "§cClan nicht gefunden.");
            return;
        }
        p.sendMessage("§8§m        §r §6" + c.getName() + " §e[" + c.getTag() + "] §8§m        ");
        OfflinePlayer leader = Bukkit.getOfflinePlayer(c.getLeader());
        p.sendMessage("§7Leader: §f" + leader.getName());
        p.sendMessage("§7Mitglieder §8(" + c.getMembers().size() + ")§7:");
        for (UUID id : c.getMembers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            p.sendMessage("  " + (op.isOnline() ? "§a● " : "§7● ") + "§f" + op.getName());
        }
    }

    private void list(Player p) {
        List<Clan> clans = new ArrayList<>(m.all());
        if (clans.isEmpty()) {
            msg(p, "§7Es gibt noch keine Clans.");
            return;
        }
        clans.sort((x, y) -> y.getMembers().size() - x.getMembers().size());
        p.sendMessage("§8§m        §r §6Clans §8§m        ");
        for (Clan c : clans) {
            p.sendMessage("§e[" + c.getTag() + "] §f" + c.getName() + " §7(" + c.getMembers().size() + " Mitglieder)");
        }
    }

    private void help(Player p) {
        p.sendMessage("§8§m        §r §6Clan-Hilfe §8§m        ");
        p.sendMessage("§e/clan create <Name> <Tag> §7- Clan erstellen");
        p.sendMessage("§e/clan invite <Spieler> §7- Spieler einladen");
        p.sendMessage("§e/clan accept §7- Einladung annehmen");
        p.sendMessage("§e/clan leave §7- Clan verlassen");
        p.sendMessage("§e/clan kick <Spieler> §7- Mitglied kicken");
        p.sendMessage("§e/clan disband §7- Clan auflösen");
        p.sendMessage("§e/clan info [Clan] §7- Infos");
        p.sendMessage("§e/clan list §7- Alle Clans");
        p.sendMessage("§e/cc [Nachricht] §7- Clan-Chat (ohne Text: an/aus)");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!cmd.getName().equalsIgnoreCase("clan")) return Collections.emptyList();
        if (a.length == 1) {
            return SUBS.stream().filter(x -> x.startsWith(a[0].toLowerCase())).collect(Collectors.toList());
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("invite") || a[0].equalsIgnoreCase("kick"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).collect(Collectors.toList());
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("info")) {
            return m.all().stream().map(Clan::getName)
                    .filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
