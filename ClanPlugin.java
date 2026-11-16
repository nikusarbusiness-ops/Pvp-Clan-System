package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ClanPlugin extends JavaPlugin {

    public static final String PREFIX = "§8[§6ClanPvP§8] §7";

    private ClanManager clans;
    private CombatManager combat;
    private LootMenu loot;
    private RecordManager records;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        clans = new ClanManager(this);
        loot = new LootMenu(this);
        combat = new CombatManager(this);
        records = new RecordManager(this);

        Bukkit.getPluginManager().registerEvents(clans, this);
        Bukkit.getPluginManager().registerEvents(loot, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(records, this);

        ClanCommand cmd = new ClanCommand(this);
        for (String name : new String[]{"clan", "cc"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmd);
                c.setTabCompleter(cmd);
            }
        }
        PluginCommand rec = getCommand("rekord");
        if (rec != null) rec.setExecutor(records);

        for (Player p : Bukkit.getOnlinePlayers()) clans.refresh(p);
        getLogger().info("ClanPvP aktiviert.");
    }

    @Override
    public void onDisable() {
        if (loot != null) loot.shutdown();
        if (clans != null) clans.save();
        if (records != null) records.save();
    }

    public ClanManager clans() { return clans; }
    public CombatManager combat() { return combat; }
    public LootMenu loot() { return loot; }
    public RecordManager records() { return records; }
}
