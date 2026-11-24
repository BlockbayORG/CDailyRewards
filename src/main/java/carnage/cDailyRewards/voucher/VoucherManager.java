package carnage.cDailyRewards.voucher;

import carnage.cDailyRewards.CDailyRewards;
import carnage.cDailyRewards.util.Actions;
import carnage.cDailyRewards.util.ItemParser;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoucherManager implements Listener {

    public record Voucher(String id, ItemParser item, List<String> actions, String permission) {
    }

    private final CDailyRewards plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey uidKey;
    private final Map<String, Voucher> vouchers = new HashMap<>();
    private final Set<String> redeemed = ConcurrentHashMap.newKeySet();
    private final File redeemedFile;

    public VoucherManager(CDailyRewards plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "voucher");
        this.uidKey = new NamespacedKey(plugin, "voucher_uid");
        this.redeemedFile = new File(plugin.getDataFolder(), "data/redeemed-vouchers.txt");
        loadRedeemed();
    }

    public void load(FileConfiguration cfg) {
        vouchers.clear();
        for (String id : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(id);
            if (sec == null)
                continue;
            ItemParser item = ItemParser.of(sec.getConfigurationSection("item"), plugin.getLogger());
            if (item == null) {
                plugin.getLogger().warning("vouchers.yml: '" + id + "' has no item section");
                continue;
            }
            vouchers.put(id, new Voucher(id, item, sec.getStringList("actions"), sec.getString("permission", "")));
        }
    }

    public Voucher get(String id) {
        return vouchers.get(id);
    }

    public Set<String> ids() {
        return vouchers.keySet();
    }

    public String displayName(String id) {
        Voucher v = vouchers.get(id);
        return v == null || v.item().name() == null ? id : v.item().name();
    }

    /** Builds one voucher item with a unique id so copies can be detected. */
    public ItemStack create(Voucher voucher, Player player) {
        ItemStack item = voucher.item().build(player, 1, s -> s.replace("%player%", player.getName()), null);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, voucher.id());
        pdc.set(uidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta())
            return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(idKey, PersistentDataType.STRING);
        if (id == null)
            return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Voucher voucher = vouchers.get(id);
        if (voucher == null) {
            plugin.messages().send(player, "voucher-unknown", Map.of("%id%", id));
            return;
        }
        if (!voucher.permission().isEmpty() && !player.hasPermission(voucher.permission())) {
            plugin.messages().send(player, "voucher-no-permission", Map.of());
            return;
        }

        // Take one item off the stack in the main hand before running actions.
        ItemStack hand = player.getInventory().getItemInMainHand();
        String uid = pdc.get(uidKey, PersistentDataType.STRING);
        hand.setAmount(hand.getAmount() - 1);

        if (uid != null && !redeemed.add(uid)) {
            plugin.messages().send(player, "voucher-duplicate", Map.of());
            plugin.getLogger()
                    .warning(player.getName() + " tried to redeem a duplicated voucher '" + id + "' (" + uid + ")");
            return;
        }
        if (uid != null)
            appendRedeemed(uid);

        Actions.run(player, voucher.actions(),
                s -> s.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString()), null);
        plugin.messages().send(player, "voucher-redeemed", Map.of());
    }

    private void loadRedeemed() {
        if (!redeemedFile.exists())
            return;
        try {
            for (String line : Files.readAllLines(redeemedFile.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank())
                    redeemed.add(line.trim());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read redeemed-vouchers.txt: " + e.getMessage());
        }
    }

    private void appendRedeemed(String uid) {
        plugin.async(() -> {
            synchronized (redeemedFile) {
                try {
                    redeemedFile.getParentFile().mkdirs();
                    Files.writeString(redeemedFile.toPath(), uid + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save redeemed voucher: " + e.getMessage());
                }
            }
        });
    }
}
