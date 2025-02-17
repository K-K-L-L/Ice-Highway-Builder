package K_K_L_L.IceRail.addon.modules;
import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.Utils.setKeyPressed;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
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
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;

public class BlueIceMiner extends Module {
    public static ArrayList<Object> groups = null;
    public static ArrayList<Integer> validGroups = null;
    public static ArrayList<Double> iceBergDistances = null;
    public static boolean miningIce = false;
    public static String state = "idle";
    public static String returnToState = "idle";
    public static int retrieveCount;
    public static Item retrieveItem;
    public static int retrieveType;
    public static String NewState;
    public static int foundCount;
    private int slotNumber;
    private int stealingDelay = 0;
    public static int repairCount = 0;
    public static BlockPos portalOriginBlock = null;
    public static int buildTimer = 0;
    public static final int SEARCH_RADIUS = 90;
    private static final int MAX_Y = 122;
    public static ArrayList<BlockPos> portalBlocks = new ArrayList<BlockPos>();
    public static ArrayList<Integer> portalObby = new ArrayList<Integer>();
    public static boolean scanningWorld = true;
    public int tick = 0;
    public static boolean isPathing = false;
    public static DimensionType dimension;
    boolean reached = false;
    public static boolean foundBlock;
    public static int leg = 0;
    public static BlockPos vertex;
    public static BlockPos landCoords;
    public static int prevCount;
    public boolean hasFoundFrozenOcean = false;
    public static int currentIceberg = 0;
    public static int initialEchests;
    int echestSlot = -1;

    MinecraftClient mc = MinecraftClient.getInstance();

    public BlueIceMiner() {
        super(IceRail.CATEGORY, "blue-ice-miner", "Automatically finds and mines more blue ice when you run out.");
    }


