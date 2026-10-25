package dev.infernity.rollplayer.listeners.commands;

import dev.infernity.rollplayer.Resources;
import dev.infernity.rollplayer.listeners.templates.SimpleCommandListener;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class Info extends SimpleCommandListener {
    public Info() {
        super("info", "Get some basic info about the bot.", Resources.getInstance().getConfig().getString("emoji.icon", "\uD83D\uDCD6"));
    }

    public void onCommandRan(@NotNull SlashCommandInteractionEvent event) {
        event.replyComponents(createContainer(
                TextDisplay.ofFormat("### %s, %s",
                        Resources.getInstance().getName(),
                        Resources.getInstance().getLabel()),
                TextDisplay.ofFormat("version %s, built at %s", Resources.getInstance().getVersion(), Resources.getInstance().getTimestamp()),
                TextDisplay.ofFormat("Java %d", Runtime.version().feature()),
                TextDisplay.ofFormat("JDA %s", JDAInfo.VERSION)
        )).useComponentsV2().queue();
    }
}
