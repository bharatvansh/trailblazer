package com.trailblazer.plugin.commands;

/**
 * Transitional unified command for the server side. For now it simply extends the existing
 * {@link PathCommand} implementation so that all logic lives in one place. This lets the
 * server expose `/trailblazer` (preferred) while keeping `/path` as a backwards-compatible
 * alias until the old name is fully removed.
 */
public class TrailblazerCommand extends PathCommand {
    public TrailblazerCommand(com.trailblazer.plugin.TrailblazerPlugin plugin) {
        super(plugin);
    }
}
