/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AuctionHouseHud extends HudElement {
    public static final HudElementInfo<AuctionHouseHud> INFO = new HudElementInfo<>(
        new meteordevelopment.meteorclient.systems.hud.HudGroup("Misc"),
        "auction-house",
        "Displays DonutSMP auction house items with click-to-buy functionality.",
        AuctionHouseHud::new
    );

    public enum SortMode {
        HighestPrice("Highest Price"),
        LowestPrice("Lowest Price"),
        Alphabetical("Alphabetical");

        private final String title;

        SortMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum BackgroundMode {
        None,
        Light,
        Dark
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> refreshInterval = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-interval")
        .description("How often to refresh auction data in seconds.")
        .defaultValue(30)
        .min(5)
        .max(300)
        .sliderRange(5, 120)
        .build());

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("How to sort auction items.")
        .defaultValue(SortMode.LowestPrice)
        .build());

    private final Setting<Integer> maxItems = sgGeneral.add(new IntSetting.Builder()
        .name("max-items")
        .description("Maximum number of items to display.")
        .defaultValue(20)
        .min(1)
        .max(50)
        .sliderRange(1, 30)
        .build());

    private final Setting<Integer> gridColumns = sgGeneral.add(new IntSetting.Builder()
        .name("grid-columns")
        .description("Number of columns in the grid.")
        .defaultValue(4)
        .min(1)
        .max(10)
        .sliderRange(1, 8)
        .build());

    private final Setting<BackgroundMode> background = sgGeneral.add(new EnumSetting.Builder<BackgroundMode>()
        .name("background")
        .description("Background mode.")
        .defaultValue(BackgroundMode.Light)
        .build());

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the auction house display.")
        .defaultValue(1.0)
        .min(0.5)
        .max(3.0)
        .sliderRange(0.5, 2.0)
        .decimalPlaces(1)
        .build());

    private final List<AuctionItem> auctionItems = new ArrayList<>();
    private long lastRefresh = 0;
    private boolean isLoading = false;
    private String errorMessage = null;
    private AuctionItem hoveredItem = null;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public AuctionHouseHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        super.tick(renderer);
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefresh > refreshInterval.get() * 1000L && !isLoading) {
            fetchAuctionData();
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;

        double width = calculateWidth();
        double height = calculateHeight();
        setSize(width, height);

        updateMousePosition();

        double s = scale.get();
        renderer.post(() -> {
            if (errorMessage != null) {
                renderError(renderer, s);
                return;
            }

            if (isLoading) {
                renderLoading(renderer, s);
                return;
            }

            if (auctionItems.isEmpty()) {
                renderEmpty(renderer, s);
                return;
            }

            renderAuctionGrid(renderer, s);
        });
    }

    private void renderError(HudRenderer renderer, double scale) {
        String text = "Error: " + errorMessage;
        renderer.text(text, x, y, Color.RED, true, scale);
    }

    private void renderLoading(HudRenderer renderer, double scale) {
        String text = "Loading auction data...";
        renderer.text(text, x, y, Color.YELLOW, true, scale);
    }

    private void renderEmpty(HudRenderer renderer, double scale) {
        String text = "No auction items found";
        renderer.text(text, x, y, Color.GRAY, true, scale);
    }

    private void renderAuctionGrid(HudRenderer renderer, double scale) {
        int columns = gridColumns.get();
        int itemSize = (int) (16 * scale);
        int padding = (int) (2 * scale);
        int textHeight = (int) (9 * scale);
        
        Color bgColor = switch (background.get()) {
            case Light -> new Color(255, 255, 255, 64);
            case Dark -> new Color(0, 0, 0, 64);
            case None -> null;
        };

        int currentX = x;
        int currentY = y;
        int column = 0;
        hoveredItem = null;

        for (int i = 0; i < Math.min(auctionItems.size(), maxItems.get()); i++) {
            AuctionItem item = auctionItems.get(i);
            
            boolean isHovered = isMouseOverItem(currentX, currentY, itemSize, textHeight + padding);
            if (isHovered) {
                hoveredItem = item;
            }
            
            Color itemBgColor = bgColor;
            if (isHovered && bgColor != null) {
                itemBgColor = new Color(bgColor.r, bgColor.g, bgColor.b, Math.min(255, bgColor.a + 64));
            } else if (isHovered) {
                itemBgColor = new Color(255, 255, 255, 32);
            }
            
            if (itemBgColor != null) {
                renderer.quad(currentX - padding, currentY - padding, 
                    itemSize + padding * 2, itemSize + textHeight + padding * 2, itemBgColor);
            }

            renderer.item(item.itemStack, currentX, currentY, (float) scale, true);
            
            String priceText = item.price + "c";
            Color textColor = isHovered ? new Color(255, 255, 0) : Color.WHITE;
            renderer.text(priceText, currentX, currentY + itemSize + 2, textColor, true, scale * 0.7);

            column++;
            if (column >= columns) {
                column = 0;
                currentX = x;
                currentY += itemSize + textHeight + padding * 2;
            } else {
                currentX += itemSize + padding * 2;
            }
        }
    }

    private double calculateWidth() {
        if (auctionItems.isEmpty()) return 100 * scale.get();
        
        int columns = Math.min(gridColumns.get(), Math.min(auctionItems.size(), maxItems.get()));
        int itemSize = (int) (16 * scale.get());
        int padding = (int) (2 * scale.get());
        
        return columns * itemSize + (columns - 1) * padding * 2;
    }

    private double calculateHeight() {
        if (auctionItems.isEmpty()) return 20 * scale.get();
        
        int totalItems = Math.min(auctionItems.size(), maxItems.get());
        int columns = gridColumns.get();
        int rows = (int) Math.ceil((double) totalItems / columns);
        
        int itemSize = (int) (16 * scale.get());
        int textHeight = (int) (9 * scale.get());
        int padding = (int) (2 * scale.get());
        
        return rows * (itemSize + textHeight + padding * 2);
    }

    private void fetchAuctionData() {
        if (isLoading) return;
        
        isLoading = true;
        errorMessage = null;
        
        CompletableFuture.supplyAsync(() -> {
            try {
                String jsonBody = "{\"search\":\"\",\"sort\":\"" + getSortParameter() + "\"}";
                String response = Http.post("https://api.donutsmp.net/v1/auction/list/1")
                    .bodyJson(jsonBody)
                    .sendString();
                
                if (response == null || response.isEmpty()) {
                    return new ArrayList<AuctionItem>();
                }
                
                return parseAuctionResponse(response);
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return new ArrayList<AuctionItem>();
            }
        }).thenAccept(items -> {
            auctionItems.clear();
            auctionItems.addAll(items);
            sortAuctionItems();
            lastRefresh = System.currentTimeMillis();
            isLoading = false;
        });
    }

    private String getSortParameter() {
        return switch (sortMode.get()) {
            case HighestPrice -> "highest_price";
            case LowestPrice -> "lowest_price";
            case Alphabetical -> "alphabetical";
        };
    }

    private List<AuctionItem> parseAuctionResponse(String response) {
        List<AuctionItem> items = new ArrayList<>();
        
        try {
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
            JsonArray results = jsonResponse.getAsJsonArray("result");
            
            if (results != null) {
                for (JsonElement element : results) {
                    if (element.isJsonObject()) {
                        JsonObject itemObj = element.getAsJsonObject();
                        
                        String itemName = itemObj.has("item") ? itemObj.get("item").getAsString() : "Unknown";
                        double price = itemObj.has("price") ? itemObj.get("price").getAsDouble() : 0.0;
                        String seller = itemObj.has("seller") ? itemObj.get("seller").getAsString() : 
                                       itemObj.has("owner") ? itemObj.get("owner").getAsString() : "Unknown";
                        
                        ItemStack itemStack = createItemStack(itemName);
                        items.add(new AuctionItem(itemName, price, seller, itemStack));
                    }
                }
            }
        } catch (Exception e) {
            errorMessage = "Failed to parse auction data: " + e.getMessage();
        }
        
        return items;
    }

    private ItemStack createItemStack(String itemName) {
        try {
            String cleanName = itemName.toLowerCase().replace(" ", "_").replace("'", "");
            Identifier itemId = Identifier.of("minecraft", cleanName);
            Item item = Registries.ITEM.get(itemId);
            
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        
        return new ItemStack(Items.BARRIER);
    }

    private void sortAuctionItems() {
        switch (sortMode.get()) {
            case HighestPrice -> auctionItems.sort((a, b) -> Double.compare(b.price, a.price));
            case LowestPrice -> auctionItems.sort((a, b) -> Double.compare(a.price, b.price));
            case Alphabetical -> auctionItems.sort(Comparator.comparing(a -> a.itemName));
        }
    }

    private void updateMousePosition() {
        if (mc.mouse != null && mc.getWindow() != null) {
            double mouseX = mc.mouse.getX();
            double mouseY = mc.mouse.getY();
            double scaleFactor = mc.getWindow().getScaleFactor();
            
            lastMouseX = (int) (mouseX * scaleFactor);
            lastMouseY = (int) (mouseY * scaleFactor);
        }
    }
    
    private boolean isMouseOverItem(int itemX, int itemY, int itemWidth, int itemHeight) {
        if (lastMouseX == -1 || lastMouseY == -1) return false;
        
        return lastMouseX >= itemX && lastMouseX <= itemX + itemWidth &&
               lastMouseY >= itemY && lastMouseY <= itemY + itemHeight;
    }
    
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!isInEditor() && button == 0 && hoveredItem != null) {
            onItemClicked(hoveredItem);
            return true;
        }
        return false;
    }

    public void onItemClicked(AuctionItem item) {
        if (mc.player != null && item.seller != null && !item.seller.equals("Unknown")) {
            ChatUtils.sendPlayerMsg("/ah " + item.seller);
        }
    }

    public static class AuctionItem {
        public final String itemName;
        public final double price;
        public final String seller;
        public final ItemStack itemStack;

        public AuctionItem(String itemName, double price, String seller, ItemStack itemStack) {
            this.itemName = itemName;
            this.price = price;
            this.seller = seller;
            this.itemStack = itemStack;
        }
    }
}
