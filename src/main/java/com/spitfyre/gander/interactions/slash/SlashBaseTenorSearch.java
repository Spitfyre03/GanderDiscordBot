package com.spitfyre.gander.interactions.slash;

import com.google.gson.JsonElement;
import com.spitfyre.gander.http.helpers.HttpHelper;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

import static com.spitfyre.gander.GanderApp.LOGGER;

public class SlashBaseTenorSearch implements ISlashInteraction {
    private static final String API_KEY = "2WFV9G1IPF7I";

    @SlashCommand public static final SlashBaseTenorSearch smashing = new SlashBaseTenorSearch("smashing", "Smashing!", "nigel thornberry smashing");
    @SlashCommand public static final SlashBaseTenorSearch cagemebro = new SlashBaseTenorSearch("cagemebro", "I'm going to steal the Declaration of Independence", "nick cage");
    @SlashCommand public static final SlashBaseTenorSearch deuces = new SlashBaseTenorSearch("deuces", "Peace bitches", "deuces");
    @SlashCommand public static final SlashBaseTenorSearch bruh = new SlashBaseTenorSearch("bruh", "Bruh moment, bruh", "bruh");

    private final String name;
    private final String description;
    private final String searchPhrase;

    public SlashBaseTenorSearch(String cmd, String desc, String phrase) {
        this.name = cmd;
        this.description = desc;
        this.searchPhrase = phrase;
    }

    @Nonnull
    @Override
    public CommandData getCommand() { return Commands.slash(this.name, this.description); }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        Consumer<Exception> error = e -> {
                LOGGER.error("An error was thrown while retrieving a gif from Tenor.", e);
                event.getHook().sendMessage("Sorry I couldn't get this gif. Please let Spitfyre know.").queue();
            };
        Consumer<JsonElement> response = (JsonElement r) -> {
                try {
                    String url = r.getAsJsonObject()
                            .getAsJsonArray("results")
                            .get(0).getAsJsonObject()
                            .getAsJsonArray("media")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("tinygif")
                            .get("url").getAsString();
                    event.getHook().sendMessage(url).queue();
                }
                catch (Exception e) {
                    error.accept(e);
                }
            };
        getSearchResults(this.searchPhrase, response, error);
    }

    /**
     * Get Search Result GIFs
     */
    private static void getSearchResults(String searchTerm, Consumer<JsonElement> responseHandler, Consumer<Exception> errorHandler) {
        searchTerm = searchTerm.replace(" ", "%20");
        final String url = String.format("https://g.tenor.com/v1/random?q=%1$s&key=%2$s&limit=1", searchTerm, API_KEY);
        // TODO implement a thread pool for this class
        new Thread(() -> {
            try {
                responseHandler.accept(HttpHelper.get(url));
            }
            catch (Exception e) {
                errorHandler.accept(e);
            }
        }).start();
    }
}
