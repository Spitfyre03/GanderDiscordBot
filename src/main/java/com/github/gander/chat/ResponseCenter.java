package com.github.gander.chat;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;

import java.util.function.Consumer;
import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONObject;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import com.github.gander.App;
import com.github.gander.chat.commands.*;
import com.google.gson.JsonArray;

public class ResponseCenter extends ListenerAdapter{
    public static final Logger LOGGER = LoggerFactory.getLogger(ResponseCenter.class);
    private static final ResponseCenter singleton = new ResponseCenter();
    private final HashMap<String, ResponseHandler> responseMap = new HashMap<>();

    private static Random randomGen = new Random();

    private static JsonArray commandList;
    private static JsonArray reactionList;

    public static ResponseCenter getSingleton() { return singleton; }

    private ResponseCenter() {
        if (loadJSON()) {
            buildResponses();
        }
    }

    private boolean loadJSON() {
        LOGGER.info("Loading Responses.json");
        try {
            URL url = App.class.getResource("/Responses.json");
            if (url != null) {
                String path = url.getPath();
                JsonObject commandTree = JsonParser.parseReader(new FileReader(path)).getAsJsonObject();
                commandList = commandTree.getAsJsonArray("responses");
                reactionList = commandTree.getAsJsonArray("reactions");
                LOGGER.info("Responses.json loaded");
                return true;
            }
        }
        catch (FileNotFoundException ignored) {}
        catch (Exception e) {
            LOGGER.warn("Responses.json could not be loaded. Is it formatted correctly?", e);
        }
        return false;
    }

    private void buildResponses() {
        for (JsonElement command : commandList) {
            if (command.isJsonObject()) {
                switch (command.getAsJsonObject().get("handler").getAsString()) {
                    case "simple":
                        responseMap.put(command.getAsJsonObject().get("keyword").getAsString(), new SimpleHandler(command.getAsJsonObject()));
                        break;
                
                    default:
                        LOGGER.warn(String.format("Handler type %s has not been implemented. Skipping %s",
                            command.getAsJsonObject().get("handler").getAsString(),
                            command.getAsJsonObject().get("keyword").getAsString()));
                        break;
                }
            }
        }
    }

    private boolean channelAccepted(String allowedChannels, TextChannel textChannel) {
        return allowedChannels.contentEquals("all") ||
            (textChannel.isNSFW() && allowedChannels.contentEquals("nsfw"));
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        Message msg = event.getMessage();
        MessageChannelUnion channel = event.getChannel();
        if (channel.getType().equals(ChannelType.TEXT)) {
            TextChannel textChannel = channel.asTextChannel();
            if (!msg.getContentRaw().equals("")) {
                String strMsg = msg.getContentRaw().trim();
                Consumer<JSONObject> memeResponseConsumer = r -> {
                    String url = r.getJSONArray("results").getJSONObject(0).getJSONArray("media").getJSONObject(0).getJSONObject("tinygif").getString("url");
                    textChannel.sendMessage(url).queue();
                };
                Consumer<Exception> errorConsumer = e -> System.out.println("Error - " + e.getMessage());
                Consumer<String> textResponseConsumer = r -> {
                    if (r != null && !r.equals(""))
                        textChannel.sendMessage(r).queue();
                };

                for (String mapKey : responseMap.keySet()) {
                    if (strMsg.contains(mapKey)) {
                        responseMap.get(mapKey).respond(event, textResponseConsumer, errorConsumer);
                    }
                }
                //search the message for reaction keywords
                // for (JsonElement reaction : reactionList) {
                //     try {
                //         String reactionKW = reaction.getAsJsonObject().get("keyword").getAsString().toLowerCase();
                //         String allowedChannels = reaction.getAsJsonObject().get("channels").getAsString().toLowerCase();
                //         if(strMsg.contains(reactionKW) && (channelAccepted(allowedChannels, textChannel))) {
                //             JsonArray possibleReactions = reaction.getAsJsonObject().get("reaction_list").getAsJsonArray();
                //             String selectedReaction = possibleReactions.get(randomGen.nextInt(possibleReactions.size())).getAsString();
                //             msg.addReaction(selectedReaction).queue();
                //         }
                //     } catch (Exception e) {
                //         errorConsumer.accept(e);
                //     }

                // }
            }
        }
    }
}