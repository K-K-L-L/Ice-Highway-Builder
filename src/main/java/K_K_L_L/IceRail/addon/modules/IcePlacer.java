package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import K_K_L_L.IceRail.addon.IceRail;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.fluid.FluidState;
import net.minecraft.block.BlockState;

import static K_K_L_L.IceRail.addon.Utils.switchToItem;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;

public class IcePlacer extends Module {
    public IcePlacer() {
        super(IceRail.CATEGORY, "ice-placer", "Places ice blocks with air gaps between them.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (isGoingToHighway || getIsEating()) return;

        Direction direction = getPlayerDirection();
        if (direction == null) {
            toggle();
            return;
        }

        boolean shouldPlace;
        BlockPos targetPos, targetPos2, guardrail1, guardrail2;

        switch (direction) {
            case NORTH -> {
                targetPos = new BlockPos(playerX + 1, playerY + 1, mc.player.getBlockZ() - 2);
                targetPos2 = new BlockPos(playerX + 2, playerY + 1, mc.player.getBlockZ() - 2);
                guardrail1 = new BlockPos(playerX - 1, playerY + 2, mc.player.getBlockZ() - 2);
                guardrail2 = new BlockPos(playerX + 2, playerY + 2, mc.player.getBlockZ() - 2);
                shouldPlace = Math.abs(mc.player.getBlockZ()) % 2 == 0;
            }
            case SOUTH -> {
                targetPos = new BlockPos(playerX + 1, playerY + 1, mc.player.getBlockZ() + 2);
                targetPos2 = new BlockPos(playerX + 2, playerY + 1, mc.player.getBlockZ() + 2);
                guardrail1 = new BlockPos(playerX - 1, playerY + 2, mc.player.getBlockZ() + 2);
                guardrail2 = new BlockPos(playerX + 2, playerY + 2, mc.player.getBlockZ() + 2);
                shouldPlace = Math.abs(mc.player.getBlockZ()) % 2 == 0;
            }
            case WEST -> {
                targetPos = new BlockPos(mc.player.getBlockX() - 2 , playerY + 1, playerZ - 1);
                targetPos2 = new BlockPos(mc.player.getBlockX() - 2 , playerY + 1, playerZ - 2);
                guardrail1 = new BlockPos(mc.player.getBlockX() - 2 , playerY + 2, playerZ - 2);
                guardrail2 = new BlockPos(mc.player.getBlockX() - 2 , playerY + 2, playerZ + 1);
                shouldPlace = Math.abs(mc.player.getBlockX()) % 2 == 0;
            }
            case EAST -> {
                targetPos = new BlockPos(mc.player.getBlockX() + 2, playerY + 1, playerZ - 1);
                targetPos2 = new BlockPos(mc.player.getBlockX() + 2 , playerY + 1, playerZ - 2);
                guardrail1 = new BlockPos(mc.player.getBlockX() + 2 , playerY + 2, playerZ - 2);
                guardrail2 = new BlockPos(mc.player.getBlockX() + 2 , playerY + 2, playerZ + 1);
                shouldPlace = Math.abs(mc.player.getBlockX()) % 2 == 0;
            }
            default -> {
                return;
            }
        }
        if (mc.world.isAir(guardrail1)) {
            switchToItem(Items.NETHERRACK);
            BlockUtils.place(guardrail1, InvUtils.findInHotbar(itemStack ->
                    itemStack.getItem() == Items.NETHERRACK), false, 0, true, true);
            return;
        }
        if (mc.world.isAir(guardrail2)) {
            switchToItem(Items.NETHERRACK);
            BlockUtils.place(guardrail2, InvUtils.findInHotbar(itemStack ->
                    itemStack.getItem() == Items.NETHERRACK), false, 0, true, true);
            return;
        }
        if (!mc.world.getBlockState(guardrail1.up(-1)).getFluidState().isEmpty()) {
            switchToItem(Items.NETHERRACK);
            BlockUtils.place(guardrail1.up(-1), InvUtils.findInHotbar(itemStack ->
                    itemStack.getItem() == Items.NETHERRACK), false, 0, true, true);
            return;
        }
        if (!mc.world.getBlockState(guardrail2.up(-1)).getFluidState().isEmpty()) {
            switchToItem(Items.NETHERRACK);
            BlockUtils.place(guardrail2.up(-1), InvUtils.findInHotbar(itemStack ->
                    itemStack.getItem() == Items.NETHERRACK), false, 0, true, true);
            return;
        }
        if (shouldPlace) {
            if (mc.world.isAir(targetPos2)) {
                switchToItem(Items.NETHERRACK);
                BlockUtils.place(targetPos2, InvUtils.findInHotbar(itemStack ->
                        itemStack.getItem() == Items.NETHERRACK), false, 0, true, true);
                return;
            }

            switchToItem(Items.BLUE_ICE);
            BlockUtils.place(targetPos, InvUtils.findInHotbar(itemStack ->
                    itemStack.getItem() == Items.BLUE_ICE), false, 0, true, true);
        }
    }
}
