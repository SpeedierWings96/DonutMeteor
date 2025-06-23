/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.donut;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoAH extends Module {
    public enum PriceMode {
        Highest("Highest"),
        Lowest("Lowest");
        
        private final String title;
        
        PriceMode(String title) {
            this.title = title;
        }
        
        @Override
        public String toString() {
            return title;
        }
    }
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Boolean> autoSell = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sell")
        .description("Automatically sell item instead of opening auction house.")
        .defaultValue(false)
        .build());
    
    private final Setting<PriceMode> priceMode = sgGeneral.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode")
        .description("Which price to use as reference for selling.")
        .defaultValue(PriceMode.Highest)
        .build());
    
    private final Setting<Double> priceMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("price-multiplier")
        .description("Multiplier for the reference auction price.")
        .defaultValue(1.0)
        .min(0.5)
        .max(10.0)
        .sliderRange(0.5, 5.0)
        .decimalPlaces(2)
        .build());

    public AutoAH() {
        super(Categories.Donut, "auto-ah", "Automatically opens auction house for the held item or sells it at market price.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        ItemStack heldItem = mc.player.getMainHandStack();
        if (heldItem.isEmpty()) {
            info("No item in main hand.");
            toggle();
            return;
        }

        String itemName = Names.get(heldItem);
        
        if (autoSell.get()) {
            double referencePrice = getItemPrice(itemName, priceMode.get());
            if (referencePrice > 0) {
                double sellPrice;
                if (priceMode.get() == PriceMode.Highest) {
                    sellPrice = referencePrice + (referencePrice * priceMultiplier.get());
                } else {
                    sellPrice = referencePrice - (referencePrice * priceMultiplier.get());
                }
                ChatUtils.sendPlayerMsg("/ah sell " + Math.round(sellPrice));
                info("Selling %s for %d coins (%s: %d, multiplier: %.2fx)", itemName, Math.round(sellPrice), priceMode.get().toString().toLowerCase(), Math.round(referencePrice), priceMultiplier.get());
            } else {
                error("Could not fetch auction prices for: " + itemName);
            }
        } else {
            String formattedName = itemName.replace(" ", "_");
            ChatUtils.sendPlayerMsg("/ah " + formattedName);
            info("Opening auction house for: " + itemName);
        }
        
        toggle();
    }
    
    private double getItemPrice(String itemName, PriceMode mode) {
        try {
            String sortMode = mode == PriceMode.Highest ? "highest_price" : "lowest_price";
            String jsonBody = "{\"search\":\"" + itemName + "\",\"sort\":\"" + sortMode + "\"}";
            String response = Http.post("https://api.donutsmp.net/v1/auction/list/1")
                .header("Authorization", "Bearer 9965f7bb27dc4c4c9f748639b733f0bf")
                .bodyJson(jsonBody)
                .sendString();
            
            if (response == null || response.isEmpty()) {
                return 0.0;
            }
            
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
            JsonArray results = jsonResponse.getAsJsonArray("result");
            
            if (results != null && results.size() > 0) {
                JsonElement firstResult = results.get(0);
                if (firstResult.isJsonObject()) {
                    JsonObject firstItem = firstResult.getAsJsonObject();
                    if (firstItem.has("price")) {
                        return firstItem.get("price").getAsDouble();
                    }
                }
            }
            
            return 0.0;
        } catch (Exception e) {
            error("Failed to fetch auction prices: " + e.getMessage());
            return 0.0;
        }
    }
}
