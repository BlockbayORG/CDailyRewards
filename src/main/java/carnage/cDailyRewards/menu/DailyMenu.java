package carnage.cDailyRewards.menu;

import carnage.cDailyRewards.CDailyRewards;
import carnage.cDailyRewards.ClaimService;
import carnage.cDailyRewards.reward.RewardDay;
import carnage.cDailyRewards.reward.RewardTypes;
import carnage.cDailyRewards.storage.PlayerData;
import carnage.cDailyRewards.util.Actions;
import carnage.cDailyRewards.util.ItemParser;
import carnage.cDailyRewards.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DailyMenu implements InventoryHolder {

    private static final Pattern VOUCHER_NAME = Pattern.compile("%voucher_name_([^%]+)%");

    private final CDailyRewards plugin;
    private final Player player;
    private final boolean preview;
    private final MenuConfig cfg;
    private final Inventory inventory;
    private final Map<Integer, Integer> slotDays = new HashMap<>();
    private int page;

    public DailyMenu(CDailyRewards plugin, Player player, boolean preview) {
        this.plugin = plugin;
        this.player = player;
        this.preview = preview;
        this.cfg = plugin.menuConfig();
        PlayerData d = plugin.data().get(player.getUniqueId());
        // Start on the page holding the current day.
        if (d != null && !cfg.daySlots.isEmpty()) page = Math.min(pages() - 1, (d.day() - 1) / cfg.daySlots.size());
        this.inventory = Bukkit.createInventory(this, cfg.rows * 9, Text.parse(vars().apply(cfg.title), player));
        render();
    }

    public void open() {
        player.openInventory(inventory);
        RewardTypes.playSound(player, cfg.openSound, 1f, 1f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private int pages() {
        return cfg.daySlots.isEmpty() ? 1 : Math.max(1, (int) Math.ceil(plugin.settings().days / (double) cfg.daySlots.size()));
    }

    private UnaryOperator<String> vars() {
        PlayerData d = plugin.data().get(player.getUniqueId());
        String day = d == null ? "?" : String.valueOf(d.day());
        String streak = d == null ? "0" : String.valueOf(d.streak());
        String total = d == null ? "0" : String.valueOf(d.totalClaims());
        String left = d == null ? "" : timeLeftText(d);
        return s -> s.replace("%player%", player.getName())
                .replace("%cdr_day%", day)
                .replace("%cdr_streak%", streak)
                .replace("%cdr_total%", total)
                .replace("%time_left%", left)
                .replace("%page%", String.valueOf(page + 1))
                .replace("%pages%", String.valueOf(pages()));
    }

    private String timeLeftText(PlayerData d) {
        return switch (plugin.claims().state(d)) {
            case AVAILABLE -> "now";
            case FINISHED -> "-";
            case COOLDOWN -> Text.formatDuration(plugin.claims().timeLeft(d));
        };
    }

    public void render() {
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null) return;
        UnaryOperator<String> base = vars();
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        slotDays.clear();

        for (int slot = 0; slot < cfg.layout.length; slot++) {
            MenuConfig.Button b = cfg.buttons.get(cfg.layout[slot]);
            if (b != null && b.item() != null) contents[slot] = b.item().build(player, b.item().amount(), base, null);
        }

        int perPage = cfg.daySlots.size();
        for (int i = 0; i < perPage; i++) {
            int slot = cfg.daySlots.get(i);
            int day = page * perPage + i + 1;
            if (day > plugin.settings().days) {
                contents[slot] = null;
                continue;
            }
            slotDays.put(slot, day);
            contents[slot] = dayItem(d, day, base);
        }
        inventory.setContents(contents);
    }

    private ItemStack dayItem(PlayerData d, int day, UnaryOperator<String> base) {
        String state;
        if (d.finished() || day < d.day()) state = "claimed";
        else if (day > d.day()) state = "locked";
        else state = plugin.claims().state(d) == ClaimService.State.AVAILABLE ? "available" : "cooldown";

        RewardDay rd = plugin.rewards().get(day);
        ItemParser template = rd.icon();
        if (template == null && rd.milestone()) template = cfg.milestoneStates.get(state);
        if (template == null) template = cfg.dayStates.get(state);
        if (template == null) return null;

        List<String> lore = new ArrayList<>();
        for (String line : rd.displayLines()) lore.add(resolveVouchers(line));
        UnaryOperator<String> vars = s -> base.apply(s).replace("%day%", String.valueOf(day));
        int amount = Math.max(1, Math.min(64, day));
        return template.build(player, amount, vars, Map.of("%rewards_lore%", lore));
    }

    private String resolveVouchers(String line) {
        Matcher m = VOUCHER_NAME.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(plugin.vouchers().displayName(m.group(1))));
        m.appendTail(sb);
        return sb.toString();
    }

    public void click(int slot) {
        Integer day = slotDays.get(slot);
        if (day != null) {
            PlayerData d = plugin.data().get(player.getUniqueId());
            if (!preview && d != null && day == d.day() && player.hasPermission("cdr.claim")) {
                plugin.claims().claim(player);
                render();
            }
            return;
        }
        if (slot < 0 || slot >= cfg.layout.length) return;
        MenuConfig.Button b = cfg.buttons.get(cfg.layout[slot]);
        if (b == null || b.actions().isEmpty()) return;
        Actions.run(player, b.actions(), vars(), custom -> {
            switch (custom[0]) {
                case "close" -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                case "page" -> {
                    int target = custom[1].equalsIgnoreCase("prev") ? page - 1 : page + 1;
                    if (target >= 0 && target < pages()) {
                        page = target;
                        render();
                    }
                }
                default -> {
                }
            }
        });
    }
}
