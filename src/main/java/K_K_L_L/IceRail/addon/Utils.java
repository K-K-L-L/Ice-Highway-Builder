package K_K_L_L.IceRail.addon;

import baritone.api.BaritoneAPI;
import baritone.api.process.IBuilderProcess;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlaceBlock;

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
}
