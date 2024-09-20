package com.spitfyre.gander.interactions.slash;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SlashRoll implements ISlashInteraction {

    @SlashCommand
    public static final SlashRoll roll = new SlashRoll();

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("roll", "Hope for a nat 20!")
            .addOptions(
                new OptionData(OptionType.INTEGER, "die", "How many die to roll. Default 1, max 10.")
                    .setRequiredRange(1, 10),
                new OptionData(OptionType.INTEGER, "sides", "How many sides on each dice. Default 6, min 3, max 5000.")
                    .setRequiredRange(3, 5000)
            );
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        Member roller = event.getMember();
        long die = event.getOption("die", 1L, OptionMapping::getAsLong);
        long sides = event.getOption("sides", 6L, OptionMapping::getAsLong);
        List<Long> rolls = new Random().longs(die, 1, sides).boxed().toList();
        String response = rolls.stream().map(Object::toString).collect(Collectors.joining(", "));
        EmbedBuilder embed = new EmbedBuilder().addField(
            String.format(
                "%s rolled %dd%d",
                roller.getEffectiveName(),
                die,
                sides
            ),
            response,
            false
        ).addField("Total", String.valueOf(rolls.stream().mapToLong(Long::longValue).sum()), false);
        event.getHook().sendMessage(roller.getAsMention()).addEmbeds(embed.build()).queue();
    }
}
