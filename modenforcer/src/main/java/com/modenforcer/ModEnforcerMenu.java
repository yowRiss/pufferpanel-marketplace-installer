package com.modenforcer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class ModEnforcerMenu extends ChestMenu {
    private final SimpleContainer container;
    private final ModEnforcerConfig config;

    public ModEnforcerMenu(int syncId, Inventory playerInventory, SimpleContainer container, ModEnforcerConfig config) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, container, 3);
        this.container = container;
        this.config = config;
        populateMenu();
    }

    public static SimpleContainer createContainer() {
        return new SimpleContainer(27);
    }

    public void populateMenu() {
        ItemStack filler = new ItemStack(Items.GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.literal("§8 "));

        for (int i = 0; i < 27; i++) {
            container.setItem(i, filler.copy());
        }

        // Slot 2: Real Tab Ping Switch
        ItemStack tabPing = new ItemStack(config.tabPingEnabled ? Items.ENDER_EYE : Items.ENDER_PEARL);
        tabPing.set(DataComponents.CUSTOM_NAME, Component.literal(config.tabPingEnabled ? "§a§lTab Ping Display: ACTIVE" : "§c§lTab Ping Display: DISABLED"));
        List<Component> tabPingLore = new ArrayList<>();
        tabPingLore.add(Component.literal("§7Status: " + (config.tabPingEnabled ? "§aENABLED" : "§cDISABLED")));
        tabPingLore.add(Component.literal("§8Shows real numerical ping [ms]"));
        tabPingLore.add(Component.literal("§8in the player tab list (server-side)."));
        tabPingLore.add(Component.literal(""));
        tabPingLore.add(Component.literal(config.tabPingEnabled ? "§e▶ Click to DISABLE Tab Ping" : "§e▶ Click to ACTIVATE Tab Ping"));
        tabPing.set(DataComponents.LORE, new ItemLore(tabPingLore));
        container.setItem(2, tabPing);

        // Slot 4: Master Switch
        ItemStack master = new ItemStack(config.enabled ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK);
        master.set(DataComponents.CUSTOM_NAME, Component.literal(config.enabled ? "§a§lMod Enforcement: ACTIVE" : "§c§lMod Enforcement: DISABLED"));
        List<Component> masterLore = new ArrayList<>();
        masterLore.add(Component.literal("§7Status: " + (config.enabled ? "§aENABLED" : "§cDISABLED")));
        masterLore.add(Component.literal("§8All client-side mod checks"));
        masterLore.add(Component.literal("§8are currently " + (config.enabled ? "enforced on join." : "bypassed.")));
        masterLore.add(Component.literal(""));
        masterLore.add(Component.literal(config.enabled ? "§e▶ Click to DISABLE all enforcement" : "§e▶ Click to ACTIVATE all enforcement"));
        master.set(DataComponents.LORE, new ItemLore(masterLore));
        container.setItem(4, master);

        // Slots 10..16: Configured Mods
        int startSlot = 10;
        int maxModSlots = 7;
        for (int i = 0; i < maxModSlots; i++) {
            int currentSlot = startSlot + i;
            if (i < config.requiredMods.size()) {
                ModEnforcerConfig.RequiredMod mod = config.requiredMods.get(i);
                ItemStack modItem = new ItemStack(mod.enabled ? Items.EMERALD : Items.REDSTONE);
                modItem.set(DataComponents.CUSTOM_NAME, Component.literal((mod.enabled ? "§a§l✔ " : "§c§l✖ ") + mod.name));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("§7ID: §f" + mod.id));
                lore.add(Component.literal("§7Channel: §e" + mod.channel));
                if (mod.url != null && !mod.url.isBlank()) {
                    lore.add(Component.literal("§7Link: §b" + mod.url));
                }
                lore.add(Component.literal(""));
                lore.add(Component.literal("§7Requirement: " + (mod.enabled ? "§aREQUIRED" : "§7OPTIONAL")));
                lore.add(Component.literal(mod.enabled ? "§e▶ Click to make OPTIONAL" : "§e▶ Click to make REQUIRED"));
                modItem.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(currentSlot, modItem);
            }
        }

        // Slot 20: Reload
        ItemStack reload = new ItemStack(Items.COMPASS);
        reload.set(DataComponents.CUSTOM_NAME, Component.literal("§b§lReload Configuration"));
        List<Component> reloadLore = new ArrayList<>();
        reloadLore.add(Component.literal("§7Reloads settings from disk:"));
        reloadLore.add(Component.literal("§8config/modenforcer.json"));
        reloadLore.add(Component.literal(""));
        reloadLore.add(Component.literal("§e▶ Click to Reload"));
        reload.set(DataComponents.LORE, new ItemLore(reloadLore));
        container.setItem(20, reload);

        // Slot 22: Info Book
        ItemStack info = new ItemStack(Items.BOOK);
        info.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lMod Enforcer - OP Control"));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.literal("§7Enforces required client-side"));
        infoLore.add(Component.literal("§7mods during player connection."));
        infoLore.add(Component.literal("§7Missing players see a full"));
        infoLore.add(Component.literal("§7error screen with download links."));
        infoLore.add(Component.literal("§8Only server OPs can open this GUI."));
        info.set(DataComponents.LORE, new ItemLore(infoLore));
        container.setItem(22, info);

        // Slot 24: Close
        ItemStack close = new ItemStack(Items.BARRIER);
        close.set(DataComponents.CUSTOM_NAME, Component.literal("§c§lClose Menu"));
        List<Component> closeLore = new ArrayList<>();
        closeLore.add(Component.literal("§e▶ Click to Close"));
        close.set(DataComponents.LORE, new ItemLore(closeLore));
        container.setItem(24, close);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < 27) {
            handleMenuClick(slotIndex, player);
            populateMenu();
            this.sendAllDataToRemote();
            return;
        }
        // Disallow moving items around in player inventory while GUI is open
    }

    private void handleMenuClick(int slotIndex, Player player) {
        if (slotIndex == 2) {
            boolean active = config.toggleTabPing();
            if (player instanceof ServerPlayer sp) {
                if (active) {
                    TabPingManager.broadcastUpdateAll(sp.level().getServer());
                } else {
                    TabPingManager.broadcastResetAll(sp.level().getServer());
                }
                sp.sendSystemMessage(Component.literal(active ? "§a✔ Real Tab Ping Display ENABLED" : "§c✖ Real Tab Ping Display DISABLED"), true);
            }
        } else if (slotIndex == 4) {
            boolean active = config.toggleMaster();
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(active ? "§a✔ Master Mod Enforcement ENABLED" : "§c✖ Master Mod Enforcement DISABLED"), true);
            }
        } else if (slotIndex >= 10 && slotIndex <= 16) {
            int modIdx = slotIndex - 10;
            if (modIdx < config.requiredMods.size()) {
                ModEnforcerConfig.RequiredMod mod = config.requiredMods.get(modIdx);
                boolean active = config.toggleMod(mod.id);
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("§e✔ " + mod.name + " is now " + (active ? "§aREQUIRED" : "§7OPTIONAL")), true);
                }
            }
        } else if (slotIndex == 20) {
            ModEnforcerConfig.reload();
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§b✔ Configuration reloaded from disk!"), true);
            }
        } else if (slotIndex == 24) {
            if (player instanceof ServerPlayer sp) {
                sp.closeContainer();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
