package com.spitfyre.gander.interactions.slash;

import com.spitfyre.gander.interactions.buttons.IButtonInteraction;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.spitfyre.gander.GanderApp.LOGGER;

public class SlashEmoji implements ISlashInteraction, IButtonInteraction {

    @SlashCommand
    public static final SlashEmoji emoji = new SlashEmoji();

    private static final Button APPROVE = Button.success("emoji-add-approve", "Approve");
    private static final Button DENY = Button.danger("emoji-add-deny", "Deny");

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("emoji", "Emoji manager")
            .addSubcommands(
                new SubcommandData("add", "Add an emoji")
                    .addOption(OptionType.STRING, "alias", "The name for the new emoji", true)
                    .addOption(OptionType.ATTACHMENT, "emoji", "The emoji to add", true)
            );
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        if (event.isFromGuild()) {
            event.deferReply().queue();
            String name = event.getOption("alias", "", OptionMapping::getAsString).toLowerCase();
            if (!name.isEmpty()) {
                event.getGuild().retrieveEmojis().queue(l -> {
                    // TODO check Guild for emote limit and availability
                    if (l.stream().anyMatch(e -> e != null && e.getName().equalsIgnoreCase(name))) {
                        event.getHook().sendMessage("An emoji already exists by this name. Please choose another alias.").queue();
                    }
                    else {
                        Message.Attachment emojiFile = event.getOption("emoji", OptionMapping::getAsAttachment);
                        String fileName = String.format("%s_%s_%s.%s", event.getGuild().getId(), event.getId(), name, emojiFile.getFileExtension());
                        emojiFile.getProxy().downloadToFile(new File(fileName)).thenAcceptAsync(f -> {
                            Member requestor = event.getMember();
                            try {
                                if (f.length() > 262144) {
                                    event.getHook().sendMessage(requestor.getAsMention() + " The submitted file is too large for an emoji. The maximum size is 256.0 kb.").queue();
                                    LOGGER.info("The file submitted for {} is too large for an emoji. Request rejected.", name);
                                    if (f.delete()) LOGGER.debug("File {} deleted.", fileName);
                                }
                                else if (event.getMember().hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
                                    event.getGuild().createEmoji(name, Icon.from(f))
                                        .queue(
                                            emoji -> {
                                                event.getHook().editOriginal(new MessageEditBuilder()
                                                    .setContent(requestor.getAsMention() + " Emoji successfully created: " + emoji.getAsMention()).build())
                                                    .queue(
                                                        message -> {
                                                            LOGGER.info("Emoji creation request auto-approved for {}.", name);
                                                            if (f.delete()) LOGGER.debug("File {} deleted.", fileName);
                                                        },
                                                        failure -> {
                                                            if (f.delete()) LOGGER.debug("File {} deleted.", fileName);
                                                        }
                                                    );
                                            }
                                        );
                                }
                                else {
                                    event.getHook().sendMessage(new MessageCreateBuilder()
                                        .addFiles(FileUpload.fromData(f, fileName))
                                        .addContent(String.format("Name: %s\nRequestor: %s", name, requestor.getAsMention()))
                                        .addComponents(ActionRow.of(this.getButtons()))
                                        .build()
                                    ).queue(
                                        message -> {
                                            LOGGER.info("Emoji {} submitted for approval.", name);
                                            if (f.delete()) LOGGER.debug("File {} deleted.", fileName);
                                        },
                                        failure -> {
                                            if (f.delete()) LOGGER.debug("File {} deleted.", fileName);
                                        }
                                    );
                                }
                            }
                            catch (IOException ioe) {
                                LOGGER.error("An I/O error occurred while handling the attachment.", ioe);
                            }
                            finally {
                                f.delete();
                            }
                        });
                    }
                });
            }
        }
    }

    @Override
    public List<Button> getButtons() {
        return List.of(APPROVE, DENY);
    }

    @Override
    public void onInteract(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            Message request = event.getMessage();
            Member member = event.getMember();
            if (member != null && member.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
                event.deferEdit().queue();
                String id = event.getComponentId();
                User mention = request.getMentions().getUsers().get(0);
                String requester = mention != null ? String.format("%s ", mention.getAsMention()) : "";
                String[] msg = request.getContentRaw().split("\n");
                String name = msg[0].substring(msg[0].indexOf(":") + 2);
                if (id.equals(APPROVE.getId())) {
                    try {
                        event.getMessage().getAttachments().get(0).getProxy().downloadAsIcon().thenAcceptAsync(icon -> {
                            event.getGuild().createEmoji(name, icon)
                                .queue(
                                    emoji -> {
                                        event.getHook().editOriginalComponents(List.of()).queue();
                                        event.getHook().sendMessage(requester + "Request was approved: " + emoji.getAsMention()).queue();
                                    },
                                    ex -> {
                                        LOGGER.error("Could not create new emoji", ex);
                                    }
                                );
                        });
                    }
                    catch (Exception e) {
                        LOGGER.error(String.format("An error occurred while processing event $1%s from message $2%s", event.getId(), event.getMessage().getId()));
                        LOGGER.error("Could not download or create Icon for an emoji file.", e);
                    }
                }
                else if (id.equals(DENY.getId())) {
                    event.getHook().editOriginalComponents(List.of()).queue();
                    event.getHook().sendMessage(requester + "Request was denied").queue();
                }
            }
            else {
                event.reply("You do not have permission to respond to this.").setEphemeral(true).queue();
            }
        }
    }
}
