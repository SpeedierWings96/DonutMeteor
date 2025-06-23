/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.elements.AuctionHouseHud;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HudEditorScreen.class)
public class HudEditorScreenMixin {
    
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        HudEditorScreen screen = (HudEditorScreen) (Object) this;
        
        for (HudElement element : meteordevelopment.meteorclient.systems.hud.Hud.get()) {
            if (element instanceof AuctionHouseHud auctionHud && element.isActive()) {
                if (auctionHud.handleMouseClick(mouseX, mouseY, button)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
