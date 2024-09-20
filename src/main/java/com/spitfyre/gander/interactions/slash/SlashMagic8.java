package com.spitfyre.gander.interactions.slash;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class SlashMagic8 implements ISlashInteraction {

    @SlashCommand
    public static final SlashMagic8 magic8 = new SlashMagic8();

    private static final List<String> answers = new ArrayList<>();
    static {
        answers.addAll(
            List.of(
                "Yes",
                "It is certain",
                "Without a doubt",
                "For sure",
                "You may rely on it",
                "No",
                "Absolutely not",
                "Probably not",
                "Don't count on it",
                "Outlook doesn't look so hot",
                "My sources say no",
                "The answer isn't not yes",
                "The answer isn't not no",
                "If your name isn't Alex, then yes",
                "Go ask magic7",
                "What does magic7 say?",
                "Better not tell you now",
                "Very doubtful",
                "Fat chance",
                "Ask again later",
                "...",
                "If your name is Bruce, then no"
            )
        );
    }

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("magic8", "Peer into the depths of your destiny.")
                .addOption(OptionType.STRING, "inquiry", "What do you want to ask", true);
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.reply(answers.get((int)(Math.random() * answers.size()))).queue();
    }
}
