package com.spitfyre.gander.interactions.slash;

import com.spitfyre.gander.chat.ChatCenter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import javax.annotation.Nonnull;

public class SlashRfR implements ISlashInteraction {

    @SlashCommand
    public static final SlashRfR cmd = new SlashRfR();

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("rfr", "React-for-role mapping admin command.")
            .addSubcommands(
                new SubcommandData("add", "Add a message->role mapping, or change a pre-existing one")
                    .addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", "The channel the message is in", true),
                        new OptionData(OptionType.STRING, "msg_id", "The message numeric ID.", true),
                        new OptionData(OptionType.ROLE, "role", "The role to map.", true)
                    ),
                new SubcommandData("rm", "Remove a message->role mapping.")
                    .addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", "The channel the message is in", true),
                        new OptionData(OptionType.STRING, "msg_id", "The message numeric ID.", true),
                        new OptionData(OptionType.ROLE, "role", "The role to un-map.", true)
                    )
            );
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (event.isFromGuild()) {
            Long guildID = event.getGuild().getIdLong();
            Member member = event.getMember();
            if (member != null && member.hasPermission(Permission.MANAGE_ROLES)) {
                String sub = event.getSubcommandName();
                GuildMessageChannel channel = event.getOption("channel", OptionMapping::getAsChannel).asGuildMessageChannel();
                Role role = event.getOption("role", OptionMapping::getAsRole);
                long msgID = event.getOption("msg_id", 0L, OptionMapping::getAsLong);
                channel.retrieveMessageById(msgID).queue(
                    msg -> {
                        if ("add".equals(sub)) {
                            // TODO check message exists too
                            if (role != null && member.canInteract(role)) {
                                ChatCenter.getInstance().addMapping(guildID, msgID, role.getIdLong());
                                event.getHook()
                                    .sendMessage("Mapping was successfully added for role " + role.getName())
                                    .addEmbeds(new EmbedBuilder().addField(msg.getAuthor().getName(), msg.getContentDisplay(), true).build())
                                    .queue();
                            }
                            else {
                                event.getHook()
                                    .sendMessage("You can't add a mapping for a role equal or higher than yourself.")
                                    .queue();
                            }
                        }
                        else if ("rm".equals(sub)) {
                            // TODO check message exists too
                            if (role != null && member.canInteract(role)) {
                                if (ChatCenter.getInstance().removeMapping(guildID, msgID, role.getIdLong())) {
                                    event.getHook()
                                        .sendMessage("Mapping was successfully removed for role " + role.getName())
                                        .addEmbeds(new EmbedBuilder().addField(msg.getAuthor().getName(), msg.getContentDisplay(), true).build())
                                        .queue();
                                }
                                else {
                                    event.getHook()
                                        .sendMessage("The message or role could not be found with those IDs")
                                        .queue();
                                }
                            }
                            else {
                                event.getHook()
                                    .sendMessage("You can't change a mapping for a role equal or higher than yourself.")
                                    .queue();
                            }
                        }
                    },
                    throwable -> event.getHook().sendMessage("No message was found for that ID").queue()
                );
            }
            else {
                event.getHook()
                    .sendMessage("You don't have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            }
        }
        else {
            event.getHook()
                .sendMessage("You can't use this here.")
                .queue();
        }
    }
}
