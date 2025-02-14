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
import net.minecraft.enchantment.*;
import java.util.*;

import static meteordevelopment.meteorclient.utils.world.BlockUtils.getPlaceSide;

import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestBlueIceShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestPicksShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findPickToSwap;

public class BlueIceMiner extends Module{
    public static ArrayList<Object> iceBergs = new ArrayList<>();
    public static boolean miningIce = false;
    public static String state = "idle";
    public static String returnToState = "idle";
    public static int retrieveCount;
    public static int retrieveItem;
    public static int retrieveType;

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
    // Blue Ice mining settings
    private final Setting<Integer> icebergSizeThreshold = sgMining.add(new IntSetting.Builder()
            .name("iceberg-size-threshold")
            .description("Mines the iceberg if it has at least this much blue ice.")
            .defaultValue(400)
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
    private boolean isSilkPick(ItemStack stack) {
        return (stack.getItem() instanceof PickaxeItem && stack.getMaxDamage() - stack.getDamage() > 50
                    && stack.getEnchantments();
    }
    //searches the hotbar, inventory, shulkers in inventory then swaps it to the slot
    public boolean search(Item item, int slot, int type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Inventory inventory = mc.player.getInventory();
        ItemStack hotbarStack = inventory.getStack(slot);
        boolean condition = false;

        if (type == 0) {
            condition = (hotbarStack.getItem() == item);
        } else {
            condition = (hotbarStack.getItem() instanceof PickaxeItem && hotbarStack.getMaxDamage() - hotbarStack.getDamage() > 50);
        }
        if (condition) {
            return true; // The slot already has the right item
        }
 
        int bestSlot = -1;
        
        for (int i = 0; i < inventory.size(); i++) {
            if (i == slot) continue; // Skip the  slot that has already been checked
 
            ItemStack stack = inventory.getStack(i);
            if (type == 0) {
                condition = (stack.getItem() == item);
            } else {
                condition = (stack.getItem() instanceof PickaxeItem && stack.getMaxDamage() - stack.getDamage() > 50);
            }
            if (condition) {
                bestSlot = i;
                break;
            }
        }

        if (bestSlot != -1) {
            InvUtils.quickSwap().fromId(6).toId(bestSlot);
            return true;
        }
        //search the shulkers in the inventory
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof BlockItem &&
                    ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                
                boolean Return = false;
                ItemStack[] containerItems = new ItemStack[27];
                Utils.getItemsInContainerItem(stack, containerItems);
                
                for (ItemStack stack : containerItems) {
                    if (type == 0) {
                        condition = (stack.getItem() == item);
                    } else {
                        condition = (stack.getItem() instanceof PickaxeItem && stack.getMaxDamage() - stack.getDamage() > 50);
                    }
                    if (!stack.isEmpty() && condition) {
                        Return = true;
                    }
                }

                if (Return) {
                    bestSlot = i;
                    break;
                }
            }
        }

        if (bestSlot != -1) {
            shulkerRestock(1, item, type);
            return true;
        }
        return false;
    }
    private void shulkerRestock(int count, Item item, int type) {
        //runs inventory clear and stops onTick.
        //state will then cycle through the following before getting reset (to returnToState)

        //retrievingFromShulker
        //waitingForPostRestock
        //waitingForGather

        restockingStartPosition = mc.player.getBlockPos();
        if (icePlacer.isActive()) {
            if (getIsEating()) { // Toggle off
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
        }
        restockingType = 2;
        slotNumber = 0;
        stealingDelay = 0;
        oldYaw = mc.player.getYaw();
        returnToState = state;
        isClearingInventory = true;
        retrieveCount = 1;
        retrieveItem = item;
        retrieveType = type;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null) return;
        if (state == "retrievingFromShulker") {
            handleItemRetrieve(retrieveCount, retrieveItem);
            return;
        }
        if (state == "waitingForPostRestock" || state == "waitingForGather" || isClearingInventory) {
            return;
        }
        if (state == "goToPortal") {
            //search moves the item to the slot if it returns true
            if (!search(ITEMS.OBSIDIAN, 6, 0)) {
                if (!search(ITEMS.DIAMOND_PICKAXE, 5, 1))
            }
            return;
        }
        
    }

    private void handleItemRetrieve(int count, Item item) {
        if (isGatheringItems()) {state == "idle"; return;}

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
                InvUtils.swap(6, false);
                lookAtBlock(shulkerBlockPos.withY(playerY - 1)); // To minimize the chance of the shulker being placed upside down
            }   

            hasLookedAtShulker++;
            return;
        }

        if (!(mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock)) {
            if (BlockUtils.canPlace(shulkerBlockPos, false) && !BlockUtils.canPlace(shulkerBlockPos, true)) return;
            place(shulkerBlockPos, Hand.MAIN_HAND, 6, true, true, true);
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
            ItemStack slotStack = mc.player.getInventory().getStack(slotNumber);
            Item slotItem = slotStack.getItem();
            if (hasOpenedShulker) {
                boolean condition;
                if (retrieveType == 0){
                    condition = (slotItem != item);
                } else {
                    condition = !(slotItem instanceof PickaxeItem && slotStack.getMaxDamage() - slotStack.getDamage() > 50);
                }
                if (stealingDelay < 5 || condition) { // To add a 5 tick delay
                    stealingDelay++;
                    return;
                }
                steal(handler, slotNumber);
                slotNumber++;
                stealingDelay = 0;
            }
        }
    }
}
