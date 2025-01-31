package K_K_L_L.IceRail.addon.modules;

import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;

import K_K_L_L.IceRail.addon.IceRail;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import java.util.*;

import static meteordevelopment.meteorclient.utils.world.BlockUtils.getPlaceSide;

import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestBlueIceShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestPicksShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findPickToSwap;

public class IceHighwayBuilder extends Module {
    private int slotNumber;
    public static boolean isGoingToHighway;
    public static boolean isGoingToHole;
    private int stealingDelay = 0;
    private static BlockPos highwayCoords;
    public static boolean wasEchestFarmerActive = false;
    public static boolean baritoneCalled;
    public static Direction playerDirection;
    private float playerYaw;
    private float playerPitch;
    public static boolean isRestocking;
    public static Integer restockingType;
    public static boolean isWallDone;
    public static boolean isPause;
    public static BlockPos restockingStartPosition;
    public static boolean isPlacingShulker;
    public static Integer stacksStolen;
    public static boolean hasOpenedShulker;
    public static Integer slot_number;
    public static boolean wasRestocking;
    public static BlockPos shulkerBlockPos;
    public static boolean isBreakingShulker;
    public static boolean isPostRestocking;
    public static boolean isClearingInventory;
    public static boolean isProcessingTasks;
    public static int hasLookedAtShulker = 0;
    public static boolean hasQueued;
    public static Integer numberOfSlotsToSteal;
    public static int swapSlot;
    public static int trashCount;
    public static float oldYaw;

    public IceHighwayBuilder() {
        super(IceRail.CATEGORY, "ice-highway-builder", "Automated ice highway builder.");
    }

    public static Direction getPlayerDirection() {
        return playerDirection;
    }

    // Constants
    public static Integer playerX;
    public static Integer playerY;
    public static Integer playerZ;

    private final SettingGroup sgAutoEat = settings.createGroup("Auto Eat");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgBlacklist = settings.createGroup("Inventory clearer");

