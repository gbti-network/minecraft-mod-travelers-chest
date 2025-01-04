package com.gbti.travelerschest;

import com.gbti.travelerschest.utils.ChestObject;
import net.fabricmc.api.ModInitializer;


import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TravelersChest implements ModInitializer {
    public static final String MOD_ID = "travelers_chest";
    public static Map<Long, ChestObject> chests = new HashMap<>();
    public static Map<UUID, Integer> playersBreaks;
    private static final Logger LOGGER = LogManager.getLogger("TravelersChest");
    public static boolean debugLoggingEnabled = false;

    public static void logDebug(String message, Object... params) {
        if (debugLoggingEnabled) {
            LOGGER.info("[TC Debug] " + message, params);
        }
    }

    public static void logError(String message, Object... params) {
        LOGGER.error(message, params);
    }

    public static void checkAndRefillChest(World world, BlockPos blockPos, long posLong) {
        ChestObject chestObject = chests.get(posLong);
        if (chestObject != null) {
            long timeSinceLastRefresh = world.getTime() - chestObject.getLastRefreshed();
            long cooldownTicks = chestObject.getChestCooldown() * 20L;
            
            if (timeSinceLastRefresh >= cooldownTicks) {
                BlockState state = world.getBlockState(blockPos);
                if (state.getBlock() instanceof ChestBlock && world.getBlockEntity(blockPos) instanceof ChestBlockEntity be) {
                    // Get the proper inventory for single or double chest
                    ChestBlock chestBlock = (ChestBlock)state.getBlock();
                    DoubleBlockProperties.Type chestType = ChestBlock.getDoubleBlockType(state);
                    boolean isDoubleChest = chestType != DoubleBlockProperties.Type.SINGLE;
                    
                    // Get the proper inventory that handles both single and double chests
                    var inventory = ChestBlock.getInventory(chestBlock, state, world, blockPos, true);
                    if (inventory == null) {
                        logError("[TC Error] Failed to get inventory for chest at {}", blockPos);
                        return;
                    }
    
                    // Set chest size based on actual inventory size
                    int chestSize = inventory.size();
                    
                    logDebug("[TC Debug] Processing {} chest at {}. Size: {}", 
                        isDoubleChest ? "double" : "single", blockPos, chestSize);
    
                    List<Integer> positions = chestObject.getItemPositions();
                    List<NbtElement> items = chestObject.getItems();
                    boolean anyRefilled = false;
    
                    for (int i = 0; i < items.size(); i++) {
                        try {
                            // Get slot number, defaulting to sequential if position list is invalid
                            int slot = (i < positions.size()) ? positions.get(i) : i;
                            
                            // Skip invalid slots
                            if (slot >= chestSize) {
                                logError("[TC Error] Skipping invalid slot {} (chest size: {})", slot, chestSize);
                                continue;
                            }
    
                            ItemStack currentStack = inventory.getStack(slot);
                            NbtElement itemNbt = items.get(i);
                            
                            Optional<ItemStack> expectedStackOpt = ItemStack.fromNbt(world.getRegistryManager(), (NbtCompound)itemNbt);
                            if (expectedStackOpt.isPresent()) {
                                ItemStack expectedStack = expectedStackOpt.get();
                                
                                if (currentStack.isEmpty() || 
                                    !currentStack.getItem().equals(expectedStack.getItem()) ||
                                    currentStack.getCount() != expectedStack.getCount()) {
                                    
                                    inventory.setStack(slot, expectedStack.copy());
                                    anyRefilled = true;
                                    logDebug("[TC Debug] Refilled slot {} with {}", slot, expectedStack.getItem());
                                }
                            }
                        } catch (Exception e) {
                            logError("[TC Error] Failed to process item {}: {}", i, e.getMessage());
                        }
                    }
                    
                    chestObject.setLastRefreshed(world.getTime());
                    if (anyRefilled) {
                        logDebug("[TC Debug] Successfully refilled items in chest at {}", blockPos);
                    }
                }
            }
        }
    }

    @Override
    public void onInitialize() {
        // First read the config files to set up debug logging
        FileManager.readFiles();
        
        // Now we can start logging
        LOGGER.info("[TC] Debug logging status: {}", debugLoggingEnabled);
        if (debugLoggingEnabled) {
            logDebug("[TC] Initializing Travelers Chest Mod with debug logging enabled");
        }
        
        playersBreaks = new HashMap<>();

        // Handles TC items respawning
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(server.getOverworld().getTime() % 20 == 0) {  // Every second

                chests.forEach((pos, chestObject) -> {
                    World world = server.getWorld(chestObject.getWorldKey());

                    BlockPos blockPos = BlockPos.fromLong(pos);
                    if(world != null) {
                        checkAndRefillChest(world, blockPos, pos);
                    }
                });
            }

        });

        // Makes the TL and TC unbreakable by non-admin players
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            
                if(state.getBlock().equals(Blocks.CHEST) && blockEntity instanceof ChestBlockEntity && chests.containsKey(pos.asLong())) {
                    if(!player.hasPermissionLevel(4)) {
                        if(playersBreaks.getOrDefault(player.getUuid(), 1) >= 3) {
                            playersBreaks.put(player.getUuid(), 0);
                            player.sendMessage(Text.literal("This chest is protected by a mysterious force."));
                        } else playersBreaks.put(player.getUuid(), playersBreaks.getOrDefault(player.getUuid(), 0) + 1);
                        return false;
                    } else {
                        chests.remove(pos.asLong());
                        FileManager.saveChests();
                    }
                }

                return true;
            }
        );

        // Load all configs and stored data
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FileManager.loadChests();
            TravelersChest.chests.forEach((pos, chestObject) -> {
                RegistryKey<World> worldKey = server.getWorldRegistryKeys().stream().filter(key -> key.toString().equals(chestObject.getWorldKey().toString())).findFirst().orElse(null);
                if(worldKey != null) chestObject.setWorldKey(worldKey);
            });
        });

        // Register TC commands
        ChestObject.chestCommand();
        
        logDebug("[TC] Initialization complete. Debug logging is {}", debugLoggingEnabled ? "enabled" : "disabled");
    }

    public static BlockHitResult getBlockPlayerIsLooking(ServerPlayerEntity player) {
        double maxDistance = 20.0D; // range of the player

        Vec3d eyePosition = player.getCameraPosVec(1.0F);
        Vec3d lookDirection = player.getRotationVec(1.0F).multiply(maxDistance);
        Vec3d targetPosition = eyePosition.add(lookDirection);

        // send raycast to determine what block the player is looking
        return player.getWorld().raycast(new RaycastContext(eyePosition, targetPosition, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player
        ));
    }
}