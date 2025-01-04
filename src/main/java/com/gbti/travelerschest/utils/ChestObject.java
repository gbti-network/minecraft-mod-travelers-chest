package com.gbti.travelerschest.utils;

import com.gbti.travelerschest.FileManager;
import com.gbti.travelerschest.TravelersChest;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static com.gbti.travelerschest.TravelersChest.chests;
import static com.gbti.travelerschest.TravelersChest.getBlockPlayerIsLooking;

public class ChestObject {

    private long lastRefreshed;
    private int chestCooldown;
    private RegistryKey<World> worldKey;
    
    private List<NbtElement> items;
    private List<Integer> itemPositions;

    public ChestObject(long lastRefreshed, int chestCooldown, RegistryKey<World> worldKey, List<NbtElement> items) {
        this(lastRefreshed, chestCooldown, worldKey, items, new ArrayList<>());
    }

    public ChestObject(long lastRefreshed, int chestCooldown, RegistryKey<World> worldKey, List<NbtElement> items, List<Integer> itemPositions) {
        this.lastRefreshed = lastRefreshed;
        this.chestCooldown = chestCooldown;
        this.worldKey = worldKey;
        this.items = items;
        this.itemPositions = itemPositions;
    }

    public long getLastRefreshed() {
        return lastRefreshed;
    }

    public void setLastRefreshed(long lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
    }

    public int getChestCooldown() {
        return chestCooldown;
    }

    public void setChestCooldown(int chestCooldown) {
        this.chestCooldown = chestCooldown;
    }

    public RegistryKey<World> getWorldKey() {
        return worldKey;
    }

    public void setWorldKey(RegistryKey<World> worldKey) {
        this.worldKey = worldKey;
    }

    public List<NbtElement> getItems() {
        return items;
    }

    public void setItems(List<NbtElement> items) {
        this.items = items;
    }

    public List<Integer> getItemPositions() {
        List<Integer> positions = itemPositions != null ? itemPositions : new ArrayList<>();
        TravelersChest.logDebug("[TC] Getting item positions: {}", positions);
        return positions;
    }

    public void setItemPositions(List<Integer> itemPositions) {
        TravelersChest.logDebug("[TC] Setting item positions: {}", itemPositions);
        this.itemPositions = itemPositions;
    }

