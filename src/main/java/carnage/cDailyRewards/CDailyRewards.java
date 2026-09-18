package carnage.cDailyRewards;

import carnage.cDailyRewards.command.AdminCommand;
import carnage.cDailyRewards.command.DailyCommand;
import carnage.cDailyRewards.hook.CdrExpansion;
import carnage.cDailyRewards.menu.MenuConfig;
import carnage.cDailyRewards.menu.MenuListener;
import carnage.cDailyRewards.reward.RewardRegistry;
import carnage.cDailyRewards.storage.PlayerData;
import carnage.cDailyRewards.storage.SqliteStorage;
import carnage.cDailyRewards.storage.Storage;
import carnage.cDailyRewards.storage.YamlStorage;
import carnage.cDailyRewards.util.Text;
import carnage.cDailyRewards.voucher.VoucherManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public final class CDailyRewards extends JavaPlugin implements Listener {

    private Settings settings;
    private Messages messages;
    private MenuConfig menuConfig;
    private final RewardRegistry rewards = new RewardRegistry();
    private VoucherManager vouchers;
    private DataManager data;
    private ClaimService claims;
    private MenuListener menuListener;
    private Object economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String res : new String[]{"messages.yml", "rewards.yml", "vouchers.yml", "menus/main.yml"}) {
            if (!new File(getDataFolder(), res).exists()) saveResource(res, false);
        }
        Text.init();

        vouchers = new VoucherManager(this);
        claims = new ClaimService(this);
        menuListener = new MenuListener(this);
        loadConfigs();

        Storage storage;
        try {
            storage = settings.storage.equals("SQLITE") ? new SqliteStorage(getDataFolder()) : new YamlStorage(getDataFolder());
        } catch (Exception e) {
            getLogger().severe("Could not open SQLite storage, falling back to YAML: " + e.getMessage());
            storage = new YamlStorage(getDataFolder());
        }
        data = new DataManager(this, storage);
        data.loadOnline();

        hookVault();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new CdrExpansion(this).register();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(data, this);
        pm.registerEvents(vouchers, this);
        pm.registerEvents(menuListener, this);
        pm.registerEvents(this, this);

        DailyCommand daily = new DailyCommand(this);
        AdminCommand admin = new AdminCommand(this);
        Objects.requireNonNull(getCommand("daily")).setExecutor(daily);
        Objects.requireNonNull(getCommand("daily")).setTabCompleter(daily);
        Objects.requireNonNull(getCommand("cdr")).setExecutor(admin);
        Objects.requireNonNull(getCommand("cdr")).setTabCompleter(admin);

        menuListener.startRefresh();
    }

    @Override
    public void onDisable() {
        if (menuListener != null) {
            menuListener.stopRefresh();
            menuListener.closeAll();
        }
        if (data != null) data.shutdown();
    }

    private void loadConfigs() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        messages = new Messages(yaml("messages.yml"));
        menuConfig = new MenuConfig(yaml("menus/main.yml"), getLogger());
        rewards.load(yaml("rewards.yml"), settings.days, getLogger());
        vouchers.load(yaml("vouchers.yml"));
    }

    /** Reloads every YAML file. Storage type changes need a restart. */
    public void reload() {
        menuListener.closeAll();
        loadConfigs();
        menuListener.startRefresh();
    }

    private FileConfiguration yaml(String name) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), name));
    }

    private void hookVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            getLogger().info("Vault not found, money rewards disabled");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        else getLogger().info("No Vault economy provider, money rewards disabled");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.joinNotify) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            PlayerData d = data.get(player.getUniqueId());
            if (d == null) return;
            claims.checkMissed(player, d);
            if (claims.state(d) == ClaimService.State.AVAILABLE && player.hasPermission("cdr.use")) {
                messages.send(player, "join-ready", Map.of("%day%", String.valueOf(d.day())));
                carnage.cDailyRewards.reward.RewardTypes.playSound(player, settings.joinNotifySound, 1f, 1f);
            }
        }, settings.joinNotifyDelay);
    }

    public void async(Runnable task) {
        data.runIo(task);
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public MenuConfig menuConfig() { return menuConfig; }
    public RewardRegistry rewards() { return rewards; }
    public VoucherManager vouchers() { return vouchers; }
    public DataManager data() { return data; }
    public ClaimService claims() { return claims; }

    /** Vault economy, or null. Typed as Economy only here so the class loads without Vault. */
    public Economy economy() {
        return (Economy) economy;
    }
}
