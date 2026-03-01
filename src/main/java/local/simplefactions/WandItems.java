package local.simplefactions;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class WandItems {

    private static final String TNT_WAND_NAME = "§c§lTNT Wand";
    private static final String SELL_WAND_NAME = "§6§lSell Wand";
    private static final String TNT_WAND_MARKER = "§8simplefactions:tnt_wand";
    private static final String SELL_WAND_MARKER = "§8simplefactions:sell_wand";

    private WandItems() {
    }

    public static ItemStack createTntWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(TNT_WAND_NAME);
        meta.setLore(List.of(
            "§7Left-click a chest/container",
                "§7to instantly deposit all TNT",
                "§7into your faction TNT bank.",
                TNT_WAND_MARKER
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        wand.setItemMeta(meta);
        return wand;
    }

    public static ItemStack createSellWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(SELL_WAND_NAME);
        meta.setLore(List.of(
            "§7Left-click a chest/container",
                "§7to trigger sell-all behavior.",
                SELL_WAND_MARKER
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        wand.setItemMeta(meta);
        return wand;
    }

    public static boolean isTntWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        if (!TNT_WAND_NAME.equals(meta.getDisplayName())) return false;
        return meta.hasLore() && meta.getLore() != null && meta.getLore().contains(TNT_WAND_MARKER);
    }

    public static boolean isSellWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        if (!SELL_WAND_NAME.equals(meta.getDisplayName())) return false;
        return meta.hasLore() && meta.getLore() != null && meta.getLore().contains(SELL_WAND_MARKER);
    }
}
