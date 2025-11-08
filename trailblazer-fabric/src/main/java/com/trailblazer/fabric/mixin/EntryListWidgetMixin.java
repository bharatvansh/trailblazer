package com.trailblazer.fabric.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {

    @Inject(method = "renderDecorations(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"), cancellable = true)
    private void trailblazer$cancelDecorations(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
    }

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void trailblazer$debugRenderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Mixin injection point - no debug logging needed in production
    }

    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/EntryListWidget;drawMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V"
        )
    )
    private void trailblazer$redirectDrawMenuListBackground(EntryListWidget<?> instance, DrawContext context) {
    }

    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/EntryListWidget;drawInWorldMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V",
            remap = true
        ),
        require = 0
    )
    private void trailblazer$redirectDrawInWorldMenuListBackground(EntryListWidget<?> instance, DrawContext context) {
    }
}