    // Auto Eat Settings
    private final Setting<Boolean> enableAutoEat = sgAutoEat.add(new BoolSetting.Builder()
            .name("enable-auto-eat")
            .description("Pauses the current task and automatically eats.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> eatEGaps = sgAutoEat.add(new BoolSetting.Builder()
            .name("eat-egap-when-burning")
            .description("Eats an enchanted golden apple if the player is burning.")
            .defaultValue(true)
            .visible(enableAutoEat::get)
            .build()
    );

    private final Setting<Boolean> disableAutoEatAfterDigging = sgAutoEat.add(new BoolSetting.Builder()
            .name("disable-auto-eat-after-digging")
            .description("Disables Auto Eat when \"Ice Highway Builder\" is disabled.")
            .defaultValue(true)
            .visible(enableAutoEat::get)
            .build()
    );

    // Inventory Management Settings
    private final Setting<Boolean> autoRefillHotbar = sgInventory.add(new BoolSetting.Builder()
            .name("auto-replenish")
            .description("Automatically move items from inventory to hotbar when slots are empty.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> openAndCloseInventory = sgInventory.add(new BoolSetting.Builder()
            .name("open-and-close-inventory")
            .description("Whether to open and close inventory on activated or not. Enable this if \"Ice Rail Auto Replenish\" doesn't work sometimes.")
            .defaultValue(true)
            .build()
    );

    public final Setting<List<Item>> blacklist = sgBlacklist.add(new ItemListSetting.Builder()
         .name("blacklist")
         .description("Items you don't want to throw (Example: Shulkers).")
         .build()
     );

    public Direction getPlayerCurrentDirection() {
        assert mc.player != null;
        return mc.player.getHorizontalFacing();
    }

    public static BlockPos getHighwayCoords() {
        return highwayCoords;
    }

    public static void setHighwayCoords(BlockPos value) {
        highwayCoords = value;
    }

    public Setting<List<Item>> getBlacklist() {
        return blacklist;
    }

    private void initializeRequiredVariables() {
        assert mc.player != null;
        highwayCoords = mc.player.getBlockPos();
        playerX = mc.player.getBlockX();
        playerY = mc.player.getBlockY();
        playerZ = mc.player.getBlockZ();
    }

    @Override
    public void onActivate() {
        if (!validateInitialConditions()) {
            toggle();
            return;
        }
        initializeRequiredVariables();
        enableRequiredModules();

        if (openAndCloseInventory.get())
            openAndCloseInventory();

        assert mc.player != null;
        playerDirection = getPlayerCurrentDirection();
        playerPitch = 0;
    }

    private boolean validateInitialConditions() {
        if (mc.player == null || mc.world == null) return false;

        boolean flag = true;

        return flag;
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        Direction direction = mc.player.getHorizontalFacing();
        if (direction == null) return;
        switch (direction) {
            case Direction.NORTH:
                mc.player.setYaw(180);
                break;
            case Direction.SOUTH:
                mc.player.setYaw(0);
                break;
            case Direction.EAST:
                mc.player.setYaw(-90);
                break;
            case Direction.WEST:
                mc.player.setYaw(90);
                break;
        }
        mc.player.setPitch(0);
        cancelCurrentProcessBaritone();
        disableAllModules();
        releaseForward();
        resetState();
        shutdownScheduler1();

    }

    private void resetState() {
        wasEchestFarmerActive = false;
        baritoneCalled = false;
        playerX = null;
        playerY = null;
        playerZ = null;
        highwayCoords = null;
        isGoingToHighway = false;
        isGoingToHole = false;
        playerDirection = null;
        releaseForward();

        // Shulker Interactions
        isRestocking = false;
        restockingType = 0; // 0=Blue ice 1=Pickaxes
        isWallDone = false;
        isPause = false;
        restockingStartPosition = null;
        isPlacingShulker = false;
        stacksStolen = 0;
        hasOpenedShulker = false;
        slot_number = 0;
        wasRestocking = false;
        shulkerBlockPos = null;
        isBreakingShulker = true;
        isPostRestocking = false;
        isProcessingTasks = false;
        hasLookedAtShulker = 0;
        hasQueued = false;
        stealingDelay = 0;
        swapSlot = -1;
        isClearingInventory = false;
        oldYaw = 0;
    }

    private void steal(ScreenHandler handler, int slot_number) {
        MeteorExecutor.execute(() -> moveSlots(handler, slot_number));
    }

    private void moveSlots(ScreenHandler handler, int i) {
        if (handler.getSlot(i).hasStack() && Utils.canUpdate()) {
            InvUtils.shiftClick().slotId(i);
            stacksStolen++;
        }
    }

    private void handleRestocking() {
        if (isGatheringItems()) {
            isRestocking = false;
            return;
        }

        assert mc.player != null;
        assert mc.world != null;

        if (!restockingStartPosition.equals(mc.player.getBlockPos())) {
            if (!isPause) {
                isPause = true;
                return;
            }
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockingStartPosition));
            resumeBaritone();
        } else {
            isPause = false;
        }

        if (isPause) {
            return;
        }

        shulkerBlockPos = getBlockPos();

        if (hasLookedAtShulker < 10) { // To add a 10 tick delay
            if (hasLookedAtShulker == 0) {
                if (restockingType == 0) {
                    InvUtils.swap(8, false);
                } else {
                    InvUtils.swap(7, false);
                }
                lookAtBlock(shulkerBlockPos.withY(playerY - 1)); // To minimize the chance of the shulker being placed upside down
            }   

            hasLookedAtShulker++;
            return;
        }

        if (!(mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock)) {
            if (BlockUtils.canPlace(shulkerBlockPos, false) && !BlockUtils.canPlace(shulkerBlockPos, true)) return;
            if (restockingType == 0) {
                place(shulkerBlockPos, Hand.MAIN_HAND, 8, true, true, true);
            } else {
                place(shulkerBlockPos, Hand.MAIN_HAND, 7, true, true, true);
            }
            return;
        }

        if (!hasOpenedShulker) {
            mc.setScreen(null);
            // Open the shulker
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(shulkerBlockPos), Direction.DOWN,
                            shulkerBlockPos, false), 0));

            mc.setScreen(null);
            hasOpenedShulker = true;
            return;
        }

        mc.setScreen(null);
        if (stacksStolen == null) {
            stacksStolen = 0;
        }
        if ((stacksStolen >= numberOfSlotsToSteal)
                || stacksStolen >= 27 // No more slots left (numberOfSlotsToSteal > stacksStolen because the shulker may not have the full amount)
            // Worst case scenario is 6.75 seconds or 135 ticks
        ) {
            // Run post restocking
            isPostRestocking = true;

            stacksStolen = 0;
            slotNumber = 0;
            
            wasRestocking = true;

            isPause = false;
            isPlacingShulker = false;
            restockingStartPosition = null;
            hasLookedAtShulker = 0;
            stealingDelay = 0;
            hasOpenedShulker = false;
            isRestocking = false;
        } else {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (hasOpenedShulker) {
                if (stealingDelay < 5) { // To add a 5 tick delay
                    stealingDelay++;
                    return;
                }
                if (restockingType==1) {
                    InvUtils.quickSwap().fromId(0).toId(slotNumber);
                    stacksStolen ++;
                } else {
                    steal(handler, slotNumber);
                }
                slotNumber++;
                stealingDelay = 0;
            }
        }
    }

    private @NotNull BlockPos getBlockPos() {
        int offset = 1;
        return switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(playerX, playerY, mc.player.getBlockZ() + offset);
            case SOUTH -> new BlockPos(playerX, playerY, mc.player.getBlockZ() - offset);
            case EAST -> new BlockPos(mc.player.getBlockX() + offset, playerY, playerZ);
            case WEST -> new BlockPos(mc.player.getBlockX() - offset, playerY, playerZ);
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }

    private void handleClearInventory() {
        if (isRestocking || isPostRestocking)
            return;
        assert mc.player != null;
        if (stealingDelay == 0) {
            if (!mc.world.getBlockState(getBlockPos()).isAir() 
            && !(mc.world.getBlockState(getBlockPos()).getBlock() instanceof ShulkerBoxBlock)
            && !hasOpenedShulker) {
                InvUtils.swap(0, true);
                BlockUtils.breakBlock(getBlockPos(), true);
            }
        }
        if (stealingDelay == 2) {
            BlockPos temp = new BlockPos(getBlockPos().getX(),getBlockPos().getY()+1,getBlockPos().getZ());
            if (!mc.world.getBlockState(temp).isAir()
                    && !(mc.world.getBlockState(temp).getBlock() instanceof ShulkerBoxBlock)
                    && !hasOpenedShulker) {
                InvUtils.swap(0, true);
                BlockUtils.breakBlock(temp, true);
            }
        }
        if (stealingDelay < 4) {
            stealingDelay ++;
            return;
        }
        trashCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!itemStack.isEmpty() && !blacklist.get().contains(itemStack.getItem())) {
                trashCount ++;
            }
        }
        if (stealingDelay > 6) {stealingDelay = 0;} else {stealingDelay++;}
        if (trashCount > 1) {
            mc.player.setPitch(-30);
            mc.player.setYaw(oldYaw - 180);
            for (int i = 2; i < 36; i++) {
                ItemStack itemStack = mc.player.getInventory().getStack(i);
                if (!itemStack.isEmpty() && !blacklist.get().contains(itemStack.getItem()) && (trashCount > 1)) {
                    if (mc.player.getInventory().getStack(6).isEmpty()) {
                        InvUtils.quickSwap().fromId(6).toId(i);
                        if (i < 9) {
                            InvUtils.swap(i, false);
                        } else {
                            InvUtils.swap(6, false);
                        }
                    }
                    if (stealingDelay == 5) {
                        if (i < 9) {
                            InvUtils.drop().slot(i);
                        } else {
                            InvUtils.drop().slot(6);
                        }
                    }
                    return;
                }
            }
        } else {
            mc.player.setYaw(oldYaw);
            isPlacingShulker = true;
            if (restockingType == 0) {
                swapSlot = -1;
                numberOfSlotsToSteal = countEmptySlots();
            } else {
                swapSlot = findPickToSwap(mc.player.getInventory().getStack(7));
                numberOfSlotsToSteal = 1;
            }
            isRestocking = true;
            isClearingInventory = false;
        }
    }

    private void handlePostRestocking() {
        if (isRestocking)
            return;

        assert mc.player != null;
        if (wasRestocking && !isPostRestocking) {
            setKeyPressed(mc.options.inventoryKey, true);
            isPostRestocking = true;
            return;
        }

        if (isPostRestocking) {
            assert mc.world != null;
            isBreakingShulker = true;
            if (mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock) {
                if (PlayerUtils.isWithinReach(shulkerBlockPos)) {
                    if (BlockUtils.breakBlock(shulkerBlockPos, true))
                        return;
                }
            }

            isBreakingShulker = false;
            Item[] items = getShulkerBoxesNearby();

            if (areShulkerBoxesNearby()) {
                for (Item item : items) {
                    if (!isGatheringItems()) {
                        IceRailGatherItem(item);
                        return;
                    }
                    return;
                }
            }

            if ((mc.world.getBlockState(shulkerBlockPos).getBlock().equals(Blocks.AIR))
                    && !isGatheringItems()
                    && !isRestocking
                    && !isBreakingShulker
                    && !isProcessingTasks) {
                isPlacingShulker = false;
                wasRestocking = false;
                setKeyPressed(mc.options.inventoryKey, false);
                isPostRestocking = false;
                isProcessingTasks = false;
                hasQueued = false;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null) return;
        boolean walkForward;

        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        Module iceRailNuker = Modules.get().get("ice-rail-nuker");
        Module icePlacer = Modules.get().get("ice-placer");

        if (iceRailAutoEat != null) {
            BoolSetting amountSetting = (BoolSetting) iceRailAutoEat.settings.get("eat-egap-when-burning");
            if (amountSetting != null) amountSetting.set(eatEGaps.get());
        }

        if (isGoingToHighway) {
            handleInvalidPosition(0);
            return;
        }

        if (mc.player.getBlockY() == playerY) {
            setHWCoords(0);
        }

        if (!isPlayerInValidPosition()) {
            isGoingToHighway = true;
            return;
        }

        if (isHolesInIce()) {
            setHWCoords(1);
            handleInvalidPosition(1);
            return;
        }

        if (isPostRestocking) {
            handlePostRestocking();
            return;
        }

        if (isClearingInventory) {
            handleClearInventory();
            return;
        }

        if (isRestocking) {
            handleRestocking();
            return;
        }

        if (countItems(Items.BLUE_ICE) <= 8) {
            ItemStack BlueIceShulker = findBestBlueIceShulker();

            if (BlueIceShulker == null && !isPlacingShulker) {
                error(String.format("Player low on materials. %d Blue Ice.", countItems(Items.BLUE_ICE)));
                toggle();
                return;
            }

            if (isGatheringItems()
                    || isRestocking) return;

            restockingStartPosition = mc.player.getBlockPos();
            if (icePlacer.isActive()) {
                if (getIsEating()) { // Toggle off
                    icePlacer.toggle();
                    iceRailNuker.toggle();
                }
            }

            restockingType = 0;
            slotNumber = 0;
            stealingDelay = 0;
            // Initiate inventory clear
            oldYaw = mc.player.getYaw();
            isClearingInventory = true;
            return;
        }

        if (countUsablePickaxes() == 0) {
            ItemStack PicksShulker = findBestPicksShulker();

            if (PicksShulker == null && !isPlacingShulker) {
                error("Insufficient materials. Need: 1 diamond/netherite, >50 durability pickaxe.");
                toggle();
                return;
            }

            if (isGatheringItems()
                    || isRestocking) return;

            restockingStartPosition = mc.player.getBlockPos();
            if (icePlacer.isActive()) {
                if (getIsEating()) { // Toggle off
                    icePlacer.toggle();
                    iceRailNuker.toggle();
                }
            }

            restockingType = 1;
            isPlacingShulker = true;
            numberOfSlotsToSteal = 1;
            swapSlot = findPickToSwap(mc.player.getInventory().getStack(7));
            slotNumber = swapSlot;
            // Initiate inventory clear
            stealingDelay = 0;
            oldYaw = mc.player.getYaw();
            isClearingInventory = true;
            return;
        }

        lockRotation();

        if (icePlacer.isActive()) { // Toggle off
            icePlacer.toggle();
            iceRailNuker.toggle();
        } else {
            if (!getIsEating()) { // Toggle on
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
        }

        walkForward = !getIsEating();

        if (needsToScaffold()) {
            boolean isAirInFront = false;

            switch (getPlayerDirection()) {
                case NORTH ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 1)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 2)).getBlock() == Blocks.AIR;
                case SOUTH ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 1)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 2)).getBlock() == Blocks.AIR;
                case EAST ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX + 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX + 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
                case WEST ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX - 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX - 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
            }

            if (isAirInFront)
                walkForward = false;
        } else
            walkForward = !getIsEating();

        if (!walkForward) {
            setKeyPressed(mc.options.forwardKey, false);
            setKeyPressed(mc.options.rightKey, false);
            setKeyPressed(mc.options.leftKey, false);
            return;
        }

        setKeyPressed(mc.options.forwardKey, true); // W
        if (getPlayerDirection() == Direction.EAST || getPlayerDirection() == Direction.SOUTH)
            setKeyPressed(mc.options.rightKey, true);    // D
        else
            setKeyPressed(mc.options.leftKey, true);    // A
    }

    public static void lookAtBlock(BlockPos blockPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d hitPos = Vec3d.ofCenter(blockPos);

        Direction side = getPlaceSide(blockPos);
        if (side != null) {
            blockPos.offset(side);
            hitPos = hitPos.add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        }
        assert mc.player != null;
        mc.player.setYaw((float) Rotations.getYaw(hitPos));
        mc.player.setPitch((float) Rotations.getPitch(hitPos));
    }

    public static boolean needsToScaffold() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null)
            return false;

        int startOffset, endOffset;

        switch (getPlayerDirection()) {
            case NORTH -> {
                startOffset = -2;
                endOffset = 5;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(playerX, playerY - 1, mc.player.getBlockZ() + offset)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case SOUTH -> {
                startOffset = -5;
                endOffset = 2;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(playerX, playerY - 1, mc.player.getBlockZ() + offset)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case EAST -> {
                startOffset = -2;
                endOffset = 5;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(mc.player.getBlockX() + offset, playerY - 1, playerZ)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case WEST -> {
                startOffset = -5;
                endOffset = 2;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(mc.player.getBlockX() + offset, playerY - 1, playerZ)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    private boolean isPlayerInValidPosition() {
        assert mc.player != null;
        switch (getPlayerDirection()) {
            case NORTH, SOUTH -> {
                return mc.player.getBlockY() == playerY &&
                        mc.player.getBlockX() == playerX &&
                        mc.player.getX() <= playerX+0.3;
            }
            case EAST, WEST -> {
                return mc.player.getBlockY() == playerY &&
                        mc.player.getBlockZ() == playerZ &&
                        mc.player.getZ() >= playerZ+0.3;
            }
        }

        return false;
    }

    private boolean isHolesInIce() {
        Direction direction = getPlayerDirection();
        assert mc.player != null;
        if (direction == null) {
            return false;
        }
        int airBlocks = 0;
        BlockPos block1 = null;
        int startBlock = 0;
        switch (direction) {
            case NORTH -> {
                if (Math.abs(mc.player.getBlockZ()) % 2 == 0) {
                    startBlock = mc.player.getBlockZ();
                } else {
                    startBlock = mc.player.getBlockZ() + 1;
                }
            }
            case SOUTH -> {
                if (Math.abs(mc.player.getBlockZ()) % 2 == 0) {
                    startBlock = mc.player.getBlockZ();
                } else {
                    startBlock = mc.player.getBlockZ() - 1;
                }
            }
            case WEST -> {
                if (Math.abs(mc.player.getBlockX()) % 2 == 0) {
                    startBlock = mc.player.getBlockX();
                } else {
                    startBlock = mc.player.getBlockX() + 1;
                }
            }
            case EAST -> {
                if (Math.abs(mc.player.getBlockX()) % 2 == 0) {
                    startBlock = mc.player.getBlockX();
                } else {
                    startBlock = mc.player.getBlockX() - 1;
                }
            }
        }
            for (int i = 1; i < 3; i++) {
                switch (direction) {
                    case WEST -> {
                        block1 = new BlockPos(startBlock + i * 2, playerY+1, playerZ - 1);
                    }
                    case EAST -> {
                        block1 = new BlockPos(startBlock - i * 2, playerY+1, playerZ - 1);
                    }
                    case NORTH -> {
                        block1 = new BlockPos(playerX + 1, playerY+1, startBlock + i * 2);
                    }
                    case SOUTH -> {
                        block1 = new BlockPos(playerX + 1, playerY+1, startBlock - i * 2);
                    }
                }
                if (Blocks.BLUE_ICE != mc.world.getBlockState(block1).getBlock()) {
                    airBlocks++;
                }
            }
            return airBlocks > 0 && airBlocks < 3;
        }

    private void handleInvalidPosition(int Type) {
        assert mc.player != null;
        BlockPos target;
        target = getHighwayCoords();

        if (getHighwayCoords() == null) {
            setHWCoords(Type);
            target = getHighwayCoords();
        }

        if (target == null) return;

        if (mc.player.getBlockPos() != target) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
            resumeBaritone();
        }

        if (hasReachedLocation(mc.player, target)) {
            isGoingToHighway = false;
        }
    }

    private void lockRotation() {
        assert mc.player != null;
        mc.player.setPitch(0);

        Direction direction = getPlayerDirection();

        switch (direction) {
            case Direction.NORTH, Direction.EAST:
                mc.player.setYaw(-135);
                break;
            case Direction.SOUTH, Direction.WEST:
                mc.player.setYaw(135);
                break;
        }
    }

    private void enableRequiredModules() {
        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        Module iceRailAutoReplenish = Modules.get().get("ice-rail-auto-replenish");
        Module scaffoldGrim = Modules.get().get("scaffold-grim");

        if (enableAutoEat.get() && !iceRailAutoEat.isActive()) iceRailAutoEat.toggle();

        if (autoRefillHotbar.get())
            if (iceRailAutoReplenish != null && !iceRailAutoReplenish.isActive())
                iceRailAutoReplenish.toggle();

        if (!scaffoldGrim.isActive()) scaffoldGrim.toggle();
    }

    private void disableAllModules() {
        String[] modulesToDisable = {
                "ice-rail-gather-item",
                "ice-placer",
                "ice-rail-auto-replenish",
                "ice-rail-nuker",
                "scaffold-grim"
        };

        for (String moduleName : modulesToDisable) {
            Module module = Modules.get().get(moduleName);
            if (module != null && module.isActive()) {
                module.toggle();
            }
        }

        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        if (disableAutoEatAfterDigging.get() && iceRailAutoEat != null && iceRailAutoEat.isActive())
            iceRailAutoEat.toggle();
    }
}
