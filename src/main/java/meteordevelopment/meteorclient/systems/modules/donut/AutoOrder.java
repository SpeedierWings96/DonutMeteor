/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.donut;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoOrder extends Module {
    public AutoOrder() {
        super(Categories.Donut, "auto-order", "Automatically opens order interface for the held item.");
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
        String formattedName = itemName.replace(" ", "_");
        
        ChatUtils.sendPlayerMsg("/order " + formattedName);
        info("Opening order interface for: " + itemName);
        
        toggle();
    }
}
