package carnage.cDailyRewards.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Item template read from config. Built per player so names/lore can hold placeholders.
 */
public final class ItemParser {

    private final Material material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final List<Object[]> enchants = new ArrayList<>();
    private final List<ItemFlag> flags = new ArrayList<>();
    private final int customModelData;
    private final Boolean glow;
    private final boolean unbreakable;
    private final String skull;

    private ItemParser(ConfigurationSection sec, Logger log) {
        String matName = sec.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            log.warning("Invalid material '" + matName + "' at " + sec.getCurrentPath() + ", using STONE");
            mat = Material.STONE;
        }
        this.material = mat;
        this.amount = Math.max(1, sec.getInt("amount", 1));
        this.name = sec.getString("name");
        this.lore = sec.getStringList("lore");
        this.customModelData = sec.getInt("custom-model-data", 0);
        this.glow = sec.contains("glow") ? sec.getBoolean("glow") : null;
        this.unbreakable = sec.getBoolean("unbreakable", false);
        this.skull = sec.getString("skull");

        ConfigurationSection ench = sec.getConfigurationSection("enchants");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (e == null) {
                    log.warning("Invalid enchant '" + key + "' at " + sec.getCurrentPath());
                    continue;
                }
                enchants.add(new Object[]{e, ench.getInt(key, 1)});
            }
        }
        for (String f : sec.getStringList("flags")) {
            try {
                flags.add(ItemFlag.valueOf(f.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("Invalid item flag '" + f + "' at " + sec.getCurrentPath());
            }
        }
    }

    public static ItemParser of(ConfigurationSection sec, Logger log) {
        return sec == null ? null : new ItemParser(sec, log);
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public String name() {
        return name;
    }

    public ItemStack build(Player player) {
        return build(player, amount, UnaryOperator.identity(), null);
    }

    /**
     * @param vars       applied to name and lore lines before parsing
     * @param loreExpand lines equal to a key are replaced by a list (e.g. %rewards_lore%); may be null
     */
    public ItemStack build(Player player, int amount, UnaryOperator<String> vars, java.util.Map<String, List<String>> loreExpand) {
        ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize() * 64));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null) meta.displayName(Text.item(vars.apply(name), player));
        if (!lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> out = new ArrayList<>();
            for (String line : lore) {
                List<String> expanded = loreExpand == null ? null : loreExpand.get(line.trim());
                if (expanded != null) {
                    for (String e : expanded) out.add(Text.item(vars.apply(e), player));
                } else {
                    out.add(Text.item(vars.apply(line), player));
                }
            }
            meta.lore(out);
        }
        for (Object[] e : enchants) meta.addEnchant((Enchantment) e[0], (Integer) e[1], true);
        if (!flags.isEmpty()) meta.addItemFlags(flags.toArray(new ItemFlag[0]));
        if (customModelData != 0) meta.setCustomModelData(customModelData);
        if (glow != null) meta.setEnchantmentGlintOverride(glow);
        if (unbreakable) meta.setUnbreakable(true);
        if (skull != null && meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(vars.apply(skull)));
        }
        item.setItemMeta(meta);
        return item;
    }
}
