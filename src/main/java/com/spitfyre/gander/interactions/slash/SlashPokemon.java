package com.spitfyre.gander.interactions.slash;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.spitfyre.gander.GanderApp.LOGGER;

public class SlashPokemon implements ISlashInteraction {

    @SlashCommand
    public static final SlashPokemon poke = new SlashPokemon();

    @Nonnull
    @Override
    public CommandData getCommand() {
        return Commands.slash("pokemon", "Who's that Pokemon?")
            .addOption(OptionType.STRING, "get", "Get a specific pokemon by name or number", false);
    }

    @Override
    public void onCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        new Thread(() -> {
            OptionMapping option = event.getOption("get");
            String get = (option == null ? String.valueOf((int)(Math.random() * 897 + 1)) : option.getAsString());
            String pokemonUrl = String.format("https://pokeapi.co/api/v2/pokemon/%s",get);
            String speciesUrl = String.format("https://pokeapi.co/api/v2/pokemon-species/%s", get);
            LOGGER.debug(String.format("Getting pokemon for url %s and option %s", pokemonUrl, option));

            HttpURLConnection pokemon = null;
            HttpURLConnection species = null;
            try {
                pokemon = requestGet(pokemonUrl);
                species = requestGet(speciesUrl);

                // Handle failure
                int statusPokemon = pokemon.getResponseCode();
                int statusSpecies = species.getResponseCode();
                if (statusPokemon == HttpURLConnection.HTTP_NOT_FOUND || statusSpecies == HttpURLConnection.HTTP_NOT_FOUND) {
                    event.getHook().sendMessage(String.format("Sorry, I couldn't find anything for pokemon %s", get)).queue();
                }
                else if (statusPokemon != HttpURLConnection.HTTP_OK && statusPokemon != HttpURLConnection.HTTP_CREATED) {
                    event.getHook().sendMessage("There was an issue connecting to the server. Please report this to Spitfyre.").queue();
                    LOGGER.warn("Error codes {} and {} were returned from PokeAPI. This command will not be fulfilled.", statusPokemon, statusSpecies);
                }
                else {
                    JsonElement pokemonData = parseResponse(pokemon);
                    JsonElement speciesData = parseResponse(species);
                    String name = "";
                    for (JsonElement entry : speciesData.getAsJsonObject().getAsJsonArray("names")) {
                        JsonObject nameEntry = entry.getAsJsonObject();
                        if (nameEntry.getAsJsonObject("language").get("name").getAsString().equals("en")) {
                            name = nameEntry.get("name").getAsString();
                            break;
                        }
                    }
                    int dexNum = 0;
                    for (JsonElement entry : speciesData.getAsJsonObject().getAsJsonArray("pokedex_numbers")) {
                        JsonObject dexEntry = entry.getAsJsonObject();
                        if (dexEntry.getAsJsonObject("pokedex").get("name").getAsString().equals("national")) {
                            dexNum = dexEntry.get("entry_number").getAsInt();
                            break;
                        }
                    }
                    String genus = "";
                    for (JsonElement entry : speciesData.getAsJsonObject().getAsJsonArray("genera")) {
                        JsonObject genusEntry = entry.getAsJsonObject();
                        if (genusEntry.getAsJsonObject("language").get("name").getAsString().equals("en")) {
                            genus = genusEntry.get("genus").getAsString();
                            break;
                        }
                    }
                    String desc;
                    List<JsonElement> descList = speciesData.getAsJsonObject().getAsJsonArray("flavor_text_entries").asList().stream().filter(
                        obj -> {
                            JsonObject entry = obj.getAsJsonObject();
                            return entry.getAsJsonObject("language").get("name").getAsString().equals("en");
                        }
                    ).toList();
                    desc = descList.get((int)(Math.random() * descList.size())).getAsJsonObject().get("flavor_text").getAsString();
                    String spriteURL = pokemonData.getAsJsonObject().getAsJsonObject("sprites").getAsJsonObject("other").getAsJsonObject("home").get("front_default").getAsString();

                    MessageEmbed embed = new EmbedBuilder()
                            .setTitle(String.format("Pokedex Entry #%d", dexNum))
                            .setImage(spriteURL)
                            .addField(name, genus, false)
                            .addField("Description", desc.replaceAll("[\f\n]", " "), false)
                            .build();
                    event.getHook().sendMessageEmbeds(embed).queue();
                }
            }
            catch (IOException ioe) {
                event.getHook().sendMessage("Sorry, something happened while trying to find that.").queue();
                LOGGER.warn("An error occurred while retrieving the page from PokeAPI.", ioe);
            }
            catch (NullPointerException | JsonIOException err) {
                event.getHook().sendMessage("Some data was missing from the site. Please report this to Spitfyre.").queue();
                LOGGER.warn("A key or value was missing from an expected result from PokeAPI. Please check the endpoint version and verify expected results.", err);
            }
            finally {
                if (pokemon != null) {
                    pokemon.disconnect();
                }
                if (species != null) {
                    species.disconnect();
                }
            }
        }).start();
    }

    private HttpURLConnection requestGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "text/html");
        connection.setRequestProperty("Accept", "text/html");
        connection.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
        return connection;
    }

    /**
     * Parse the response into JSONObject
     */
    private static JsonElement parseResponse(HttpURLConnection connection) {
        if (connection == null) { return null; }
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
        catch (IOException ioe) {
            LOGGER.error("Could not parse JSON object from HTTP response.", ioe);
        }
        return null;
    }
}
