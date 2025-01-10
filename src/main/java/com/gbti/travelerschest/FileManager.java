package com.gbti.travelerschest;

import com.gbti.travelerschest.utils.ChestObject;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;

import java.util.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileManager {

    private static final Logger LOGGER = LogManager.getLogger("TravelersChest");

    private static final GsonBuilder gsonBuilder = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NbtElement.class,
                    (JsonSerializer<NbtElement>) (nbtElement, typeOfSrc, context) -> new JsonPrimitive(nbtElement.asString()))
            .registerTypeAdapter(ChestObject.class,
                    (JsonSerializer<ChestObject>) (chest, type, context) -> {
                        JsonObject json = new JsonObject();
                        json.addProperty("lastRefreshed", chest.getLastRefreshed());
                        json.addProperty("chestCooldown", chest.getChestCooldown());
                        json.add("worldKey", context.serialize(chest.getWorldKey()));
                        
                        // Convert NBT items to a simpler format for storage
                        JsonArray itemsArray = new JsonArray();
                        for (NbtElement item : chest.getItems()) {
                            JsonObject itemObj = new JsonObject();
                            itemObj.addProperty("nbt", item.toString());
                            itemsArray.add(itemObj);
                        }
                        json.add("items", itemsArray);
                        
                        // Always include itemPositions, even if empty
                        JsonArray positionsArray = new JsonArray();
                        List<Integer> positions = chest.getItemPositions();
                        if (positions != null) {
                            for (Integer pos : positions) {
                                positionsArray.add(pos);
                            }
                        }
                        json.add("itemPositions", positionsArray);
                        
                        return json;
                    })
            .registerTypeAdapter(ChestObject.class,
                    (JsonDeserializer<ChestObject>) (json, typeOfT, context) -> {
                        JsonObject jsonObject = json.getAsJsonObject();
                        long lastRefreshed = jsonObject.get("lastRefreshed").getAsLong();
                        int chestCooldown = jsonObject.get("chestCooldown").getAsInt();
                        RegistryKey<World> worldKey = context.deserialize(jsonObject.get("worldKey"), new TypeToken<RegistryKey<World>>(){}.getType());
                        
                        // Parse chest items
                        JsonArray itemsArray = jsonObject.getAsJsonArray("items");
                        List<NbtElement> items = new ArrayList<>();
                        for (JsonElement element : itemsArray) {
                            try {
                                JsonObject itemObj = element.getAsJsonObject();
                                if (itemObj.has("nbt")) {
                                    // Direct NBT format
                                    String nbtString = itemObj.get("nbt").getAsString();
                                    TravelersChest.logDebug("[TC Debug] Loading item NBT: {}", nbtString);
                                    NbtElement nbt = net.minecraft.nbt.StringNbtReader.parse(nbtString);
                                    items.add(nbt);
                                } else if (itemObj.has("field_11515")) {
                                    // Legacy format
                                    JsonObject field11515 = itemObj.getAsJsonObject("field_11515");
                                    String id = field11515.get("id").getAsString();
                                    String count = field11515.get("count").getAsString();
                                    
                                    String nbtString = String.format("{id:\"%s\",Count:%sb}", id, count);
                                    TravelersChest.logDebug("[TC Debug] Loading legacy item NBT: {}", nbtString);
                                    NbtElement nbt = net.minecraft.nbt.StringNbtReader.parse(nbtString);
                                    items.add(nbt);
                                }
                            } catch (Exception e) {
                                TravelersChest.logDebug("[TC Error] Failed to parse chest item NBT: {}", e.getMessage());
                                throw new JsonParseException("Error parsing chest item NBT", e);
                            }
                        }
                        
                        List<Integer> positions = new ArrayList<>();
                        if (jsonObject.has("itemPositions")) {
                            JsonArray posArray = jsonObject.getAsJsonArray("itemPositions");
                            for (JsonElement element : posArray) {
                                positions.add(element.getAsInt());
                            }
                        }
                        
                        return new ChestObject(lastRefreshed, chestCooldown, worldKey, items, positions);
                    })
            .enableComplexMapKeySerialization()
            .serializeNulls();


    public static int chestPlayerCooldown = 86400 * 7;
    public static int chestPlayerDelay = 600;

    private static final String CONFIG_DIR = "config/travelers-chest";

    public static void readFiles() {

        try {
            File directory = new File(CONFIG_DIR);
            directory.mkdir();

            // Config file
            File configFile = new File("config/travelers-chest/travelers_chest_config.txt");
            if(configFile.createNewFile()) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
                writer.write("debug_logging=false");
                writer.close();
            } else {
                Scanner reader = new Scanner(configFile);
                while (reader.hasNextLine()) {
                    String line = reader.nextLine();

                    if(line.startsWith("debug_logging=")) {
                        // Get everything after the equals sign
                        String debugValue = line.substring(line.indexOf('=') + 1).trim();
                    
                        // Use direct logging for debug setting since logDebug isn't ready yet
                        TravelersChest.debugLoggingEnabled = debugValue.equals("true");
                        LOGGER.info("[TC] Set debugLoggingEnabled to: {}", TravelersChest.debugLoggingEnabled);
                    }
                }
                reader.close();
            }

        } catch (Exception e) {
            TravelersChest.logDebug("[TC] An error occurred: {}", e.getMessage());
            e.printStackTrace();
        }
    }


    public static void saveChests() {
        File file = new File("config/travelers-chest/travelers_chests.json");
        try {
            file.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            String json = gsonBuilder.create().toJson(TravelersChest.chests);
            TravelersChest.logDebug("[TC] Saving chests JSON: {}", json);
            writer.write(json);
            writer.close();
        } catch(Exception e) {
            TravelersChest.logDebug("[TC] Error saving chests: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadChests() {
        try {
            File file = new File("config/travelers-chest/travelers_chests.json");
            if(!file.createNewFile()) {
                Scanner reader = new Scanner(file);
                StringBuilder jsonStr = new StringBuilder();
                while (reader.hasNextLine()) {
                    jsonStr.append(reader.nextLine());
                }
                reader.close();
                
                String json = jsonStr.toString().trim();
                TravelersChest.logDebug("[TC] Loading chests from JSON: {}", json);
                
                if (!json.isEmpty()) {
                    Map<Long, ChestObject> loadedChests = gsonBuilder.create().fromJson(
                        json,
                        new TypeToken<Map<Long, ChestObject>>(){}.getType()
                    );
                    if (loadedChests != null) {
                        TravelersChest.logDebug("[TC Debug] Successfully loaded {} chests", loadedChests.size());
                        for (Map.Entry<Long, ChestObject> entry : loadedChests.entrySet()) {
                            TravelersChest.logDebug("[TC Debug] Chest {} has {} items and {} positions", 
                                entry.getKey(),
                                entry.getValue().getItems().size(),
                                entry.getValue().getItemPositions().size());
                        }
                        TravelersChest.chests = loadedChests;
                    } else {
                        TravelersChest.logDebug("[TC Debug] No chests loaded from JSON, creating new map");
                        TravelersChest.chests = new HashMap<>();
                    }
                } else {
                    TravelersChest.logDebug("[TC Debug] Empty JSON file, creating new map");
                    TravelersChest.chests = new HashMap<>();
                }
            } else {
                TravelersChest.logDebug("[TC Debug] No chests file exists, creating new map");
                TravelersChest.chests = new HashMap<>();
            }
        } catch(Exception e) {
            TravelersChest.logDebug("[TC] Error loading chests: {}", e.getMessage());
            e.printStackTrace();
            TravelersChest.chests = new HashMap<>();
        }
    }
}
