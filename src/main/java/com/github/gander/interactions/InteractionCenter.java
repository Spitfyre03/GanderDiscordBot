package com.github.gander.interactions;

import com.github.gander.interactions.buttons.IButtonInteraction;
import com.github.gander.interactions.slash.ISlashInteraction;
import com.github.gander.interactions.slash.SlashCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The hub for all Gander Discord interactions. This class hosts event listeners for
 * SlashCommand and ButtonInteraction events, and passes these events along to
 * registered handlers.<br>
 * <br>
 * To create a Slash Command for this bot, create a new class in
 * {@link com.github.gander.interactions} or a subpackage, and have this class implement
 * {@link ISlashInteraction}. Refer to the documentation on how to create an effective
 * implementation.<br>
 * Within this new class, create and initialize a public static instance of your handler and
 * annotate this field with {@link SlashCommand}.<br>
 * To register a command, consider the following example
 * <h3>Example</h3><pre>{@code
 * public class SlashPing implements ISlashInteraction {
 *    @SlashCommand
 *    public static final SlashPing ping = new SlashPing();
 *    // ISlashInteraction methods implemented below
 * }
 * }</pre>
 * The instance <em>ping</em> satisfies the requirements of implementing {@link ISlashInteraction}
 * and being annotated with {@link SlashCommand}. This object would be collected for registration.
 *
 * Currently, button interactions are only registered via registered slash interactions. Modals
 * are currently not supported at all. A separate registration framework for Buttons and Modals
 * will be implemented in a future update.
 */
public class InteractionCenter extends ListenerAdapter {

    public static final Logger LOGGER = LoggerFactory.getLogger(InteractionCenter.class);
    private static final InteractionCenter singleton = new InteractionCenter();

    private final Map<String, ISlashInteraction> slashHandlers = new HashMap<>();
    private final Map<String, IButtonInteraction> buttonHandlers = new HashMap<>();

    private InteractionCenter() {}

    public static InteractionCenter getSingleton() { return singleton; }

    // should be called after bot starts up
    public void registerCommands(@Nonnull JDA bot) {
        LOGGER.info("Registering slash commands for Waifu");
        List<ISlashInteraction> commandsToRegister = collectSubscribedHandlers();
        LOGGER.info(String.format(
            "Commands collected for registration: %s",
            commandsToRegister.stream().map(interaction -> interaction.getCommand().getName()).collect(Collectors.joining(", "))
        ));

        CompletableFuture<List<Command>> globalCommandsFuture = bot.retrieveCommands().submit();
        Map<Long, CompletableFuture<List<Command>>> guildCommandsFuture = new HashMap<>();
        bot.getGuilds().forEach(g -> guildCommandsFuture.put(g.getIdLong(), g.retrieveCommands().submit()));

        CompletableFuture.allOf(guildCommandsFuture.values().toArray(new CompletableFuture[0])).thenAcceptBothAsync(
            globalCommandsFuture, (_void, globalCommands) -> {
                Map<Long, List<Command>> guildCommands = guildCommandsFuture
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().join()));
                commandsToRegister.forEach(slash -> {
                    String name = slash.getCommand().getName();
                    List<Long> guildsToRegister = slash.getGuilds();
                    if (Optional.ofNullable(guildsToRegister).orElse(List.of()).isEmpty()) {
                        bot.upsertCommand(slash.getCommand()).queue(cmd ->
                            LOGGER.debug(String.format("Global command %s queued to register", name))
                        );
                        globalCommands.removeIf(gCmd -> gCmd.getName().equals(name));
                    }
                    else {
                        guildsToRegister.forEach(g -> {
                            Guild guildToRegister = bot.getGuildById(g);
                            if (guildToRegister != null) {
                                guildToRegister.upsertCommand(slash.getCommand()).queue(cmd ->
                                    LOGGER.debug(String.format("Command %s queued to register for guild %d", name, g))
                                );
                                guildCommands.get(g).removeIf(gCmd -> gCmd.getName().equals(name));
                            }
                        });
                    }
                    slashHandlers.put(name, slash);

                    if (slash instanceof IButtonInteraction handler) {
                        handler.getButtons().stream().filter(Objects::nonNull).forEach(b -> {
                            LOGGER.debug(String.format("Registering button handler %s for command %s",
                                b.getId(),
                                name));
                            buttonHandlers.put(b.getId(), handler);
                        });
                    }
                });
                globalCommands.forEach(cmd -> cmd.delete().submit().thenRunAsync(
                    () -> LOGGER.info(String.format("Deleted global command %s", cmd.getName()))
                ));
                guildCommands.forEach(
                    (key, val) -> val.forEach(
                        cmd -> cmd.delete().submit().thenRunAsync(
                            () -> LOGGER.info(String.format("Deleted command %s for guild %d", cmd.getName(), key))
                        )
                    )
                );
            }
        );
    }

    /**
     * Collects all ISlashInteraction instances annotated with {@link SlashCommand} in
     * the same package or subpackages of {@link InteractionCenter}. For example, in
     * the following setup
     * <pre>
     * com.github.gander
     *    App.class
     *    .interactions
     *       InteractionCenter.class
     *       .slash
     *          SlashPing.class
     *          SlashTenor.class
     * </pre>
     * @return a Nonnull List of {@link ISlashInteraction} handlers annotated
     *      with {@link SlashCommand}
     */
    @Nonnull
    private List<ISlashInteraction> collectSubscribedHandlers() {
        Reflections reflections = new Reflections(InteractionCenter.class.getPackageName(), Scanners.FieldsAnnotated);
        return reflections.getFieldsAnnotatedWith(SlashCommand.class).stream()
            .map(field -> {
                try {
                    return field.get(null);
                }
                catch (Exception e) {
                    LOGGER.warn("An error was thrown while collecting commands to register", e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(o -> {
                if (o instanceof ISlashInteraction) {
                    return true;
                }
                else {
                    LOGGER.warn(
                        String.format(
                            "An object of type %s is not an instance of ISlashInteraction. It will not be registered",
                            o.getClass().getSimpleName()
                        )
                    );
                    return false;
                }
            })
            .map(o -> (ISlashInteraction)o )
            .toList();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        LOGGER.debug("Capturing slash event for command " + event.getName());
        ISlashInteraction command = slashHandlers.get(event.getName());
        try {
            if (command != null) command.onCommand(event);
        }
        catch (Exception e) {
            LOGGER.error("An uncaught exception was thrown while processing a slash command.", e);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        LOGGER.debug("Capturing button interaction event for button " + event.getComponentId());
        IButtonInteraction handler = buttonHandlers.get(event.getComponentId());
        try {
            if (handler != null) handler.onInteract(event);
        }
        catch (Exception e) {
            LOGGER.error("An uncaught exception was thrown while processing a button interaction.", e);
        }
    }
}
