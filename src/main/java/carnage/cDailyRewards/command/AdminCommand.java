package carnage.cDailyRewards.command;

import carnage.cDailyRewards.CDailyRewards;
import carnage.cDailyRewards.ClaimService;
import carnage.cDailyRewards.voucher.VoucherManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** /cdr reload | reset | setday | skipcooldown | give | voucher give | info */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("reload", "reset", "setday", "skipcooldown", "give", "voucher",
            "info");

    private final CDailyRewards plugin;

    public AdminCommand(CDailyRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "admin-help", Map.of());
            return true;
        }
        String sub = args[0].toLowerCase();
        String perm = switch (sub) {
            case "skipcooldown" -> "cdr.admin.cooldown";
            default -> "cdr.admin." + sub;
        };
        if (!sender.hasPermission(perm)) {
            plugin.messages().send(sender, "no-permission", Map.of());
            return true;
        }

        switch (sub) {
            case "reload" -> {
                plugin.reload();
                plugin.messages().send(sender, "reloaded", Map.of());
            }
            case "reset" -> withTarget(sender, args, 1, target -> plugin.data().edit(target.getUniqueId(), d -> {
                d.reset();
                d.totalClaims(0);
                plugin.messages().send(sender, "admin-reset", Map.of("%player%", name(target)));
            }));
            case "setday" -> withTarget(sender, args, 1, target -> {
                Integer day = parseDay(sender, args, 2);
                if (day == null)
                    return;
                plugin.data().edit(target.getUniqueId(), d -> {
                    d.day(day);
                    d.finished(false);
                    plugin.messages().send(sender, "admin-setday",
                            Map.of("%player%", name(target), "%day%", String.valueOf(day)));
                });
            });
            case "skipcooldown" -> withTarget(sender, args, 1, target -> plugin.data().edit(target.getUniqueId(), d -> {
                d.cooldownSkipped(true);
                plugin.messages().send(sender, "admin-skip", Map.of("%player%", name(target)));
            }));
            case "give" -> {
                Player target = online(sender, args, 1);
                if (target == null)
                    return true;
                Integer day = parseDay(sender, args, 2);
                if (day == null)
                    return true;
                plugin.claims().giveDay(target, day);
                plugin.messages().send(sender, "admin-give",
                        Map.of("%player%", target.getName(), "%day%", String.valueOf(day)));
            }
            case "voucher" -> {
                if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
                    plugin.messages().send(sender, "admin-help", Map.of());
                    return true;
                }
                Player target = online(sender, args, 2);
                if (target == null)
                    return true;
                VoucherManager.Voucher v = plugin.vouchers().get(args[3]);
                if (v == null) {
                    plugin.messages().send(sender, "voucher-unknown", Map.of("%id%", args[3]));
                    return true;
                }
                int amount = 1;
                if (args.length > 4) {
                    try {
                        amount = Math.max(1, Math.min(2304, Integer.parseInt(args[4])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                for (int i = 0; i < amount; i++) {
                    target.getInventory().addItem(plugin.vouchers().create(v, target)).values()
                            .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                }
                plugin.messages().send(sender, "voucher-given",
                        Map.of("%player%", target.getName(), "%id%", v.id(), "%amount%", String.valueOf(amount)));
            }
            case "info" -> withTarget(sender, args, 1, target -> plugin.data().edit(target.getUniqueId(), d -> {
                String last = d.lastClaim() == 0
                        ? "never"
                        : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(d.lastClaim()));
                plugin.messages().send(sender, "admin-info",
                        Map.of("%player%", name(target), "%day%", String.valueOf(d.day()), "%streak%",
                                String.valueOf(d.streak()), "%total%", String.valueOf(d.totalClaims()), "%last%", last,
                                "%can_claim%",
                                String.valueOf(plugin.claims().state(d) == ClaimService.State.AVAILABLE)));
            }));
            default -> plugin.messages().send(sender, "admin-help", Map.of());
        }
        return true;
    }

    private void withTarget(CommandSender sender, String[] args, int idx,
            java.util.function.Consumer<OfflinePlayer> action) {
        if (args.length <= idx) {
            plugin.messages().send(sender, "admin-help", Map.of());
            return;
        }
        Player online = Bukkit.getPlayerExact(args[idx]);
        if (online != null) {
            action.accept(online);
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[idx]);
        if (cached == null || !cached.hasPlayedBefore()) {
            plugin.messages().send(sender, "player-not-found", Map.of());
            return;
        }
        action.accept(cached);
    }

    private Player online(CommandSender sender, String[] args, int idx) {
        Player p = args.length > idx ? Bukkit.getPlayerExact(args[idx]) : null;
        if (p == null)
            plugin.messages().send(sender, "player-not-found", Map.of());
        return p;
    }

    private Integer parseDay(CommandSender sender, String[] args, int idx) {
        int max = plugin.settings().days;
        try {
            int day = Integer.parseInt(args[idx]);
            if (day >= 1 && day <= max)
                return day;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
        }
        plugin.messages().send(sender, "admin-invalid-day", Map.of("%max%", String.valueOf(max)));
        return null;
    }

    private static String name(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        Stream<String> options = Stream.empty();
        if (args.length == 1) {
            options = SUBS.stream();
        } else if (args.length == 2) {
            options = args[0].equalsIgnoreCase("voucher")
                    ? Stream.of("give")
                    : args[0].equalsIgnoreCase("reload")
                            ? Stream.empty()
                            : Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("voucher")) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("voucher")) {
            options = plugin.vouchers().ids().stream();
        }
        String last = args[args.length - 1].toLowerCase();
        return options.filter(s -> s.toLowerCase().startsWith(last)).toList();
    }
}
