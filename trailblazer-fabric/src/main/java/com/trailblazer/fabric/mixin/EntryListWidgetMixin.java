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

    // Cancel the built-in menu list background (both in-world and menu variants)
    @Inject(method = "drawMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void trailblazer$cancelMenuListBackground(DrawContext context, CallbackInfo ci) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Trailblazer mixin: cancel EntryListWidget.drawMenuListBackground");
        ci.cancel();
    }

    // Optional: some mappings use a separate method for in-world background
    @Inject(method = "drawInWorldMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void trailblazer$cancelInWorldBackground(DrawContext context, CallbackInfo ci) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Trailblazer mixin: cancel EntryListWidget.drawInWorldMenuListBackground");
        ci.cancel();
    }

    // Some versions tint via decorations pass; cancel that too to avoid overlays
    @Inject(method = "renderDecorations(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"), cancellable = true)
    private void trailblazer$cancelDecorations(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        // Do not cancel completely; the decorations include scrollbars and selection highlight.
        // If canceling disrupts UX, comment out this line. For debugging transparency, leave off.
        // ci.cancel();
    }

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void trailblazer$debugRenderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("Trailblazer mixin: EntryListWidget.renderWidget");
    }

    // Robustly remove the vanilla background: redirect the invocation inside renderWidget
    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/EntryListWidget;drawMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V"
        )
    )
    private void trailblazer$redirectDrawMenuListBackground(EntryListWidget instance, DrawContext context) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Trailblazer mixin: redirected EntryListWidget.drawMenuListBackground -> no-op");
        // no-op
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
    private void trailblazer$redirectDrawInWorldMenuListBackground(EntryListWidget instance, DrawContext context) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Trailblazer mixin: redirected EntryListWidget.drawInWorldMenuListBackground -> no-op");
        // no-op
    }
}
