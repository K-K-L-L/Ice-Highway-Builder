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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class IceHighwayBuilder extends Module {
    public static boolean isGoingToHighway;

    public IceHighwayBuilder() {
        super(IceRail.CATEGORY, "ice-highway-builder", "Automated ice highway builder.");
    }

    private static BlockPos highwayCoords;
    public static boolean wasEchestFarmerActive = false;
    public static boolean baritoneCalled;
    public static Direction playerDirection;
    private float playerYaw;
    private float playerPitch;

    public static Direction getPlayerDirection() {
        return playerDirection;
    }

    // Constants
    public static Integer playerX;
    public static Integer playerY;
    public static Integer playerZ;

    private final SettingGroup sgAutoEat = settings.createGroup("Auto Eat");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

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
        playerYaw = mc.player.getYaw();
        playerPitch = mc.player.getPitch();
    }

    private boolean validateInitialConditions() {
        if (mc.player == null || mc.world == null) return false;

        boolean flag = true;

        if (countUsablePickaxes() == 0) {
            error("Insufficient materials. Need: 1 diamond/netherite, >50 durability pickaxe.");
            flag = false;
        }

        return flag;
    }

    @Override
    public void onDeactivate() {
        cancelCurrentProcessBaritone();
        disableAllModules();
        resetState();
        mc.player.setYaw(playerYaw);
        mc.player.setPitch(playerPitch);
    }

    private void resetState() {
        wasEchestFarmerActive = false;
        baritoneCalled = false;
        playerX = null;
        playerY = null;
        playerZ = null;
        highwayCoords = null;
        isGoingToHighway = false;
        playerDirection = null;
        releaseForward();
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
            handleInvalidPosition();
            return;
        }

        if (mc.player.getBlockY() == playerY) {
            setHWCoords();
        }

        if (!isPlayerInValidPosition()) {
            isGoingToHighway = true;
            return;
        }

        if (countUsablePickaxes() == 0) {
            error("0 Usable pickaxes were found. Ice Highway Builder will turn off.");
            toggle();
            return;
        }

        if (countItems(Items.BLUE_ICE) == 0) {
            error("No blue ice left.");
            toggle();
            return;
        }

        lockRotation();

        if (icePlacer.isActive()) {
            if (getIsEating()) { // Toggle off
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
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
                case NORTH -> isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 1)).getBlock() == Blocks.AIR &&
                        mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 2)).getBlock() == Blocks.AIR;
                case SOUTH -> isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 1)).getBlock() == Blocks.AIR &&
                        mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 2)).getBlock() == Blocks.AIR;
                case EAST -> isAirInFront = mc.world.getBlockState(new BlockPos(playerX + 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                        mc.world.getBlockState(new BlockPos(playerX + 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
                case WEST -> isAirInFront = mc.world.getBlockState(new BlockPos(playerX - 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                        mc.world.getBlockState(new BlockPos(playerX - 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
            }

            if (isAirInFront)
                walkForward = false;
        } else
            walkForward = true;

        if (!walkForward) {
            setKeyPressed(mc.options.forwardKey, false);
            setKeyPressed(mc.options.rightKey, false);
            return;
        }

        setKeyPressed(mc.options.forwardKey, true); // W
        setKeyPressed(mc.options.leftKey, true);    // A
    }

    public static boolean needsToScaffold() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null) return false;

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
                        mc.player.getBlockX() == playerX;
            }
            case EAST, WEST -> {
                return mc.player.getBlockY() == playerY &&
                        mc.player.getBlockZ() == playerZ;
            }
        }

        return false;
    }

    private void handleInvalidPosition() {
        assert mc.player != null;
        BlockPos target;
        target = getHighwayCoords();

        if (getHighwayCoords() == null) {
            setHWCoords();
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
        Direction direction = getPlayerDirection();

        switch (direction) {
            case Direction.NORTH:
                mc.player.setPitch(0);
                mc.player.setYaw(-135);
                break;
            case Direction.SOUTH:
                mc.player.setPitch(0);
                mc.player.setYaw(45);
                break;
            case Direction.EAST:
                mc.player.setPitch(0);
                mc.player.setYaw(-45);
                break;
            case Direction.WEST:
                mc.player.setPitch(0);
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
            if(iceRailAutoReplenish != null && !iceRailAutoReplenish.isActive())
                iceRailAutoReplenish.toggle();

        if (!scaffoldGrim.isActive()) scaffoldGrim.toggle();
    }

    private void disableAllModules() {
        String[] modulesToDisable = {
            "scaffold-grim",
            "ice-rail-nuker",
            "ice-rail-auto-replenish"
        };

        for (String moduleName : modulesToDisable) {
            Module module = Modules.get().get(moduleName);
            if (module != null && module.isActive()) {
                module.toggle();
            }
        }

        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        if(disableAutoEatAfterDigging.get() && iceRailAutoEat != null && iceRailAutoEat.isActive())
            iceRailAutoEat.toggle();
    }
}