    public static void chestCommand() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("travelers_chest").requires(source -> {
            if(source instanceof ServerCommandSource serverSource) return serverSource.hasPermissionLevel(4);
            else return false;
        }).then(CommandManager.literal("create").then(CommandManager.argument("time", IntegerArgumentType.integer(0)).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(createTravelersChest(player, IntegerArgumentType.getInteger(ctx, "time"))) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully created travelers chest"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a chest and is not empty"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(createTravelersChest(player, 1800)) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully created travelers chest"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a chest and is not empty"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })).then(CommandManager.literal("edit").then(CommandManager.argument("time", IntegerArgumentType.integer(0)).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(editTravelersChest(player, IntegerArgumentType.getInteger(ctx, "time"))) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully edited travelers chest"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a travelers chest"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        }))).then(CommandManager.literal("destroy").executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(destroyTravelersChest(player)) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully destroyed travelers chest"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a travelers chest"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })))));
    }

    public static boolean createTravelersChest(ServerPlayerEntity player, int time) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);
    
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());
    
            if (block.getBlock() instanceof ChestBlock) {
                // Get the proper inventory that handles both single and double chests
                Inventory inv = ChestBlock.getInventory((ChestBlock) block.getBlock(), block, player.getWorld(), hitResult.getBlockPos(), true);
                if (inv != null && !inv.isEmpty()) {
                    // For double chests, always use the primary (northern/western) chest position
                    BlockPos mainChestPos = hitResult.getBlockPos();
                    if (ChestBlock.getDoubleBlockType(block) != DoubleBlockProperties.Type.SINGLE) {
                        Direction directionToOther = ChestBlock.getFacing(block);
                        BlockPos otherPos = hitResult.getBlockPos().offset(directionToOther);
                        // Always use the smaller position (x, then z) as the main chest
                        mainChestPos = (hitResult.getBlockPos().getX() < otherPos.getX() || 
                                     (hitResult.getBlockPos().getX() == otherPos.getX() && 
                                      hitResult.getBlockPos().getZ() < otherPos.getZ())) 
                                     ? hitResult.getBlockPos() : otherPos;
                    }
    
                    // Store items and positions
                    List<NbtElement> items = new ArrayList<>();
                    List<Integer> positions = new ArrayList<>();
                    for (int i = 0; i < inv.size(); i++) {
                        if (!inv.getStack(i).isEmpty()) {
                            items.add(inv.getStack(i).encode(player.getWorld().getRegistryManager()));
                            positions.add(i);
                        }
                    }
    
                    // Create single chest object for the entire inventory
                    ChestObject chest = new ChestObject(
                        player.getWorld().getTime(),
                        time,
                        player.getWorld().getRegistryKey(),
                        items,
                        positions
                    );
    
                    // Store only one entry using the main chest position
                    chests.put(mainChestPos.asLong(), chest);
                    TravelersChest.logDebug("[TC] Created {} chest at: {}", 
                        ChestBlock.getDoubleBlockType(block) != DoubleBlockProperties.Type.SINGLE ? "double" : "single", 
                        mainChestPos);
    
                    FileManager.saveChests();
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean editTravelersChest(ServerPlayerEntity player, int time) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);
    
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());
    
            if (block.getBlock() instanceof ChestBlock) {
                // Get the main chest position for double chests
                BlockPos mainChestPos = hitResult.getBlockPos();
                if (ChestBlock.getDoubleBlockType(block) != DoubleBlockProperties.Type.SINGLE) {
                    Direction directionToOther = ChestBlock.getFacing(block);
                    BlockPos otherPos = hitResult.getBlockPos().offset(directionToOther);
                    mainChestPos = (hitResult.getBlockPos().getX() < otherPos.getX() || 
                                 (hitResult.getBlockPos().getX() == otherPos.getX() && 
                                  hitResult.getBlockPos().getZ() < otherPos.getZ())) 
                                 ? hitResult.getBlockPos() : otherPos;
                }
    
                // Check if the chest exists at the main position
                if (chests.containsKey(mainChestPos.asLong())) {
                    ChestObject chest = chests.get(mainChestPos.asLong());
                    chest.setChestCooldown(time);
                    
                    // Update items and positions from current inventory state
                    Inventory inv = ChestBlock.getInventory((ChestBlock) block.getBlock(), block, player.getWorld(), hitResult.getBlockPos(), true);
                    if (!inv.isEmpty()) {
                        List<NbtElement> items = new ArrayList<>();
                        List<Integer> positions = new ArrayList<>();
                        for (int i = 0; i < inv.size(); i++) {
                            if (!inv.getStack(i).isEmpty()) {
                                items.add(inv.getStack(i).encode(player.getWorld().getRegistryManager()));
                                positions.add(i);
                            }
                        }
                        chest.setItems(items);
                        chest.setItemPositions(positions);
                    }
                    
                    FileManager.saveChests();
                    return true;
                }
            }
        }
        return false;
    }
    
    public static boolean destroyTravelersChest(ServerPlayerEntity player) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);
    
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());
    
            if (block.getBlock() instanceof ChestBlock) {
                // Get the main chest position for double chests
                BlockPos mainChestPos = hitResult.getBlockPos();
                if (ChestBlock.getDoubleBlockType(block) != DoubleBlockProperties.Type.SINGLE) {
                    Direction directionToOther = ChestBlock.getFacing(block);
                    BlockPos otherPos = hitResult.getBlockPos().offset(directionToOther);
                    mainChestPos = (hitResult.getBlockPos().getX() < otherPos.getX() || 
                                 (hitResult.getBlockPos().getX() == otherPos.getX() && 
                                  hitResult.getBlockPos().getZ() < otherPos.getZ())) 
                                 ? hitResult.getBlockPos() : otherPos;
                }
    
                // Remove the chest using the main position
                if (chests.remove(mainChestPos.asLong()) != null) {
                    FileManager.saveChests();
                    return true;
                }
            }
        }
        return false;
    }

}
