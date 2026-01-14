package com.trailblazer.fabric.mixin;

/**
 * Legacy placeholder.
 *
 * Older versions of Trailblazer used a global mixin into {@code EntryListWidget} to suppress
 * certain background/decorations calls. Minecraft 1.21.11 removed some of the targeted methods
 * (e.g. {@code renderDecorations}), so keeping those injections would cause remap warnings and can
 * fail at runtime.
 *
 * The current UI widgets (e.g. {@code PathListWidget}) handle their own rendering directly, so we
 * intentionally apply no EntryListWidget mixins.
 */
@Deprecated(forRemoval = true)
public final class EntryListWidgetMixin {
    private EntryListWidgetMixin() {
    }
}
