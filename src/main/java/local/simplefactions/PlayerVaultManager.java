package local.simplefactions;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerVaultManager {

    public static class VaultData {
        private String name;
        private Material icon;
        private ItemStack[] contents;

        public VaultData(String name, Material icon, ItemStack[] contents) {
            this.name = name;
            this.icon = icon;
            this.contents = contents;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Material getIcon() {
            return icon;
        }

        public void setIcon(Material icon) {
            this.icon = icon;
        }

        public ItemStack[] getContents() {
            return contents;
        }

        public void setContents(ItemStack[] contents) {
            this.contents = contents;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<Integer, VaultData>> vaults = new LinkedHashMap<>();

    public PlayerVaultManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vaults.yml");
    }

    public void load() {
        vaults.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidKey : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection playerVaults = players.getConfigurationSection(uuidKey + ".vaults");
            if (playerVaults == null) continue;

            Map<Integer, VaultData> byIndex = new LinkedHashMap<>();
            for (String indexKey : playerVaults.getKeys(false)) {
                int index;
                try {
                    index = Integer.parseInt(indexKey);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                String base = uuidKey + ".vaults." + index;
                String name = players.getString(base + ".name", "PV " + index);
                Material icon = Material.matchMaterial(players.getString(base + ".icon", "DIAMOND"));
                if (icon == null || !icon.isItem() || icon == Material.AIR) {
                    icon = Material.DIAMOND;
                }

                ItemStack[] contents = new ItemStack[54];
                Object raw = players.get(base + ".contents");
                if (raw instanceof java.util.List<?> list) {
                    for (int i = 0; i < Math.min(54, list.size()); i++) {
                        Object value = list.get(i);
                        if (value instanceof ItemStack stack) {
                            contents[i] = stack;
                        }
                    }
                }

                byIndex.put(index, new VaultData(name, icon, contents));
            }

            vaults.put(uuid, byIndex);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, Map<Integer, VaultData>> playerEntry : vaults.entrySet()) {
            String playerBase = "players." + playerEntry.getKey() + ".vaults";
            for (Map.Entry<Integer, VaultData> vaultEntry : playerEntry.getValue().entrySet()) {
                int index = vaultEntry.getKey();
                VaultData data = vaultEntry.getValue();
                String base = playerBase + "." + index;

                yaml.set(base + ".name", data.getName());
                yaml.set(base + ".icon", data.getIcon().name());
                yaml.set(base + ".contents", data.getContents());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save vaults.yml: " + e.getMessage());
        }
    }

    public VaultData getOrCreateVault(UUID playerId, int index) {
        Map<Integer, VaultData> byIndex = vaults.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        return byIndex.computeIfAbsent(index,
                ignored -> new VaultData("PV " + index, Material.DIAMOND, new ItemStack[54]));
    }

    public Map<Integer, VaultData> getVaults(UUID playerId) {
        return vaults.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
    }
}
