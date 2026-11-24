package carnage.cDailyRewards.reward;

import carnage.cDailyRewards.CDailyRewards;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public record RewardContext(CDailyRewards plugin, Player player, int day, double multiplier) {

    public String apply(String s) {
        return s.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString())
                .replace("%day%", String.valueOf(day));
    }

    /** Gives items, dropping overflow at the player's feet. */
    public void giveItem(ItemStack item) {
        int remaining = item.getAmount();
        int max = item.getMaxStackSize();
        while (remaining > 0) {
            ItemStack part = item.clone();
            part.setAmount(Math.min(max, remaining));
            remaining -= part.getAmount();
            Map<Integer, ItemStack> left = player.getInventory().addItem(part);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                plugin.claims().markDropped(player);
            }
        }
    }
}
