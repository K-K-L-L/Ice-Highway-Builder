
package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import K_K_L_L.IceRail.addon.IceRail;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Direction;

import java.util.List;

import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.getPlayerDirection;

public class ScaffoldGrim extends Module {
    public ScaffoldGrim() {
        super(IceRail.CATEGORY, "scaffold-grim", "Places blocks in front of you.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
            .name("blocks-filter")
            .description("How to use the block list setting")
            .defaultValue(ListMode.Blacklist)
            .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Selected blocks.")
            .build()
    );

    private int tickCounter = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;
        Direction playerDirection;

        // Only place a block every 3 ticks to avoid rubberband
        if (tickCounter % 3 != 0) return;
        if (!isActive()) return;
        if (getPlayerDirection() == null) {
            playerDirection = mc.player.getHorizontalFacing();
        } else
            playerDirection = getPlayerDirection();

        BlockPos playerPos = mc.player.getBlockPos();
        Item item = null;

        // Find a block in the player's inventory
        for (int i = 0; i < 9; i++) {
            Item foundItem = mc.player.getInventory().getStack(i).getItem();
            if (foundItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();

                // Check blacklist/whitelist
                if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) continue;
                if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) continue;

                item = foundItem;
                break;
            }
        }

        if (item == null) return;

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos targetPos = switch (playerDirection) {
                case NORTH -> playerPos.add(0, -1, -offset);
                case SOUTH -> playerPos.add(0, -1, offset);
                case EAST -> playerPos.add(offset, -1, 0);
                case WEST -> playerPos.add(-offset, -1, 0);
                default -> null;
            };

            if (targetPos == null) return;
            if (mc.getNetworkHandler() == null) return;
            assert mc.world != null;
            if (mc.world.getBlockState(targetPos).getBlock() == Blocks.SOUL_SAND) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, BlockUtils.getDirection(targetPos)));
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, BlockUtils.getDirection(targetPos)));
            }
            if (mc.world.getBlockState(targetPos).isAir()) {

                if (placeBlock(item, targetPos, playerDirection)) {
                    break;
                }
            }
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
