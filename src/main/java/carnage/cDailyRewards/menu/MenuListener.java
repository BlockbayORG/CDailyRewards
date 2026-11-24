package carnage.cDailyRewards.menu;

import carnage.cDailyRewards.CDailyRewards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MenuListener implements Listener {

    private final CDailyRewards plugin;
    private BukkitTask refreshTask;

    public MenuListener(CDailyRewards plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof DailyMenu menu))
            return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;
        menu.click(event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof DailyMenu)
            event.setCancelled(true);
    }

    /** Re-renders open menus so countdowns stay live. */
    public void startRefresh() {
        stopRefresh();
        int interval = plugin.menuConfig().updateInterval;
        if (interval <= 0)
            return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof DailyMenu menu)
                    menu.render();
            }
        }, interval, interval);
    }

    public void stopRefresh() {
        if (refreshTask != null)
            refreshTask.cancel();
        refreshTask = null;
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof DailyMenu)
                p.closeInventory();
        }
    }
}
