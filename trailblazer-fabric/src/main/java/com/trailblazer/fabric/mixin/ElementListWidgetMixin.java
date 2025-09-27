package com.trailblazer.fabric.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ElementListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElementListWidget.class)
public abstract class ElementListWidgetMixin {

    @Inject(method = "drawMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void trailblazer$cancelMenuListBackground(DrawContext context, CallbackInfo ci) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Trailblazer mixin: cancel ElementListWidget.drawMenuListBackground");
        ci.cancel();
    }
}
