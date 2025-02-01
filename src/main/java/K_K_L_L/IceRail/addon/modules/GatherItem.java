package K_K_L_L.IceRail.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import K_K_L_L.IceRail.addon.IceRail;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static K_K_L_L.IceRail.addon.Utils.*;

public class GatherItem extends Module {
    private int tickCounter = 0;
    private static ClientPlayerEntity player;
    private static ScheduledExecutorService scheduler1;

    public static final int SEARCH_RADIUS = 15;
    private static final int MAX_Y = 122;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
            .name("item")
            .description("Item to gather")
            .defaultValue(Items.OBSIDIAN)
            .build()
    );

    public GatherItem() {
        super(IceRail.CATEGORY, "gather-item", "A helper module that gathers nearby items.");
    }

    private record ItemLocation(BlockPos pos, boolean isAccessible, double distanceToPlayer) {
        static boolean isLocationAccessible(World world, BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            return state.isAir() ||
                    state.isReplaceable() ||
                    !state.getFluidState().isEmpty();
        }
    }

    @Override
    public void onActivate() {
        player = mc.player;
        reinitializeScheduler();
    }

    @Override
    public void onDeactivate() {
        if (scheduler1 != null && !scheduler1.isShutdown()) {
            scheduler1.shutdownNow();
        }
        cancelCurrentProcessBaritone();
    }

    private static void reinitializeScheduler() {
        if (scheduler1 != null && !scheduler1.isShutdown()) {
            scheduler1.shutdownNow();
        }
        scheduler1 = Executors.newScheduledThreadPool(1);
    }

    private List<BlockPos> findItemLocations() {
        if (player == null || player.getWorld() == null) {
            return new ArrayList<>();
        }

        World world = player.getWorld();
        Item targetItem = item.get();

        Box searchArea = new Box(
                player.getX() - SEARCH_RADIUS,
                player.getY() - SEARCH_RADIUS,
                player.getZ() - SEARCH_RADIUS,
                player.getX() + SEARCH_RADIUS,
                MAX_Y,
                player.getZ() + SEARCH_RADIUS
        );

        List<ItemLocation> itemLocations = new ArrayList<>();

        world.getEntitiesByClass(ItemEntity.class, searchArea,
                        itemEntity -> itemEntity.getStack().getItem() == targetItem)
                .forEach(itemEntity -> {
                    BlockPos itemPos = new BlockPos(
                            (int)Math.floor(itemEntity.getX()),
                            (int)Math.floor(itemEntity.getY()),
                            (int)Math.floor(itemEntity.getZ())
                    );

                    double distance = Math.sqrt(
                            Math.pow(player.getX() - itemEntity.getX(), 2) +
                                    Math.pow(player.getY() - itemEntity.getY(), 2) +
                                    Math.pow(player.getZ() - itemEntity.getZ(), 2)
                    );

                    boolean isAccessible = ItemLocation.isLocationAccessible(world, itemPos);
                    itemLocations.add(new ItemLocation(itemPos, isAccessible, distance));
                });

        return itemLocations.stream()
                .sorted(Comparator
                        .comparing((ItemLocation loc) -> !loc.isAccessible)
                        .thenComparing(loc -> loc.distanceToPlayer))
                .map(loc -> loc.pos)
                .toList();
    }

    private boolean canNotHoldMoreItems() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return true;

        Item targetItem = item.get();
        int totalItemSpace = 0;

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            if (mc.player.getInventory().main.get(i).isOf(Items.AIR)) {
                totalItemSpace += 64;
            } else if (mc.player.getInventory().main.get(i).isOf(targetItem)) {
                totalItemSpace += 64 - mc.player.getInventory().main.get(i).getCount();
            }
        }

        return totalItemSpace <= 0;
    }

    private void moveToItemLocations(List<BlockPos> locations, int index) {
        if (scheduler1 == null || scheduler1.isShutdown()) {
            return;
        }

        if (index >= locations.size()) {
            moveToNextItem();
            return;
        }

        if (canNotHoldMoreItems()) {
            goToHighwayCoords(Modules.get().get("ice-highway-builder").isActive());

            scheduler1.shutdownNow();
            return;
        }

        BlockPos targetLocation = locations.get(index);
        if (targetLocation != null) {
            BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getCustomGoalProcess()
                    .setGoalAndPath(new GoalBlock(targetLocation));
            resumeBaritone();
        }

        scheduler1.schedule(() -> {
            if (hasReachedLocation(player, targetLocation)) {
                scheduler1.schedule(this::moveToNextItem, 50, TimeUnit.MILLISECONDS);
            } else {
                scheduler1.schedule(() -> moveToItemLocations(locations, index), 50, TimeUnit.MILLISECONDS);
            }
        }, 50, TimeUnit.MILLISECONDS);
    }

    private static boolean hasReachedLocation(ClientPlayerEntity player, BlockPos location) {
        return player != null && player.getBlockPos().isWithinDistance(location, 1);
    }

    public void findNearbyItem() {
        reinitializeScheduler();
        resumeBaritone();
        moveToNextItem();
    }

    private void moveToNextItem() {
        List<BlockPos> itemLocations = findItemLocations();

        if (itemLocations.isEmpty() || canNotHoldMoreItems()) {
            if (itemLocations.isEmpty())
                ChatUtils.info("No more " + item.get().getName().getString() + " found nearby.");
            else
                ChatUtils.info("No more space in inventory.");

            goToHighwayCoords(Modules.get().get("ice-highway-builder").isActive());

            toggle();
            return;
        }

        moveToItemLocations(itemLocations, 0);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Module betterEChestFarmer = Modules.get().get("better-EChest-farmer");
        if (betterEChestFarmer != null && betterEChestFarmer.isActive()) {
            return;
        }

        List<BlockPos> itemLocations = findItemLocations();

        if (itemLocations.isEmpty() || canNotHoldMoreItems()) {
            toggle();
            return;
        }

        if (mc.player != null) {
            tickCounter++;
            if (tickCounter % 2 == 0) {
                if (checkItemsOnGround(findItemLocations()))
                    findNearbyItem();
            }
        }
    }
}