    private final SettingGroup sgToggle = settings.createGroup("Toggle");
    private final SettingGroup sgPortal = settings.createGroup("Nether Portals");
    private final SettingGroup sgMining = settings.createGroup("Mining");
    // Toggle settings
    private final Setting<Boolean> autoToggle = sgToggle.add(new BoolSetting.Builder()
            .name("auto-toggle")
            .description("Turns on when out of blue ice.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> minDistanceToSpawn = sgToggle.add(new IntSetting.Builder()
            .name("min-distance-to-spawn")
            .description("Minimum nether distance from spawn that Auto Toggle will turn on to avoid old chunks.")
            .defaultValue(30000)
            .min(0)
            .max(100000)
            .sliderRange(0, 100000)
            .visible(autoToggle::get)
            .build()
    );
    // Nether Portal settings
    private final Setting<Boolean> buildNetherPortal = sgPortal.add(new BoolSetting.Builder()
            .name("build-nether-portal")
            .description("Builds a portal if none are in render distance.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> searchOnHighway = sgPortal.add(new BoolSetting.Builder()
            .name("search-on-highway")
            .description("Searches for portals on the highway if none are found in render distance.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> cruiseAltitude = sgPortal.add(new IntSetting.Builder()
            .name("cruise-altitude")
            .description("Distance to maintain while flying to a cold ocean.")
            .defaultValue(400)
            .min(0)
            .max(2000)
            .sliderRange(0, 1000)
            .build()
    );
    // Blue Ice mining settings
    private final Setting<Integer> groupsizeThreshold = sgMining.add(new IntSetting.Builder()
            .name("iceberg-size-threshold")
            .description("Mines the iceberg if it has at least this much blue ice.")
            .defaultValue(250)
            .min(0)
            .max(1000)
            .sliderRange(0, 500)
            .build()
    );
    private final Setting<Boolean> useSpeedmine = sgMining.add(new BoolSetting.Builder()
            .name("use-speedmine")
            .description("Uses speedmine, might be glitchy for some players.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> factor = sgMining.add(new DoubleSetting.Builder()
            .name("factor")
            .description("Speed factor of speedmine")
            .defaultValue(1.0)
            .min(0.0)
            .max(2.0)
            .sliderRange(0.0, 2.0)
            .visible(useSpeedmine::get)
            .build()
    );
    private final Setting<Boolean> doubleMine = sgMining.add(new BoolSetting.Builder()
            .name("double-mine")
            .description("Mines two blocks at once.")
            .defaultValue(true)
            .visible(useSpeedmine::get)
            .build()
    );
    @Override
    public void onActivate() {
        assert mc.world != null;
        dimension = mc.world.getDimension();
        state = "idle";
        scanningWorld = true;
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

    private boolean isNotSilkPick(ItemStack stack) {
        return (stack.getItem() instanceof PickaxeItem && stack.getDamage() < stack.getMaxDamage() - 50
                && !Utils.hasEnchantments(stack, Enchantments.SILK_TOUCH));
    }

    private boolean condition(ItemStack stack, int type, Item item) {
        if (type == 0 || type > 2) {
            if (item == Items.OBSIDIAN) {
                return (stack.getItem() == item && stack.getCount() > 15);
            } else if (item == Items.ENDER_CHEST){
                return (stack.getItem() == item && stack.getCount() > 1);
            } else {
                return (stack.getItem() == item);
            }
        } else if (type == 1) {
            return isNotSilkPick(stack);
        } else {
            if (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE) {
                if (stack.getDamage() < stack.getMaxDamage() - 50) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean search(Item item, int slot, int type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        assert (mc.player != null);
        Inventory inventory = mc.player.getInventory();
        ItemStack hotbarStack = inventory.getStack(slot);

        foundCount = 0;
        if (condition(hotbarStack, type, item)) {
            foundCount = 1;
            return true; // The slot already has the right item
        }
        int bestSlot = -1;

        for (int i = 0; i < inventory.size(); i++) {
            if (i == slot) continue; // Skip the  slot that has already been checked
            ItemStack stack = inventory.getStack(i);

            if (condition(stack, type, item)) {
                if (bestSlot == -1) {
                    bestSlot = i;
                }
                foundCount ++;
            }
        }
        if (bestSlot != -1) {
            if (type != 2) {
                if (type < 2) {InvUtils.quickSwap().fromId(slot).toId(bestSlot);}
                return true;
            }
        }
        //search the shulkers in the inventory
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {

                boolean Return = false;
                ItemStack[] containerItems = new ItemStack[27];
                Utils.getItemsInContainerItem(stack, containerItems);

                for (ItemStack stack1 : containerItems) {
                    if (!stack1.isEmpty() && condition(stack1, type, item)) {
                        Return = true;
                        foundCount++;
                    }
                }
                if (Return) {
                    if (bestSlot == -1) {
                        bestSlot = i;
                    }

                }
            }
        }
        if (type != 2) {
            if (bestSlot != -1) {
                if (bestSlot > 9) {
                    InvUtils.quickSwap().fromId(5).toId(bestSlot);
                } else {
                    InvUtils.quickSwap().fromId(bestSlot).toId(9);
                    InvUtils.quickSwap().fromId(5).toId(9);
                }
                isPathing = false;
                shulkerRestock(item == Items.FIREWORK_ROCKET ? 2 : 1, item, type);
                return true;
            } else {
                return false;
            }
        } else {
            return foundCount < 5;
        }
    }

    public static List<BlockPos> searchWorld(Block blockType) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        // Get the player's current position
        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();

        // Get the current render distance in chunks and convert it to block distance (16 blocks per chunk)
        int renderDistance = 6 * 16;

        List<BlockPos> blockPosList = new ArrayList<>();
        int minY = blockType == Blocks.BLUE_ICE ? 40 : 32;
        int maxY = blockType == Blocks.BLUE_ICE ? 80 : 122;
        // Iterate through blocks in the render distance around the player
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -renderDistance; z <= renderDistance; z++) {
                    // Calculate the BlockPos for each block
                    BlockPos currentPos = playerPos.east(x).withY(y).south(z);
                    if (mc.world.getBlockState(currentPos).getBlock() == blockType) {
                        blockPosList.add(currentPos);
                    }
                }
            }
        }
        return blockPosList;
    }

    public void getBlockGroups(Block block) {
        List<BlockPos> locations = searchWorld(block);
        double distance;
        double smallestDistance;
        int xDiff, zDiff, iceBergIndex = 0;
        groups = new ArrayList<>();
        validGroups = new ArrayList<>();
        iceBergDistances = new ArrayList<>();
        for (BlockPos foundIce : locations) {
            smallestDistance = Double.MAX_VALUE;
            for (int i = 0; i < groups.size(); i+=2) {
                BlockPos block2 = (BlockPos) groups.get(i);
                xDiff = foundIce.getX() - block2.getX();
                zDiff = foundIce.getZ() - block2.getZ();
                distance = Math.sqrt(Math.pow(xDiff, 2) + Math.pow(zDiff, 2));
                if (distance < smallestDistance) {
                    smallestDistance = distance;
                    iceBergIndex = i;
                }
            }
            if (smallestDistance > 5 || groups.isEmpty()) {
                groups.add(new BlockPos(foundIce));
                groups.add(1);
            } else {
                groups.set(iceBergIndex + 1, (Integer) groups.get(iceBergIndex + 1) + 1);
            }
        }
        for (int i=0; i < groups.size(); i+=2){
            if ((int)groups.get(i+1) > groupsizeThreshold.get()) {
                validGroups.add(i);
            }
        }
        assert mc.player != null;
        for (int i : validGroups) {
            BlockPos block1 = (BlockPos) groups.get(i);
            double xSquared = Math.pow(block1.getX()-mc.player.getX(), 2);
            double zSquared = Math.pow(block1.getZ()-mc.player.getZ(), 2);
            iceBergDistances.add(Math.sqrt(xSquared+zSquared));
        }
    }

    public static BlockPos nearestGroupCoords(boolean updateCurrentIceberg){
        int j = 0;
        double min = Double.MAX_VALUE;
        for (int i = 0; i<iceBergDistances.size(); i++) {
            if (iceBergDistances.get(i) < min) {
                j = i;
                min = iceBergDistances.get(i);
            }
        }
        if (updateCurrentIceberg) {
            currentIceberg = j*2;
        }
        return (BlockPos) groups.get(j*2);
    }
    private void disableAllModules() {
        String[] modulesToDisable = {
                "ice-rail-gather-item",
                "ice-placer",
                "ice-rail-auto-replenish",
                "ice-rail-nuker",
                "scaffold-grim",
                "ice-highway-builder"
        };

        for (String moduleName : modulesToDisable) {
            Module module = Modules.get().get(moduleName);
            if (module != null && module.isActive()) {
                module.toggle();
            }
        }
    }
    public static void packetMine(BlockPos block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, block, BlockUtils.getDirection(block)));
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, block, BlockUtils.getDirection(block)));
    }
    public static boolean isInColdBiome(){
        MinecraftClient mc = MinecraftClient.getInstance();
        assert mc.player != null;
        assert mc.world != null;
        RegistryEntry<Biome> biome = mc.world.getBiome(mc.player.getBlockPos());
        return biome.getKey().equals(Optional.of(BiomeKeys.COLD_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.DEEP_COLD_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.ICE_SPIKES)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_BEACH)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_PLAINS)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_TAIGA)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.TAIGA));
    }
    public static ArrayList<BlockPos> getBlueIceInRange() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ArrayList<BlockPos> blockPosList = new ArrayList<>();

        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = playerPos.getX() - 4; x <= playerPos.getX() + 4; x++) {
            for (int y = playerPos.getY() - 4; y <= playerPos.getY() + 4; y++) {
                for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    double distanceSquared = playerPos.getSquaredDistance(x, y, z);

                    if (distanceSquared <= 16 && mc.world.getBlockState(blockPos).getBlock() == Blocks.BLUE_ICE) {
                        blockPosList.add(blockPos);
                    }
                }
            }
        }
        return blockPosList;
    }
    public static int getMaxY(BlockPos block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int j = 200;
        while (mc.world.getBlockState(mc.player.getBlockPos().withY(j)).isAir()) {
            j --;
        }
        return j;
    }
    public static int getEchestSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int echestSlot = -1;
        for (int i = 0; i < 9; i++) {
            assert mc.player != null;
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) {
                echestSlot = i;
                break;
            }
        }
        if (echestSlot == -1) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) {
                    InvUtils.quickSwap().fromId(4).toId(i);
                    echestSlot = 4;
                    break;
                }
            }
        }
        return echestSlot;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        if (mc.player == null || mc.world == null){
            return;
        }
        if (!isActive()) return;
        if (state == null || state.equals("idle")) {isPathing = false; return;}
        if (state.equals("waitingForClearInventory")) return;
        error(state);
        Module iceRailAutoReplenish = Modules.get().get("ice-rail-auto-replenish");
        /* ice rail auto replenish can conflict with the various item rearrangements
        necessary to build portals, light portals, repair picks, use fireworks, etc */
        if (!state.equals("idle")) {
            if (iceRailAutoReplenish.isActive()) {
                iceRailAutoReplenish.toggle();
            }
        } else {
            if (!iceRailAutoReplenish.isActive()) {
                iceRailAutoReplenish.toggle();
            }
        }
        if (dimension != mc.world.getDimension()) {
            //if bed doesn't work then dimension is nether
            scanningWorld = true;
            leg = 0;
            Module scaffoldGrim = Modules.get().get("scaffold-grim");
            if (!dimension.bedWorks()) {
                state = "flyToBlueIce";
                hasFoundFrozenOcean = false;
                mc.player.setPitch((float) -45.0);
                if (scaffoldGrim.isActive()) {
                    scaffoldGrim.toggle();
                }
            } else {
                state = "resumeBuilding";
                if (!scaffoldGrim.isActive()) {
                    scaffoldGrim.toggle();
                }
            }
            dimension = mc.world.getDimension();
        }
        Module iceHighwayBuilder = Modules.get().get("ice-highway-builder");
        if (state.equals("retrievingFromShulker")) {
            handleItemRetrieve(retrieveCount, retrieveItem);
            return;
        }
        if (state.equals("waitingForPostRestock") || state.equals("waitingForGather") || isClearingInventory) {
            return;
        }
        if (state.equals("miningEchests")) {
            if (getEchestSlot() != -1) {
                echestSlot = getEchestSlot();
            }
            if (!mc.player.getBlockPos().equals(restockingStartPosition)) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockingStartPosition));
                resumeBaritone();
                return;
            }
            if (mc.world.getBlockState(shulkerBlockPos).getBlock() != Blocks.ENDER_CHEST) {
                BlockUtils.place(shulkerBlockPos, InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.ENDER_CHEST), true, 0, true, true);
            } else {
                int swapSlot = -1;
                for (int i = 0; i < 9; i++) {
                    if (isNotSilkPick(mc.player.getInventory().getStack(i))) {
                        swapSlot = i;
                        break;
                    }
                }
                if (swapSlot == -1) {
                    for (int i = 9; i < 36; i++) {
                        if (isNotSilkPick(mc.player.getInventory().getStack(i))) {
                            InvUtils.quickSwap().fromId(3).toId(i);
                            swapSlot = 3;
                            break;
                        }
                    }
                }
                InvUtils.swap(swapSlot,false);
                assert mc.getNetworkHandler() != null;
                lookAtBlock(shulkerBlockPos);
                packetMine(shulkerBlockPos);
            }
            error("Echest slot " + echestSlot + " initialechests " + initialEchests);
            if (initialEchests - mc.player.getInventory().getStack(echestSlot).getCount() > 0) {
                disablemodules();
                oldYaw = mc.player.getYaw();
                restockingType = 2;
                isClearingInventory = true;
                state = "waitingForClearInventory";
                return;
            }
        }
        if (state.equals("goToPortal")) {
            //check if there are under 5 usable pickaxes
            reached = false;
            if (search(null, 6, 2) && countUsablePickaxes() < 5) {
                if (search(null, 5, 1)) {
                    //check if there are non-silk pickaxes to get xp
                    returnToState = "goToPortal";
                    repairCount = 5;
                    state = "repairingPickaxes";
                    error("repairingPickaxes");
                    return;
                } else {
                    error("Too low on pickaxes and no non-silk pickaxes for repairing.");
                    disableAllModules();
                    isPathing = false;
                    toggle();
                    return;
                }
            }
            if (!search(Items.ELYTRA, 6, 3)) {
                error("No elytra, cannot fly to blue ice.");
                disableAllModules();
                toggle();
                return;
            } else {
                boolean test = false;
                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.ELYTRA) {
                        test = true;
                        break;
                    }
                }
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {test = true;}
                if (test) {
                    if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
                            ItemStack item = mc.player.getInventory().main.get(i);

                            if (item.getItem() == Items.ELYTRA) {
                                InvUtils.move().from(i).toArmor(2);
                                break;
                            }
                        }
                    }
                } else {
                    error("no elytra found");
                    return;
                }
            }
            if (!search(Items.FIREWORK_ROCKET, 4, 3)) {
                error("No firework rockets, cannot fly to blue ice.");
                disableAllModules();
                toggle();
                return;
            } else {
                int slot = -1;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.FIREWORK_ROCKET) {
                        slot = i;
                        break;
                    }
                }
                if (slot > 8) {
                    InvUtils.quickSwap().fromId(4).toId(slot);
                    return;}
            }

            //search render distance for nether_portal, only search once to avoid lag
            if (scanningWorld) {
                scanningWorld = false;
                foundBlock = !searchWorld(Blocks.NETHER_PORTAL).isEmpty();
                error("scanningWorld found? " + foundBlock);
            }
            //go to the coords of the nearest group of nether portal frames if the player isn't there already
            if (foundBlock) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                isPathing = true;
                error("going to nearest coords");
                return;
            }
            //search moves the item to the slot if it returns true
            if (!search(Items.OBSIDIAN, 6, 0) || !buildNetherPortal.get()) {
                // search for a non-silk touch pickaxe
                if (!search(null, 5, 1) || !buildNetherPortal.get()) {
                    if (searchOnHighway.get()) {
                        error("search on highway");
                        BlockPos goal = switch (getPlayerDirection()) {
                            case NORTH, SOUTH -> new BlockPos(0, 120, mc.player.getBlockZ());
                            case EAST, WEST -> new BlockPos(mc.player.getBlockX(), 120, 0);
                            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
                        };
                        boolean test1 = switch (getPlayerDirection()) {
                            case NORTH, SOUTH -> mc.player.getBlockX() == 0;
                            case EAST, WEST -> mc.player.getBlockZ() == 0;
                            default -> false;
                        };
                        //  if searchOnHighway is enabled and player isn't yet at the highway then path to it
                        if (test1) {
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                            resumeBaritone();
                            error(String.valueOf(goal.withY(mc.player.getBlockY())));
                            isPathing = true;
                            return;
                        } else {
                            //if player is already at highway then walk forwards (add elytra bounce later)
                            BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                            resumeBaritone();
                            isPathing = true;
                            scanningWorld = tick % 200 == 0;
                            return;
                        }

                    } else {
                        error("No obsidian, echest, or non-silk touch pickaxe in inventory, and searchOnHighway is disabled.");
                        disableAllModules();
                        toggle();
                        return;
                    }
                } else {
                    if(search(Items.ENDER_CHEST, 5, 0)) {
                        getRestockingStartPos();
                        shulkerBlockPos = mc.player.getBlockPos();
                        initialEchests = mc.player.getInventory().getStack(getEchestSlot()).getCount();
                        state = "miningEchests";
                        NewState = "waitingForGather";
                        returnToState = "goToPortal";
                        return;
                    } else {
                        error("No echests, cannot get obsidian for the portal");
                        disableAllModules();
                        toggle();
                        return;
                    }
                }
            } else {
                if (search(Items.FLINT_AND_STEEL, 5, 0)) {
                    portalOriginBlock = null;
                    buildTimer = 0;
                    state = "buildPortal";
                    returnToState = "goToPortal";
                    return;
                } else {
                    error("No flint and steel found, cannot light portal");
                    disableAllModules();
                    isPathing = false;
                    toggle();
                    return;
                }

            }
        }
        Module autoMend = Modules.get().get("auto-mend");
        if (state.equals("repairingPickaxes")) {
            if (!autoMend.isActive()) {autoMend.toggle();}
            int blocksToMine = Math.round((float) (repairCount * 1561) /7);
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(blocksToMine, "minecraft:nether_quartz_ore");
            resumeBaritone();
            isPathing = true;
            reached = false;
            if (search(null, 5, 2)) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                scanningWorld = true;
                state = returnToState;
            }
        } else {
            if (autoMend.isActive()) {autoMend.toggle();}
        }
        if (state.equals("buildPortal")) {
            assert (getPlayerDirection() != null);
            int goalOff;
            boolean test;
            goalOff = -210;
            test = switch (getPlayerDirection()) {
                case NORTH, SOUTH -> mc.player.getBlockX() != goalOff;
                case EAST, WEST -> mc.player.getBlockZ() != goalOff;
                default -> false;
            };
            switch (getPlayerDirection()) {
                case NORTH, SOUTH -> {
                    if (mc.player.getBlockX() > goalOff + 2) {
                        reached = false;
                    }
                }
                case EAST, WEST -> {
                    if (mc.player.getBlockZ() > goalOff + 2) {
                        reached = false;
                    }
                }
                default -> {
                    reached = false;
                }
            }
            if (test && !reached) {
                portalOriginBlock = null;
                BlockPos goal = switch (getPlayerDirection()) {
                    case NORTH, SOUTH -> new BlockPos(goalOff, 114, mc.player.getBlockZ());
                    case EAST, WEST -> new BlockPos(mc.player.getBlockX(), 114, goalOff);
                    default -> new BlockPos(0, 0, 0); // This shouldn't happen.
                };
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                isPathing = true;
                return;
            } else {
                reached = true;
                isPathing = true;
                //only generate a new portal location if it is null
                if (portalOriginBlock == null) {
                    switch (getPlayerDirection()) {
                        case NORTH, SOUTH -> {
                            portalOriginBlock = mc.player.getBlockPos().east(-1);
                        }
                        case EAST, WEST -> {
                            portalOriginBlock = mc.player.getBlockPos().south(-1);
                        }
                    }                    ;
                }
                error(String.valueOf(portalOriginBlock));
                ArrayList<Integer> offset = new ArrayList<Integer>(Arrays.asList(
                        0,0,1,0,2,0,3,0,
                        0,1,1,1,2,1,3,1,
                        0,2,1,2,2,2,3,2,
                        0,3,1,3,2,3,3,3,
                        0,4,1,4,2,4,3,4)
                );
                portalObby = new ArrayList<Integer>(Arrays.asList(1,1,1,1,1,0,0,1,1,0,0,1,1,0,0,1,1,1,1,1));
                portalBlocks = new ArrayList<BlockPos>();
                assert portalOriginBlock != null;
                for (int i = 0; i < 20; i++) {
                    switch (getPlayerDirection()) {
                        case NORTH, SOUTH -> {
                            portalBlocks.add(portalOriginBlock.south(offset.get(i*2)).up(offset.get(i*2 + 1)));
                        }
                        case EAST, WEST -> {
                            portalBlocks.add(portalOriginBlock.east(offset.get(i*2)).up(offset.get(i*2 + 1)));
                        }
                    };
                }
            }
            buildTimer++;
            //make sure the block is air
            if (buildTimer < 200) {
                isPathing = true;
                BlockPos target = portalBlocks.get((int)((buildTimer)/10));
                boolean placeObby = (portalObby.get((int)((buildTimer)/10))==1);
                createPortal(target, placeObby);
                return;
            } else {
                if (buildTimer < 280) {
                    BlockPos target = portalBlocks.get((int)((buildTimer-200)/4));
                    boolean placeObby = (portalObby.get((int)((buildTimer-200)/4))==1);
                    createPortal(target, placeObby);
                    return;
                } else {
                    if (mc.world.getBlockState(portalBlocks.get(2).up(1)).getBlock() != Blocks.NETHER_PORTAL) {
                        isPathing = true;
                        for (int i = 0; i < 9; i++) {
                            if (mc.player.getInventory().getStack(i).getItem() == Items.FLINT_AND_STEEL) {
                                InvUtils.swap(i, false);
                            }
                        }
                        IceHighwayBuilder.lookAtBlock(portalBlocks.get(2));
                        //right click
                        if (!mc.player.isUsingItem() && buildTimer % 5 == 0) {
                            Utils.rightClick();
                        }
                    } else {
                        //portal finished
                        scanningWorld = true;
                        isPathing = false;
                        state = returnToState;
                        return;
                    }
                }
            }
        }
        boolean isInFrozenOcean = mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN));
        if (state.equals("flyToBlueIce")) {
            isPathing = true;
            if (isInFrozenOcean && !hasFoundFrozenOcean) {
                hasFoundFrozenOcean = true;
            }
            if (scanningWorld) {
                scanningWorld = false;
                getBlockGroups(Blocks.BLUE_ICE);
                if (iceBergDistances == null) {
                    foundBlock = false;
                    return;
                }
                foundBlock = !iceBergDistances.isEmpty();
                return;
            }
            if (foundBlock) {
                BlockPos nearestCoord = nearestGroupCoords(true);
                landCoords = nearestCoord.withY(getMaxY(nearestCoord));
                if (PlayerUtils.isWithin(new Vec3d(landCoords.getX(),mc.player.getBlockY(),landCoords.getZ()),5)) {
                    state = "land";
                    vertex = landCoords.withY(mc.player.getBlockY());
                    returnToState = "goToBlueIce";
                } else {
                    float[] angles = PlayerUtils.calculateAngle(new Vec3d(landCoords.getX(),landCoords.getY(),landCoords.getZ()));
                    mc.player.setYaw(angles[0]);
                    mc.player.setPitch(15);
                }
                return;
            }
            int fireworkSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                    fireworkSlot = i;
                    break;
                }
            }
            if (fireworkSlot == -1) {
                for (int i = 9; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                        InvUtils.quickSwap().fromId(5).toId(i);
                        prevCount = mc.player.getInventory().getStack(fireworkSlot).getCount();
                        fireworkSlot = 5;
                        break;
                    }
                }
            }
            if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.NETHER_PORTAL) {
                BlockPos pos = mc.player.getBlockPos();
                BlockPos goal;
                if (mc.player.getY() > getMaxY(pos.east(2))) {
                    goal = pos.east(2).withY(getMaxY(pos.east(2)));
                } else if (mc.player.getY() > getMaxY(pos.east(-2))) {
                    goal = pos.east(-2).withY(getMaxY(pos.east(-2)));
                } else if (mc.player.getY() > getMaxY(pos.south(2))) {
                    goal = pos.east(2).withY(getMaxY(pos.south(2)));
                } else {
                    goal = pos.east(-2).withY(getMaxY(pos.south(-2)));
                }
                isPathing = true;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                resumeBaritone();
                prevCount = mc.player.getInventory().getStack(fireworkSlot).getCount();
                return;
            }
            if (isInFrozenOcean && tick % 60 == 30) {
                scanningWorld = true;
            }

            //pitch and firework control
            if (mc.player.getY() < cruiseAltitude.get()) {
                InvUtils.swap(fireworkSlot, false);
                //use fireworks every 4 seconds while spamming spacebar
                //if the elytra is open but player is still near the ground then spam fireworks faster
                if (tick % (mc.player.getY() < getMaxY(mc.player.getBlockPos())+5 ? 10 : 80) == 0) {
                    mc.player.setPitch((float) -45.0);
                    if (mc.player.isFallFlying()) {
                        Utils.rightClick();
                    }
                } else {
                    setKeyPressed(mc.options.jumpKey, tick % 4 < 2);
                }
            } else {
                double velocitySquared = Math.pow(mc.player.getVelocity().x,2)+Math.pow(mc.player.getVelocity().y,2)+Math.pow(mc.player.getVelocity().z,2);
                //velocitySquared is the square of the blocks per tick
                if (velocitySquared < 0.25) {
                    mc.player.setPitch((float) 15.0);
                    setKeyPressed(mc.options.jumpKey, false);}
            }
            //yaw control
            if (!hasFoundFrozenOcean) {
                float yaw = switch(getPlayerDirection()) {
                    case NORTH -> (float)90;
                    case SOUTH -> (float)-90;
                    case EAST -> (float)180;
                    case WEST -> (float)0;
                    default -> 0;
                };
                if (isInColdBiome() || leg > 0) {
                    //if player is in a cold biome then there will likely be a frozen ocean nearby so it will fly in an isosceles triangle shape
                    double squaredDistance = Math.pow(vertex.getX()-mc.player.getX(), 2)+Math.pow(vertex.getZ()-mc.player.getZ(), 2);
                    if (leg == 0) {
                        leg = 1;
                    }
                    if (leg == 1) {
                        mc.player.setYaw(yaw-45);
                        if (squaredDistance > 9000000) {
                            vertex = mc.player.getBlockPos();
                            leg = 2;
                        }
                    }
                    if (leg == 2) {
                        mc.player.setYaw(yaw+90);
                        if (squaredDistance > 18000000) {
                            vertex = mc.player.getBlockPos();
                            leg = 3;
                        }
                    }
                    if (leg == 3) {
                        mc.player.setYaw(yaw-135);
                        if (squaredDistance > 9000000) {
                            vertex = mc.player.getBlockPos();
                            leg = 0;
                        }
                    }
                } else {
                    mc.player.setYaw(yaw);
                    vertex = mc.player.getBlockPos();
                }
            }
        }

        if (state.equals("land")) {
            isPathing = true;
            if (mc.player.getY() > landCoords.getY()) {
                double xDiff = vertex.getX()-mc.player.getX();
                if (xDiff > 5) {
                    mc.player.setYaw(-90);
                } else if (xDiff < -5) {
                    mc.player.setYaw(90);
                }
                if (mc.player.getY() > landCoords.getY() + 150) {
                    mc.player.setPitch(90);
                } else if (mc.player.getY() > landCoords.getY() + 30) {
                    mc.player.setPitch(45);
                } else {
                    mc.player.setPitch(15);
                }
                return;
            } else {
                state = returnToState;
                return;
            }
        }
        if (state.equals("goToBlueIce")) {
            if (iceRailAutoReplenish.isActive()) {
                iceRailAutoReplenish.toggle();
            }
            ArrayList<BlockPos> range = getBlueIceInRange();
            error("current iceberg" + groups.get(currentIceberg+1));
            if (range.isEmpty()) {
                if ((int)groups.get(currentIceberg+1) == 0) {
                    if (!isInFrozenOcean && tick % 60 == 0) {
                        mc.player.setYaw((float)Math.random()*360-180);
                    }
                    state = "flyToBlueIce";
                } else {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.BLUE_ICE);
                    resumeBaritone();
                }
                return;
            } else {
                state = "mineBlueIceInRange";
                return;
            }
        }
        if (state.equals("mineBlueIceInRange")) {
            error("blue ice in current iceberg: " + groups.get(currentIceberg+1));
            if (iceRailAutoReplenish.isActive()) {iceRailAutoReplenish.toggle();}
            ArrayList<BlockPos> range = getBlueIceInRange();
            if (range.isEmpty()) {
                state = "goToBlueIce";
                return;
            } else {
                if (useSpeedmine.get()) {
                    Direction startDirection = mc.player.getHorizontalFacing();
                    BlockPos pos = range.getFirst();
                    assert mc.getNetworkHandler() != null;
                    switchToBestTool(pos);
                    if (startDirection != null)
                        if (doubleMine.get()) {
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, startDirection));
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, startDirection));
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, startDirection));
                        } else {
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, startDirection));
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, startDirection));
                        }
                } else {
                    lookAtBlock(range.getFirst());
                    BlockUtils.breakBlock(range.getFirst(),true);
                }
                return;
            }
        }
    }

    private void disablemodules() {
        Module iceRailNuker = Modules.get().get("ice-rail-nuker");
        Module icePlacer = Modules.get().get("ice-placer");
        assert mc.player != null;

        getRestockingStartPos();
        if (icePlacer.isActive()) {
            if (getIsEating()) { // Toggle off
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
        }
    }

    private void createPortal(BlockPos target, boolean placeObby) {
        if (buildTimer % 10 < 3) {
            switch (getPlayerDirection()) {
                case NORTH, SOUTH -> {
                    if (!mc.world.getBlockState(target.east(1)).isAir()) {
                        packetMine(target.east(1));
                    }
                }
                case EAST, WEST -> {
                    if (!mc.world.getBlockState(target.south(1)).isAir()) {
                        packetMine(target.south(1));
                    }
                }
            };
            //place the block
        } else if (buildTimer % 10 < 6) {
            if (!mc.world.getBlockState(target).isAir() && mc.world.getBlockState(target).getBlock() != Blocks.OBSIDIAN) {
                packetMine(target);
            }
        } else if (buildTimer % 10 == 8) {
            if (placeObby) {
                if (mc.world.getBlockState(target).getBlock() != Blocks.OBSIDIAN) {
                    BlockUtils.place(target, InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.OBSIDIAN), true, 0, true, true);
                }
            }
            //delay
        }
    }

    private void getRestockingStartPos() {
        restockingStartPosition = switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() + 2);
            case SOUTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() - 2);
            case EAST -> new BlockPos(mc.player.getBlockX() - 2, mc.player.getBlockY(), mc.player.getBlockZ());
            case WEST -> new BlockPos(mc.player.getBlockX() + 2, mc.player.getBlockY(), mc.player.getBlockZ());
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }

    private void shulkerRestock(int count, Item item, int type) {
        //runs inventory clear and stops onTick.
        //state will then cycle through the following before getting reset (to returnToState)


        //retrievingFromShulker
        //waitingForPostRestock
        //waitingForGather

        disablemodules();
        restockingType = 2;
        oldYaw = mc.player.getYaw();
        returnToState = state;
        NewState = "retrievingFromShulker";
        isClearingInventory = true;
        retrieveCount = 1;
        retrieveItem = item;
        retrieveType = type;
        if (type > 2) {retrieveType = 0;}
    }
    private @NotNull BlockPos getBlockPos() {
        int offset = 0;
        assert mc.player != null;
        return switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() + offset);
            case SOUTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() - offset);
            case EAST -> new BlockPos(mc.player.getBlockX() + offset, mc.player.getBlockY(), mc.player.getBlockZ());
            case WEST -> new BlockPos(mc.player.getBlockX() - offset, mc.player.getBlockY(), mc.player.getBlockZ());
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }
    private void handleItemRetrieve(int count, Item item) {
        if (isGatheringItems()) {
            state = "idle";
            return;}


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
        error(String.valueOf(shulkerBlockPos));

        if (hasLookedAtShulker < 10) { // To add a 10 tick delay
            if (hasLookedAtShulker == 0) {
                InvUtils.swap(5, false);
                lookAtBlock(shulkerBlockPos.withY(mc.player.getBlockY() - 1)); // To minimize the chance of the shulker being placed upside down
            }


            hasLookedAtShulker++;
            return;
        }


        if (!(mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock)) {
            if (BlockUtils.canPlace(shulkerBlockPos, false) && !BlockUtils.canPlace(shulkerBlockPos, true)) return;
            place(shulkerBlockPos, Hand.MAIN_HAND, 5, true, true, true);
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
        if ((stacksStolen >= count) || stacksStolen >= 27) {
            // Run post restocking
            isPostRestocking = true;


            stacksStolen = 0;
            slotNumber = 0;

            wasRestocking = true;
            state = "waitingForPostRestock";
            isPause = false;
            isPlacingShulker = false;
            restockingStartPosition = null;
            hasLookedAtShulker = 0;
            stealingDelay = 0;
            hasOpenedShulker = false;
            isRestocking = false;
        } else {
            ScreenHandler handler = mc.player.currentScreenHandler;
            ItemStack slotStack = handler.getSlot(slotNumber).getStack();
            Item slotItem = slotStack.getItem();
            if (hasOpenedShulker) {
                boolean condition;
                if (retrieveType == 0){
                    condition = (slotItem != item);
                } else {
                    condition = !isNotSilkPick(slotStack);
                }
                if (stealingDelay < 5) { // To add a 5 tick delay
                    stealingDelay++;
                    return;
                }
                if (!condition) {
                    steal(handler, slotNumber);
                }
                slotNumber++;
                stealingDelay = 0;
                //THe glitch is that it's searching in the inventory, not the shulker box
            }
        }
    }
    public boolean getIsPathing() {
        return isPathing;
    }
}
