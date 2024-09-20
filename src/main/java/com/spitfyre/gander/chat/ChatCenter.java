package com.spitfyre.gander.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.Checks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.spitfyre.gander.GanderApp.LOGGER;

public class ChatCenter extends ListenerAdapter {
    private static ChatCenter SINGLETON;
    private static final String RFR_PATH = Path.of("").toAbsolutePath() + "/assets/rfr.json";

    private final Map<Long, Map<Long, Long>> gmrMap;

    private ChatCenter() {
        if (SINGLETON != null) throw new IllegalStateException("ChatCenter has already been initialized");
        SINGLETON = this;
        this.gmrMap = new ConcurrentHashMap<>();
        try {
            loadMappings();
        }
        catch (IOException ioe) {
            LOGGER.error("An error occurred while loading the react-for-role mapping file." +
                "The mappings will not persist to a file for this run.", ioe);
        }
    }

    public static ChatCenter getInstance() {
        if (SINGLETON == null)
            new ChatCenter();
        return SINGLETON;
    }

    public void saveMappings() {
        JsonArray mappingsMap = new JsonArray();
        for (Map.Entry<Long, Map<Long, Long>> e : gmrMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            JsonObject guildEntry = new JsonObject();
            mappingsMap.add(guildEntry);
            guildEntry.addProperty("guild", e.getKey());
            JsonArray mappingEntries = new JsonArray();
            guildEntry.add("entries", mappingEntries);
            Map<Long, Long> guildMappings = e.getValue();
            for (Map.Entry<Long, Long> entry : guildMappings.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                JsonObject mapEntry = new JsonObject();
                mapEntry.addProperty("message", entry.getKey());
                mapEntry.addProperty("role", entry.getValue());
                mappingEntries.add(mapEntry);
            }
        }
        JsonArray fileArray = null;
        File file = new File(RFR_PATH);
        try (FileReader reader = new FileReader(file)) {
            fileArray = JsonParser.parseReader(reader).getAsJsonArray();
        }
        // Ignored because a malformed json should be saved over
        // And a new file should be created if one doesn't exist
        catch (Exception ignored) {}

        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("The rfr mapping directory could not be created.");
            }
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("The rfr mapping file could not be created.");
            }
            if (!mappingsMap.equals(fileArray)) {
                FileWriter writer = new FileWriter(file);
                writer.write(mappingsMap.toString());
                writer.flush();
                writer.close();
                LOGGER.info("Mappings saved successfully to file.");
            }
        }
        catch (IOException ioe) {
            LOGGER.error("An error occurred while trying to save the rfr mappings", ioe);
        }
    }

    public void loadMappings() throws IOException {
        File file = new File(RFR_PATH);
        LOGGER.info("Loading react-for-role mappings from resource file");
        if (file.exists()) {
            LOGGER.info("RFR resource file located at " + file.getAbsolutePath());
            FileReader reader = new FileReader(file);
            JsonElement fileObj = JsonParser.parseReader(reader);
            if (!fileObj.isJsonArray()) {
                new JsonWriter(new FileWriter(file)).beginArray().endArray().close();
                fileObj = JsonParser.parseReader(reader);
            }
            JsonArray mappings = fileObj.getAsJsonArray();
            synchronized (gmrMap) {
                mappings.forEach(
                    // For each object, load all the mappings. Continues to next entry if guild is missing or entries array is empty
                    objEntry -> {
                        if (!objEntry.isJsonObject()) return;
                        JsonObject obj = objEntry.getAsJsonObject();
                        if (!obj.has("guild") || !obj.get("guild").isJsonPrimitive()) return;
                        long guildID = obj.get("guild").getAsLong();
                        if (!obj.has("entries") || obj.get("entries").getAsJsonArray().isEmpty()) {
                            LOGGER.warn(String.format("No mappings were provided for guild %d. This entry will be skipped.", guildID));
                            return;
                        }
                        obj.get("entries").getAsJsonArray().forEach(
                            mappingElement -> {
                                JsonObject mapping = mappingElement.getAsJsonObject();
                                if (!(mapping.has("message") && mapping.has("role"))) {
                                    LOGGER.warn("The property for message or role was missing from an entry. This will be skipped");
                                }
                                else {
                                    long messageID = mapping.get("message").getAsLong();
                                    long roleID = mapping.get("role").getAsLong();
                                    addMapping(guildID, messageID, roleID);
                                }
                            }
                        );
                    }
                );
            }
            LOGGER.info("React-for-role mappings successfully loaded from file.");
        }
        else {
            LOGGER.warn("No rfr file was found at " + file.getAbsolutePath());
        }
    }

    public void addMapping(@Nonnull Long guildID, @Nonnull Long msgID, @Nonnull Long roleID) {
        Checks.notNull(guildID, "Guild ID");
        Checks.notNull(msgID, "Message ID");
        Checks.notNull(roleID, "Role ID");
        synchronized (gmrMap) {
            Map<Long, Long> mrMap = gmrMap.get(guildID);
            if (mrMap == null) {
                gmrMap.put(guildID, new HashMap<>(Map.of(msgID, roleID)));
            }
            else {
                mrMap.put(msgID, roleID);
            }
        }
    }

    public boolean removeMapping(@Nonnull Long guildID, @Nonnull Long msgID, @Nonnull Long roleID) {
        Checks.notNull(guildID, "Guild ID");
        Checks.notNull(msgID, "Message ID");
        Checks.notNull(roleID, "Role ID");
        synchronized (gmrMap) {
            Map<Long, Long> mrMap = gmrMap.get(guildID);
            if (mrMap != null) {
                return mrMap.remove(msgID, roleID);
            }
        }
        return false;
    }

    @Nullable
    public Long getMapping(@Nonnull Long guildID, @Nonnull Long msgID) {
        Checks.notNull(guildID, "Guild ID");
        Checks.notNull(msgID, "Message ID");
        synchronized (gmrMap) {
            Map<Long, Long> mrMap = gmrMap.get(guildID);
            return mrMap == null ? null : mrMap.get(msgID);
        }
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        if (event.isFromGuild()) {
            Guild server = event.getGuild();
            Map<Long, Long> msgRoleMap;
            msgRoleMap = gmrMap.get(server.getIdLong());
            if (msgRoleMap != null) {
                Long roleLong = msgRoleMap.get(event.getMessageIdLong());
                if (roleLong != null) {
                    Role role = server.getRoleById(roleLong);
                    if (role != null) {
                        event.retrieveMember().queue(member -> server.addRoleToMember(member, role).queue());
                    }
                }
            }
        }
    }

    @Override
    public void onMessageReactionRemove(@Nonnull MessageReactionRemoveEvent event) {
        if (event.isFromGuild()) {
            Guild server = event.getGuild();
            Map<Long, Long> msgRoleMap;
            msgRoleMap = gmrMap.get(server.getIdLong());
            if (msgRoleMap != null) {
                Long roleLong = msgRoleMap.get(event.getMessageIdLong());
                if (roleLong != null) {
                    Role role = server.getRoleById(roleLong);
                    if (role != null) {
                        event.retrieveMember().queue(member -> server.removeRoleFromMember(member, role).queue());
                    }
                }
            }
        }
    }
}
