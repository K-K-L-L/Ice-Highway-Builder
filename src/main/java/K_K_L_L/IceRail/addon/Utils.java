package K_K_L_L.IceRail.addon;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.IBuilderProcess;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import static K_K_L_L.IceRail.addon.modules.GatherItem.SEARCH_RADIUS;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlaceBlock;
import static net.minecraft.world.World.MAX_Y;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void switchToBestTool(BlockPos blockPos) {
        if (mc.world == null || mc.player == null || blockPos == null) return;

        BlockState blockState = mc.world.getBlockState(blockPos);
        double bestSpeed = 0;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() instanceof ToolItem) {
                double speed = itemStack.getMiningSpeedMultiplier(blockState);

                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    public static int countUsablePickaxes() {
        int count = 0;

        if (mc.player == null) return count;

        // Check main inventory
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (itemStack.getItem() instanceof PickaxeItem) {
                if (itemStack.getMaxDamage() - itemStack.getDamage() > 50) {
                    count++;
                }
            }
        }

        // Check offhand
        ItemStack offhandStack = mc.player.getInventory().offHand.getFirst();
        if (offhandStack.getItem() instanceof PickaxeItem) {
            if (offhandStack.getMaxDamage() - offhandStack.getDamage() > 50) {
                count++;
            }
        }

        return count;
    }

    public static void cancelCurrentProcessBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    public static FindItemResult getItemSlot(Item item) {
        return InvUtils.findInHotbar(itemStack -> itemStack.getItem() == item);
    }

    public static void switchToItem(Item item) {
        if (mc.player != null) {
            FindItemResult result = getItemSlot(item);
            if (result.found()) {
                InvUtils.swap(result.slot(), false);
            }
        }
    }

    public static void setKeyPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }

    public static boolean placeBlock(BlockPos pos, Direction direction) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return false;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, direction));

        Hand hand = Hand.OFF_HAND;

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), direction, pos, true);

        mc.interactionManager.interactBlock(mc.player, hand, hit);

        mc.player.swingHand(Hand.MAIN_HAND, false);

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, direction));

        return true;
    }

    public static boolean placeBlock(Item item, BlockPos pos, Direction direction) {
        if (!canPlaceBlock(pos, true, Block.getBlockFromItem(item))) return false;
        switchToItem(item);

        return placeBlock(pos, direction);
    }

    public static void openAndCloseInventory() { // This is to fix Ice Rail Auto Replenish not being able to work unless the inventory has been opened once
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        mc.setScreen(new InventoryScreen(mc.player));

        // Close the inventory immediately (on the next tick)
        mc.execute(() -> mc.setScreen(null));
    }

    public static void releaseForward() {
        setKeyPressed(mc.options.forwardKey, false);
    }

    public static boolean hasReachedLocation(ClientPlayerEntity player, BlockPos location) {
        return player.getBlockPos().equals(location);
    }

    private static final IBuilderProcess builderProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess();

    public static void resumeBaritone() {
        if (builderProcess.isPaused())
            builderProcess.resume();
    }

    public static void setHWCoords() {
        assert mc.player != null;
        switch (getPlayerDirection()) {
            case NORTH -> setHighwayCoords(new BlockPos(playerX, playerY, mc.player.getBlockZ() - 1));
            case SOUTH -> setHighwayCoords(new BlockPos(playerX, playerY, mc.player.getBlockZ() + 1));
            case EAST -> setHighwayCoords(new BlockPos(mc.player.getBlockX() - 1, playerY, playerZ));
            case WEST -> setHighwayCoords(new BlockPos(mc.player.getBlockX() + 1, playerY, playerZ));
        }
    }

    public static int countItems(Item item) {
        int count = 0;
        if (mc.player != null) {
            // Main inventory, hotbar, and offhand
            for (int i = 0; i <= 40; i++) {
                ItemStack itemStack = mc.player.getInventory().getStack(i);
                if (!itemStack.isEmpty() && itemStack.getItem() == item) {
                    count += itemStack.getCount();
                }
            }
        }
        return count;
    }
    public static int countEmptySlots() {
        int emptyCount = 0;
        if (mc.player != null) {
            // Loop through the player's main inventory (excluding offhand and armor)
            for (ItemStack itemStack : mc.player.getInventory().main) {
                if (itemStack.isEmpty()) {
                    emptyCount++;
                }
            }
        }
        return emptyCount;
    }

    public static boolean isGatheringItems() {
        return Modules.get().get("gather-item").isActive();
    }

    public static boolean checkItemsOnGround(List<BlockPos> locations) {
        if (locations.isEmpty()) return false;

        assert mc.player != null;
        for (BlockPos pos : locations) {
            boolean hasGroundBelow = false;

            for (int x = -1; x <= 1 && !hasGroundBelow; x++) {
                for (int z = -1; z <= 1 && !hasGroundBelow; z++) {
                    BlockPos checkPos = pos.add(x, -1, z);
                    if (!mc.player.getWorld().getBlockState(checkPos).isAir()) {
                        hasGroundBelow = true;
                    }
                }
            }

            if (!hasGroundBelow) return false;
        }

        return true;
    }
    private static ScheduledExecutorService scheduler1 = Executors.newScheduledThreadPool(1);

    private static void reinitializeScheduler1() {
        if (scheduler1 != null && !scheduler1.isShutdown()) {
            scheduler1.shutdownNow();  // Ensure the previous scheduler is properly shut down
        }
        scheduler1 = Executors.newScheduledThreadPool(1);  // Reinitialize the scheduler
    }

    public static void shutdownScheduler1() {
        if (scheduler1 != null) {
            scheduler1.shutdownNow();
            scheduler1 = null;
        }
    }

    public static void togglePaver(boolean activate) {
        meteordevelopment.meteorclient.systems.modules.Module icePlacer = Modules.get().get("ice-placer");
        meteordevelopment.meteorclient.systems.modules.Module iceRailNuker = Modules.get().get("ice-rail-nuker");

        if (icePlacer != null && activate != icePlacer.isActive())
            icePlacer.toggle();

        if (iceRailNuker != null && activate != iceRailNuker.isActive())
            iceRailNuker.toggle();
    }
    public static void goToHighwayCoords(boolean paveAfterwards) {
        assert mc.player != null;
        BlockPos target;

        target = getHighwayCoords();

        if (target == null) {
            Direction direction = getPlayerDirection();
            BlockPos target2;

            switch (direction) {
                case NORTH -> target2 = new BlockPos(playerX, playerY, mc.player.getBlockZ() + 1);
                case SOUTH -> target2 = new BlockPos(playerX, playerY, mc.player.getBlockZ() - 1);
                case EAST -> target2 = new BlockPos(mc.player.getBlockX() - 1, playerY, playerZ);
                case WEST -> target2 = new BlockPos(mc.player.getBlockX() + 1, playerY, playerZ);
                default -> target2 = null;
            }

            setHighwayCoords(target2);
            target = getHighwayCoords();
        }

        if(target != null) {
            if ((mc.player.getBlockPos() != target) && !isGatheringItems()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
                resumeBaritone();
            }
        }

        reinitializeScheduler1();
        BlockPos finalTarget = target;
        scheduler1.scheduleAtFixedRate(() -> {
            if (hasReachedLocation(mc.player, finalTarget)) {
                if(paveAfterwards)
                    togglePaver(true);
                scheduler1.shutdownNow();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static void place(BlockPos blockPos, Hand hand, int slot, boolean swingHand, boolean checkEntities, boolean swapBack) {
        if (slot < 0 || slot > 8 || mc.player == null) return;

        Block toPlace = Blocks.BEDROCK;
        ItemStack i = hand == Hand.MAIN_HAND ? mc.player.getInventory().getStack(slot) : mc.player.getInventory().getStack(SlotUtils.OFFHAND);
        if (i.getItem() instanceof BlockItem blockItem) toPlace = blockItem.getBlock();
        if (!canPlaceBlock(blockPos, checkEntities, toPlace)) return;

        Vec3d hitPos = Vec3d.ofCenter(blockPos);

        BlockPos neighbour;
        Direction side = Direction.DOWN;

        neighbour = blockPos.offset(side);
        hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

        InvUtils.swap(slot, swapBack);

        BlockUtils.interact(bhr, hand, swingHand);

        if (swapBack) InvUtils.swapBack();
    }

    public static Item[] shulkerBoxes = {
            Items.SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX
    };
    public static Item[] getShulkerBoxesNearby() {
        List<Item> nearbyShulkers = new ArrayList<>();
        if (mc.player == null || mc.world == null) return new Item[0];

        Box searchArea = new Box(
                mc.player.getX() - SEARCH_RADIUS,
                mc.player.getY() - SEARCH_RADIUS,
                mc.player.getZ() - SEARCH_RADIUS,
                mc.player.getX() + SEARCH_RADIUS,
                MAX_Y,
                mc.player.getZ() + SEARCH_RADIUS
        );

        for (ItemEntity itemEntity : mc.world.getEntitiesByClass(ItemEntity.class, searchArea, e -> true)) {
            Item item = itemEntity.getStack().getItem();
            for (Item shulkerBox : shulkerBoxes) {
                if (item == shulkerBox) {
                    nearbyShulkers.add(item);
                }
            }
        }
        return nearbyShulkers.toArray(new Item[0]);
    }

    public static boolean areShulkerBoxesNearby() {
        return getShulkerBoxesNearby().length > 0;
    }

    public static void gatherItem(Item item) {
        setModuleSetting("gather-item", "item", item);
        Module gatherItem = Modules.get().get("gather-item");
        if (!isGatheringItems()) gatherItem.toggle();
    }

    public static void setModuleSetting(String moduleName, String settingName, Object value) {
        Module module = Modules.get().get(moduleName);
        if (module == null) {
            ChatUtils.warning("Module '" + moduleName + "' not found.");
            return;
        }

        Setting<?> setting = module.settings.get(settingName);
        if (setting == null) {
            ChatUtils.warning("Setting '" + settingName + "' not found in module '" + moduleName + "'.");
            return;
        }

        try {
            // Handle different setting types
            switch (value) {
                case Integer i -> ((IntSetting) setting).set(i);
                case Double v -> ((DoubleSetting) setting).set(v);
                case String s -> ((StringSetting) setting).set(s);
                case Boolean b -> ((BoolSetting) setting).set(b);
                case Item item -> ((ItemSetting) setting).set(item);
                case null, default -> System.out.println("Utils.java setModuleSetting: Incompatible value type for setting '" + settingName + "'.");
            }

        } catch (Exception e) {
            System.out.println("Utils.java setModuleSetting: Error setting value: " + e.getMessage());
        }
    }
}
