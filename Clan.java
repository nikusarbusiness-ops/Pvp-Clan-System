package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Clan {

    private final String name;
    private final String tag;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Clan(String name, String tag, UUID leader) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() { return name; }
    public String getTag() { return tag; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public Set<UUID> getMembers() { return members; }

    /** Prefix für Name, Tablist und Chat. */
    public String getPrefix() {
        return "§8[§e" + tag + "§8] §r";
    }

    public void broadcast(String message) {
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(message);
        }
    }
}
