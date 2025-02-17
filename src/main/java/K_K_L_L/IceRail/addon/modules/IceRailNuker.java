/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Edited by K-K-L-L (Discord:theorangedot).
 */

 package K_K_L_L.IceRail.addon.modules;

 import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.renderer.ShapeMode;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.meteorclient.utils.player.Rotations;
 import meteordevelopment.meteorclient.utils.render.RenderUtils;
 import meteordevelopment.meteorclient.utils.render.color.SettingColor;
 import meteordevelopment.meteorclient.utils.world.BlockIterator;
 import meteordevelopment.meteorclient.utils.world.BlockUtils;
 import meteordevelopment.orbit.EventHandler;
 import meteordevelopment.orbit.EventPriority;
 import net.minecraft.block.Block;
 import net.minecraft.block.Blocks;
 import net.minecraft.client.MinecraftClient;
 import net.minecraft.item.Items;
 import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
 import net.minecraft.util.Hand;
 import net.minecraft.util.math.BlockPos;
 import K_K_L_L.IceRail.addon.IceRail;
 
 import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.List;
 
 import static K_K_L_L.IceRail.addon.Utils.switchToBestTool;
 import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
 import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.playerZ;
 import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;
 
 public class IceRailNuker extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
     private final SettingGroup sgRender = settings.createGroup("Render");
     private final MinecraftClient mc = MinecraftClient.getInstance();
     static boolean isBreaking;
 
     private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
         .name("delay")
         .description("Delay in ticks between breaking blocks.")
         .defaultValue(0)
         .build()
     );
 
     private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder()
         .name("max-blocks-per-tick")
         .description("Maximum blocks to try to break per tick. Useful when insta mining.")
         .defaultValue(3)
         .min(1)
         .sliderRange(1, 6)
         .build()
     );
 
     private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
         .name("swing-hand")
         .description("Swing hand client side.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
         .name("packet-mine")
         .description("Attempt to instamine everything at once.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
         .name("rotate")
         .description("Rotates server-side to the block being mined.")
         .defaultValue(false)
         .build()
     );
 
     private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
         .name("list-mode")
         .description("Selection mode.")
         .defaultValue(ListMode.Blacklist)
         .build()
     );
 
     private final Setting<List<Block>> blacklist = sgWhitelist.add(new BlockListSetting.Builder()
         .name("blacklist")
         .description("The blocks you don't want to mine.")
         .defaultValue(Blocks.BLUE_ICE)
         .visible(() -> listMode.get() == ListMode.Blacklist)
         .build()
     );
 
     private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
         .name("whitelist")
         .description("The blocks you want to mine.")
         .visible(() -> listMode.get() == ListMode.Whitelist)
         .build()
     );
 
     private final Setting<Boolean> enableRenderBreaking = sgRender.add(new BoolSetting.Builder()
         .name("broken-blocks")
         .description("Enable rendering broken blocks.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<ShapeMode> shapeModeBreak = sgRender.add(new EnumSetting.Builder<ShapeMode>()
         .name("nuke-block-mode")
         .description("How the shapes for broken blocks are rendered.")
         .defaultValue(ShapeMode.Both)
         .visible(enableRenderBreaking::get)
         .build()
     );
 
     private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
         .name("side-color")
         .description("The side color of the target block rendering.")
         .defaultValue(new SettingColor(255, 0, 0, 80))
         .visible(enableRenderBreaking::get)
         .build()
     );
 
     private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
         .name("line-color")
         .description("The line color of the target block rendering.")
         .defaultValue(new SettingColor(255, 0, 0, 255))
         .visible(enableRenderBreaking::get)
         .build()
     );
 
     private final List<BlockPos> blocks = new ArrayList<>();
     private boolean firstBlock;
     private final BlockPos.Mutable lastBlockPos = new BlockPos.Mutable();
     private int timer;
     private int noBlockTimer;
 
     public static boolean getIsBreaking() {
         return isBreaking;
     }
 
     public static void setIsBreaking(boolean value) {
         isBreaking = value;
     }
 
 
     public IceRailNuker() {
         super(IceRail.CATEGORY, "ice-rail-nuker", "A helper module that cleans the highway.");
     }
 
     private BlockPos getRegion1Start() {
         if (mc.player == null) return null;
 
         return switch (getPlayerDirection()) {
             case NORTH -> new BlockPos(playerX + 1, playerY, mc.player.getBlockZ() + 2 - Math.abs(mc.player.getBlockZ()) % 2);
             case SOUTH -> new BlockPos(playerX + 1, playerY, mc.player.getBlockZ() - 2 + Math.abs(mc.player.getBlockZ()) % 2);
             case EAST -> new BlockPos(mc.player.getBlockX() + 2 - Math.abs(mc.player.getBlockX()) % 2, playerY, playerZ - 1);
             case WEST -> new BlockPos(mc.player.getBlockX() - 2 + Math.abs(mc.player.getBlockX()) % 2, playerY, playerZ - 1);
             default -> new BlockPos(0, 64, 0); // This shouldn't happen
         };
     }
 
     private BlockPos getRegion1End() {
         if (mc.player == null) return null;
 
         return switch (getPlayerDirection()) {
             case NORTH -> new BlockPos(playerX, playerY + 4, mc.player.getBlockZ() - 4);
             case SOUTH -> new BlockPos(playerX, playerY + 4, mc.player.getBlockZ() + 4);
             case EAST -> new BlockPos(mc.player.getBlockX() - 4, playerY + 4, playerZ);
             case WEST -> new BlockPos(mc.player.getBlockX() + 4, playerY + 4, playerZ);
             default -> new BlockPos(0, 64, 0); // This shouldn't happen
         };
     }
 
     private boolean isInRegion(BlockPos pos, BlockPos regionStart, BlockPos regionEnd) {
         if (regionStart == null || regionEnd == null) return false;
         int minX = Math.min(regionStart.getX(), regionEnd.getX());
         int maxX = Math.max(regionStart.getX(), regionEnd.getX());
         int minY = Math.min(regionStart.getY(), regionEnd.getY());
         int maxY = Math.max(regionStart.getY(), regionEnd.getY());
         int minZ = Math.min(regionStart.getZ(), regionEnd.getZ());
         int maxZ = Math.max(regionStart.getZ(), regionEnd.getZ());
 
         return pos.getX() >= minX && pos.getX() <= maxX &&
             pos.getY() >= minY && pos.getY() <= maxY &&
             pos.getZ() >= minZ && pos.getZ() <= maxZ;
     }
 
     private boolean isInAnyRegion(BlockPos pos) {
         return isInRegion(pos, getRegion1Start(), getRegion1End());
     }
 
     @Override
     public void onActivate() {
         firstBlock = true;
         timer = 0;
         noBlockTimer = 0;
     }
 
     @EventHandler
     private void onTickPre(TickEvent.Pre event) {
         if (playerX == null || playerY == null || playerZ == null) return;
 
         if (isGoingToHighway || getIsEating()) return;
 
         if (timer > 0) {
             timer--;
             return;
         }
 
         if (mc.player == null) return;
 
         BlockPos pos1 = getRegion1Start();
         BlockPos pos2 = getRegion1End();
 
         if (pos1 == null || pos2 == null) return;
 
         int maxWidth = Math.abs(pos2.getX() - pos1.getX()) + 1;
         int maxHeight = Math.abs(pos2.getY() - pos1.getY()) + 1;
 
         BlockIterator.register(maxWidth, maxHeight, (blockPos, blockState) -> {
             if (!isInAnyRegion(blockPos)) return;
             if (!BlockUtils.canBreak(blockPos, blockState)) return;
 
             if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(blockState.getBlock())) return;
             if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(blockState.getBlock())) return;
 
             blocks.add(blockPos.toImmutable());
         });
 
         BlockIterator.after(() -> {
             blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
             processBlocks();
         });
     }
 
     private void processBlocks() {
         if (blocks.isEmpty()) {
             if (noBlockTimer++ >= delay.get()) {
                 firstBlock = true;
                 setIsBreaking(false);
             }
             return;
         } else {
             noBlockTimer = 0;
         }
 
         if (!firstBlock && !lastBlockPos.equals(blocks.getFirst())) {
             timer = delay.get();
             firstBlock = false;
             lastBlockPos.set(blocks.getFirst());
             if (timer > 0) return;
         }
 
         int count = 0;
         for (BlockPos block : blocks) {
             if (count >= maxBlocksPerTick.get() && block.getY() < 115) break;
             if (count >= 4 && block.getY() > 115) break;
 
             boolean canInstaMine = BlockUtils.canInstaBreak(block);
 
             if (rotate.get()) {
                 Rotations.rotate(Rotations.getYaw(block), Rotations.getPitch(block), () -> breakBlock(block));
             } else {
                 breakBlock(block);
             }
 
             if (enableRenderBreaking.get()) {
                 RenderUtils.renderTickingBlock(block, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
             }
 
             lastBlockPos.set(block);
             count++;
             if (!canInstaMine && !packetMine.get()) break;
         }
 
         firstBlock = false;
         blocks.clear();
     }
 
     private void breakBlock(BlockPos blockPos) {
         if (blockPos == null || mc.world.getBlockState(blockPos).getBlock() == Blocks.AIR) return;
         switchToBestTool(blockPos);
         setIsBreaking(true);
 
         if (packetMine.get()) {
             mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
             if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
             mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
         } else {
             BlockUtils.breakBlock(blockPos, swingHand.get());
         }
     }
 
     @EventHandler(priority = EventPriority.HIGHEST)
     private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
         event.cooldown = 0;
     }
 
     public enum ListMode {
         Whitelist,
         Blacklist
     }
 }
 