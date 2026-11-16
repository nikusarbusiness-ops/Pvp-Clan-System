package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Allzeit-Spielerrekord (gleichzeitig online) + /rekord */
public class RecordManager implements Listener, CommandExecutor {

    private final ClanPlugin plugin;
    private final File file;
    private int record;
    private long time;

    public RecordManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "record.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.record = cfg.getInt("record", 0);
        this.time = cfg.getLong("time", 0L);
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("record", record);
        cfg.set("time", time);
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("record.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        int now = Bukkit.getOnlinePlayers().size();
        if (now > record) {
            int old = record;
            record = now;
            time = System.currentTimeMillis();
            save();
            if (old > 0) {
                Bukkit.broadcastMessage(ClanPlugin.PREFIX + "§6Neuer Spielerrekord: §e" + now + " §6Spieler gleichzeitig online!");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String date = time == 0 ? "-" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(time));
        sender.sendMessage("§8§m        §r §6Spielerrekord §8§m        ");
        sender.sendMessage("§7Allzeit-Rekord: §e" + record + " Spieler");
        sender.sendMessage("§7Aufgestellt am: §f" + date);
        sender.sendMessage("§7Aktuell online: §a" + Bukkit.getOnlinePlayers().size());
        return true;
    }
}
