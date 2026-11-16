package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loot-Menü: Der Killer (bzw. der Gegner eines Combat-Loggers) sieht das Inventar
 * des Opfers und darf sich genau 1 Item aussuchen. Der Rest bleibt beim Opfer.
 */
public class LootMenu implements Listener {

    private static final int SIZE = 41; // 36 Inventar + 4 Rüstung + 1 Offhand

    private static class Session {
        UUID taker;
        UUID victim;
        String victimName;
        ItemStack[] snap;
        Inventory inv;
        boolean done;
        BukkitTask timeout;
    }

    private final ClanPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final File pendingFile;
    private final YamlConfiguration pending;
    private final Random random = new Random();

    public LootMenu(ClanPlugin plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), "pending.yml");
        this.pending = YamlConfiguration.loadConfiguration(pendingFile);
    }

    // ---------------------------------------------------------------- Start

    public void start(Player taker, Player victim, boolean logout) {
        ItemStack[] contents = victim.getInventory().getContents();
        ItemStack[] snap = new ItemStack[SIZE];
        boolean any = false;
        for (int i = 0; i < SIZE && i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                snap[i] = contents[i].clone();
                any = true;
            }
        }
        if (!any) {
            taker.sendMessage(ClanPlugin.PREFIX + "§7" + victim.getName() + " hatte keine Items.");
            return;
        }

        Session old = sessions.get(taker.getUniqueId());
        if (old != null) autoPick(old, null);

        Session s = new Session();
        s.taker = taker.getUniqueId();
        s.victim = victim.getUniqueId();
        s.victimName = victim.getName();
        s.snap = snap;
        s.inv = Bukkit.createInventory(null, 54, "§cLoot von " + victim.getName());

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.setDisplayName(" "); pane.setItemMeta(pm); }
        for (int i = SIZE; i < 54; i++) s.inv.setItem(i, pane);
        for (int i = 0; i < SIZE; i++) if (snap[i] != null) s.inv.setItem(i, snap[i].clone());

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§eWähle 1 Item!");
            im.setLore(List.of("§7Klicke auf das Item, das du", "§7behalten willst. Der Rest bleibt", "§7bei " + victim.getName() + "."));
            info.setItemMeta(im);
        }
        s.inv.setItem(49, info);

        sessions.put(s.taker, s);
        int secs = plugin.getConfig().getInt("loot.pick-seconds", 30);
        s.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> autoPick(s, null), secs * 20L);

        taker.sendMessage(ClanPlugin.PREFIX + (logout
                ? "§e" + victim.getName() + " §7hat sich im Kampf ausgeloggt!"
                : "§7Du hast §e" + victim.getName() + " §7getötet!")
                + " §7Wähle §e1 Item §7(" + secs + "s).");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!s.done && taker.isOnline()) taker.openInventory(s.inv);
        });
    }

    // ---------------------------------------------------------------- Auswahl

    private void autoPick(Session s, Player takerOverride) {
        if (s.done) return;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) if (s.snap[i] != null) slots.add(i);
        if (slots.isEmpty()) { s.done = true; sessions.remove(s.taker); return; }
        pick(s, slots.get(random.nextInt(slots.size())), takerOverride);
    }

    private void pick(Session s, int slot, Player takerOverride) {
        if (s.done) return;
        s.done = true;
        if (s.timeout != null) s.timeout.cancel();
        sessions.remove(s.taker);

        ItemStack item = s.snap[slot];
        Player taker = takerOverride != null ? takerOverride : Bukkit.getPlayer(s.taker);
        Player victim = Bukkit.getPlayer(s.victim);

        boolean ok = true;
        if (victim != null && victim.isOnline()) ok = removeFrom(victim, slot, item);
        else addPending(s.victim, slot, item);

        if (taker != null) {
            if (ok) {
                Map<Integer, ItemStack> left = taker.getInventory().addItem(item.clone());
                for (ItemStack rest : left.values()) taker.getWorld().dropItemNaturally(taker.getLocation(), rest);
                taker.sendMessage(ClanPlugin.PREFIX + "§aDu hast §e" + describe(item) + " §avon §e" + s.victimName + " §aerhalten!");
            } else {
                taker.sendMessage(ClanPlugin.PREFIX + "§cDas Item war nicht mehr vorhanden.");
            }
            final Player t = taker;
            Bukkit.getScheduler().runTask(plugin, () -> { if (t.isOnline()) t.closeInventory(); });
        }
        if (ok && victim != null) {
            String who = taker != null ? taker.getName() : "Dein Gegner";
            victim.sendMessage(ClanPlugin.PREFIX + "§e" + who + " §7hat dir §e" + describe(item)
                    + " §7abgenommen. Den Rest behältst du.");
        }
    }

    private boolean removeFrom(Player v, int slot, ItemStack item) {
        ItemStack cur = v.getInventory().getItem(slot);
        if (cur != null && cur.isSimilar(item)) {
            v.getInventory().setItem(slot, null);
            return true;
        }
        return v.getInventory().removeItem(item.clone()).isEmpty();
    }

    private String describe(ItemStack item) {
        ItemMeta m = item.getItemMeta();
        String name = (m != null && m.hasDisplayName()) ? m.getDisplayName()
                : item.getType().name().toLowerCase().replace('_', ' ');
        return item.getAmount() + "x " + name;
    }

    // ---------------------------------------------------------------- Offline-Opfer

    private void addPending(UUID victim, int slot, ItemStack item) {
        String key = "pending." + victim + "." + System.nanoTime();
        pending.set(key + ".slot", slot);
        pending.set(key + ".item", item);
        savePending();
    }

    private void savePending() {
        try {
            plugin.getDataFolder().mkdirs();
            pending.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().warning("pending.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String path = "pending." + p.getUniqueId();
        if (!pending.contains(path)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ConfigurationSection sec = pending.getConfigurationSection(path);
            if (sec == null || !p.isOnline()) return;
            for (String k : sec.getKeys(false)) {
                int slot = sec.getInt(k + ".slot");
                ItemStack item = sec.getItemStack(k + ".item");
                if (item == null) continue;
                removeFrom(p, slot, item);
                p.sendMessage(ClanPlugin.PREFIX + "§cDu hast dich im Kampf ausgeloggt: §e" + describe(item) + " §cwurde dir abgenommen.");
            }
            pending.set(path, null);
            savePending();
        }, 10L);
    }

    // ---------------------------------------------------------------- Menü-Events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || e.getView().getTopInventory() != s.inv) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != s.inv) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= SIZE || s.snap[slot] == null) return;
        pick(s, slot, null);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s != null && e.getView().getTopInventory() == s.inv) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.done || e.getInventory() != s.inv) return;
        // Menü wieder öffnen, bis ein Item gewählt wurde (oder die Zeit abläuft)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!s.done && p.isOnline()) p.openInventory(s.inv);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) autoPick(s, e.getPlayer());
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) autoPick(s, null);
        savePending();
    }
}
