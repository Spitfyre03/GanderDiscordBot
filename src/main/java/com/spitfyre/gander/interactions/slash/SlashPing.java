package com.spitfyre.gander.interactions.slash;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;

/**
 * Because I am an agent of chaos
 *
 * @author Spitfyre03
 */
public class SlashPing implements ISlashInteraction {
    @SlashCommand
    public static final SlashPing ping = new SlashPing();

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("ping", "Ping pong!");
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("Pong!").queue();
        }
        else {
            event.deferReply().queue();
            event.getGuild()
                .findMembers(member -> member.hasAccess(event.getGuildChannel()))
                .onSuccess(members -> {
                    int index = (int)(Math.random() * members.size());
                    event.getHook().sendMessage(members.get(index).getAsMention()).queue();
                });
        }
    }
}
