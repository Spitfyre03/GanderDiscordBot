package com.github.gander.interactions.slash;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;
import java.net.URL;

public class SlashNh implements ISlashInteraction {

    private final Logger LOGGER = LoggerFactory.getLogger(SlashNh.class);

    @SlashCommand
    public static final SlashNh nh = new SlashNh();

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("nh", "Get the quicklink for an nh extension.")
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
            .addOptions(
                new OptionData(OptionType.INTEGER, "extension", "The extension to get")
                    .setRequiredRange(0, 999999).setRequired(true)
            );
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        LOGGER.debug("Replying to nh command.");
        MessageChannelUnion ch = event.getChannel();
        if (!event.isFromGuild() || (ch.getType().equals(ChannelType.TEXT) && ch.asTextChannel().isNSFW())) {
            event.deferReply().queue();
            String ext = String.format("%06d", event.getOption("extension").getAsLong());
            LOGGER.debug("Getting link for extension " + ext);
            new Thread(() -> {
                String url = String.format("https://nhentai.net/g/%1$s", ext);
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    // Get request
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "text/html");
                    connection.setRequestProperty("Accept", "text/html");
                    connection.setRequestProperty("Content-Type", "text/html; charset=UTF-8");

                    // Handle failure
                    int statusCode = connection.getResponseCode();
                    if (statusCode != HttpURLConnection.HTTP_OK && statusCode != HttpURLConnection.HTTP_CREATED) {
                        event.getHook().sendMessage("Sorry Senpai, I couldn't find that one.").queue();
                    } else {
                        event.getHook().sendMessage(url).queue();
                    }
                } catch (Exception e) {
                    event.getHook().sendMessage("Sorry Senpai, something happened while trying to find that.").queue();
                    LOGGER.warn("An error occurred while retrieving the page from nhentai.", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).start();
        }
        else {
            event.reply("Sorry Senpai. I can't share that in this channel.").queue();
        }
    }
}
