/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Edited by K-K-L-L (Discord:theorangedot).
 */

 package K_K_L_L.IceRail.addon.modules;

 import K_K_L_L.IceRail.addon.IceRail;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.mixin.ItemStackAccessor;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.meteorclient.systems.modules.Modules;
 import meteordevelopment.meteorclient.utils.Utils;
 import meteordevelopment.meteorclient.utils.player.InvUtils;
 import meteordevelopment.meteorclient.utils.player.SlotUtils;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.block.ShulkerBoxBlock;
 import net.minecraft.client.MinecraftClient;
 import net.minecraft.inventory.Inventory;
 import net.minecraft.item.*;
 
 import java.util.Objects;
 
 public class IceRailAutoReplenish extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private static final MinecraftClient mc = MinecraftClient.getInstance();
 
     private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
             .name("threshold")
             .description("The threshold of items left to trigger replenishment.")
             .defaultValue(16)
             .min(1)
             .sliderRange(1, 63)
             .build()
     );
 
     private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
             .name("delay")
             .description("Tick delay between replenishment checks.")
             .defaultValue(1)
             .min(0)
             .build()
     );
 
     private final Setting<Item> slot3Item = sgGeneral.add(new ItemSetting.Builder()
             .name("slot-3-item")
             .description("Item to maintain in the third hotbar slot.")
             .defaultValue(Items.AIR)
             .build()
     );
 
     private final Setting<Item> slot4Item = sgGeneral.add(new ItemSetting.Builder()
             .name("slot-4-item")
             .description("Item to maintain in the fourth hotbar slot.")
             .defaultValue(Items.AIR)
             .build()
     );
 
     private final Setting<Item> slot5Item = sgGeneral.add(new ItemSetting.Builder()
             .name("slot-5-item")
             .description("Item to maintain in the fifth hotbar slot.")
             .defaultValue(Items.AIR)
             .build()
     );
 
     private final Setting<Item> slot6Item = sgGeneral.add(new ItemSetting.Builder()
             .name("slot-6-item")
             .description("Item to maintain in the sixth hotbar slot.")
             .defaultValue(Items.AIR)
             .build()
     );
 
     private final Setting<Item> slot7Item = sgGeneral.add(new ItemSetting.Builder()
             .name("slot-7-item")
             .description("Item to maintain in the seventh hotbar slot.")
             .defaultValue(Items.AIR)
             .build()
     );
 
     private final ItemStack[] items = new ItemStack[10];
     private int tickDelayLeft;
 
     public IceRailAutoReplenish() {
         super(IceRail.CATEGORY, "ice-rail-auto-replenish",
                 "Automatically refills specific items in each hotbar slot. %nSlot 1 = Pickaxe, Slot 2 = Netherrack, Slot 8 = Pickaxe shulker, Slot 9 = Blue Ice Shulker");
 
         for (int i = 0; i < items.length; i++) items[i] = new ItemStack(Items.AIR);
     }
 
     @Override
     public void onActivate() {
         fillItems();
         tickDelayLeft = tickDelay.get();
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         fillItems();
 
         boolean flag = false;
 
         if (tickDelayLeft <= 0) {
             tickDelayLeft = tickDelay.get();
 
             findAndMoveBestToolToFirstHotbarSlot();
             checkBlueIceShulkerSlot();
             checkPicksShulkerSlot();
 
             Item[] itemsToCheck = new Item[]{
                     slot3Item.get(),
                     slot4Item.get(), slot5Item.get(),
                     slot6Item.get(), slot7Item.get()
             };
             
             for (int i = 1; i <= 5; i++) {
                 if (!itemsToCheck[i - 1].equals(Items.AIR) && !flag) flag = true;
                 if (i == 1) {
                    checkSlotWithDesignatedItem(1, Items.NETHERRACK);
                 } else {
                    checkSlotWithDesignatedItem(i, itemsToCheck[i - 1]);
                 }
             }
         }
         else {
             tickDelayLeft--;
             return;
         }
 
         if (!flag) {
             error("Ice Rail Auto Replenish is not configured correctly, please configure the module and enable the \"Ice Highway Builder\" module once again.");
             Module iceHighwayBuilder = Modules.get().get("ice-highway-builder");
             if (iceHighwayBuilder.isActive()) iceHighwayBuilder.toggle();
             toggle();
         }
     }
 
     private void findAndMoveBestToolToFirstHotbarSlot() {
         MinecraftClient mc = MinecraftClient.getInstance();
         Inventory inventory = mc.player.getInventory();
 
         int firstHotbarSlot = 0;
         ItemStack firstHotbarStack = inventory.getStack(firstHotbarSlot);
 
         if (firstHotbarStack.getItem() instanceof PickaxeItem
                 && firstHotbarStack.getMaxDamage() - firstHotbarStack.getDamage() > 50) {
             return; // The first slot already has a valid pickaxe
         }
 
         int bestSlot = -1;
 
         for (int i = 0; i < inventory.size(); i++) {
             if (i == firstHotbarSlot) continue; // Skip the first slot
 
             ItemStack stack = inventory.getStack(i);
             if (stack.getItem() instanceof PickaxeItem
                     && stack.getMaxDamage() - stack.getDamage() > 50) {
                 bestSlot = i;
                 break;
             }
         }
 
         if (bestSlot != -1) {
             InvUtils.move().from(bestSlot).toHotbar(firstHotbarSlot);
         }
     }
 
     private void checkBlueIceShulkerSlot() {
         assert mc.player != null;
 
         ItemStack currentStack = mc.player.getInventory().getStack(8); // 9th slot (0-indexed)
 
         if (!(currentStack.getItem() instanceof BlockItem &&
                 ((BlockItem) currentStack.getItem()).getBlock() instanceof ShulkerBoxBlock) ||
                 !hasBlueiceInShulker(currentStack)) {
 
             ItemStack shulkerWithBlueIce = findBestBlueIceShulker();
 
             if (shulkerWithBlueIce != null) {
                 int sourceSlot = findItemStackSlot(shulkerWithBlueIce);
                 if (sourceSlot != -1) {
                     InvUtils.move().from(sourceSlot).to(8); // 9th slot (0-indexed)
                 }
             }
         }
     }
 
     private void checkPicksShulkerSlot() {
         assert mc.player != null;
 
         ItemStack currentStack = mc.player.getInventory().getStack(7); // 8th slot (0-indexed)
 
         if (!(currentStack.getItem() instanceof BlockItem &&
                 ((BlockItem) currentStack.getItem()).getBlock() instanceof ShulkerBoxBlock) ||
                 !hasPicksInShulker(currentStack)) {
 
             ItemStack shulkerWithPicks = findBestPicksShulker();
 
             if (shulkerWithPicks != null) {
                 int sourceSlot = findItemStackSlot(shulkerWithPicks);
                 if (sourceSlot != -1) {
                     InvUtils.move().from(sourceSlot).to(7); // 8th slot (0-indexed)
                 }
             }
         }
     }
 
     private static boolean hasBlueiceInShulker(ItemStack shulkerBox) {
         ItemStack[] containerItems = new ItemStack[27];
         Utils.getItemsInContainerItem(shulkerBox, containerItems);
 
         for (ItemStack stack : containerItems) {
             if (!stack.isEmpty() && (stack.getItem() == Items.BLUE_ICE)) {
                 return true;
             }
         }
         return false;
     }
 
     private static boolean hasPicksInShulker(ItemStack shulkerBox) {
         ItemStack[] containerItems = new ItemStack[27];
         Utils.getItemsInContainerItem(shulkerBox, containerItems);
 
         for (ItemStack stack : containerItems) {
             if (!stack.isEmpty() && (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE)) {
                 if (stack.getDamage() < stack.getMaxDamage() - 50) {
                     return true;
                 }
             }
         }
         return false;
     }

     public static int findPickToSwap(ItemStack shulkerBox) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulkerBox, containerItems);
        int i;
        i = 0;
        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE)) {
                if (stack.getDamage() < stack.getMaxDamage() - 50) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }
 
     public static ItemStack findBestBlueIceShulker() {
         for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
             ItemStack stack = mc.player.getInventory().getStack(i);
 
             if (stack.getItem() instanceof BlockItem &&
                     ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
 
                 if (hasBlueiceInShulker(stack)) {
                     return stack;
                 }
             }
         }
         return null;
     }
 
     public static ItemStack findBestPicksShulker() {
         for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
             ItemStack stack = mc.player.getInventory().getStack(i);
 
             if (stack.getItem() instanceof BlockItem &&
                     ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
 
                 if (hasPicksInShulker(stack)) {
                     return stack;
                 }
             }
         }
         return null;
     }
 
     private int findItemStackSlot(ItemStack stack) {
         for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
             if (mc.player.getInventory().getStack(i).equals(stack)) {
                 return i;
             }
         }
         return -1;
     }
 
     private void checkSlotWithDesignatedItem(int slot, Item desiredItem) {
         assert mc.player != null;
         ItemStack currentStack = mc.player.getInventory().getStack(slot);
 
         if (desiredItem == Items.AIR) return;
 
         if (currentStack.isEmpty() || currentStack.getItem() != desiredItem) {
             int foundSlot = findSpecificItem(desiredItem, slot, threshold.get());
             if (foundSlot != -1) {
                 addSlots(slot, foundSlot);
             }
         }
         else if (currentStack.isStackable() && currentStack.getCount() <= threshold.get()) {
             int foundSlot = findSpecificItem(desiredItem, slot, threshold.get() - currentStack.getCount() + 1);
             if (foundSlot != -1) {
                 addSlots(slot, foundSlot);
             }
         }
     }
 
     private int findSpecificItem(Item item, int excludedSlot, int goodEnoughCount) {
         int slot = -1;
         int count = 0;
 
         assert mc.player != null;
         for (int i = mc.player.getInventory().size() - 2; i >= 0; i--) {
             ItemStack stack = mc.player.getInventory().getStack(i);
 
             if (i != excludedSlot && stack.getItem() == item) {
                 if (stack.getCount() > count) {
                     slot = i;
                     count = stack.getCount();
 
                     if (count >= goodEnoughCount) break;
                 }
             }
         }
 
         return slot;
     }
 
     private void addSlots(int to, int from) {
         InvUtils.move().from(from).to(to);
     }
 
     private void fillItems() {
         for (int i = 0; i < 9; i++) {
             assert mc.player != null;
             setItem(i, mc.player.getInventory().getStack(i));
         }
 
         setItem(SlotUtils.OFFHAND, mc.player.getOffHandStack());
     }
 
     private void setItem(int slot, ItemStack stack) {
         if (slot == SlotUtils.OFFHAND) slot = 9;
 
         ItemStack s = items[slot];
         ((ItemStackAccessor) (Object) s).setItem(stack.getItem());
         s.setCount(stack.getCount());
         s.applyComponentsFrom(stack.getComponents());
         if (stack.isEmpty()) ((ItemStackAccessor) (Object) s).setItem(Items.AIR);
     }
 }
 