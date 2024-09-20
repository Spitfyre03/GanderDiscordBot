package com.spitfyre.gander.interactions;

import com.spitfyre.gander.GanderApp;
import com.spitfyre.gander.interactions.buttons.IButtonInteraction;
import com.spitfyre.gander.interactions.slash.ISlashInteraction;
import com.spitfyre.gander.interactions.slash.SlashCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The hub for all Gander Discord interactions. This class hosts event listeners for
 * SlashCommand and ButtonInteraction events, and passes these events along to
 * registered handlers.<br>
 * <br>
 * To create a Slash Command for this bot, create a new class in
 * {@link com.spitfyre.gander.interactions} or a subpackage, and have this class implement
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
 * <br>
 * Currently, button interactions are only registered via registered slash interactions. Modals
 * are currently not supported at all. A separate registration framework for Buttons and Modals
 * will be implemented in a future update.
 */
public class InteractionCenter extends ListenerAdapter {
    public static final Logger LOGGER = LoggerFactory.getLogger(InteractionCenter.class);
    private static InteractionCenter singleton;

    private final Map<String, ISlashInteraction> slashHandlers = new HashMap<>();
    private final Map<String, IButtonInteraction> buttonHandlers = new HashMap<>();

    private InteractionCenter() {
        if (singleton != null) throw new IllegalStateException("InteractionCenter has already been instantiated.");
        singleton = this;
    }

    public static InteractionCenter getSingleton() {
        if (singleton == null)
            new InteractionCenter();
        return singleton;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        registerCommands(GanderApp.getInstance().getShardManager());
    }

    private void registerCommands(@Nonnull ShardManager shardManager) {
        LOGGER.info("Registering slash commands for Gander");
        List<ISlashInteraction> collectedCommands = collectSubscribedHandlers();
        LOGGER.info("Commands collected for registration: {}", collectedCommands.stream().map(interaction -> interaction.getCommand().getName()).collect(Collectors.joining(", ")));
        collectedCommands.forEach(slash -> {
            String name = slash.getCommand().getName();
            slashHandlers.put(name, slash);

            if (slash instanceof IButtonInteraction handler) {
                handler.getButtons().stream().filter(Objects::nonNull).forEach(b -> {
                    LOGGER.debug("Registering button handler {} for command {}", b.getId(), name);
                    buttonHandlers.put(b.getId(), handler);
                });
            }
        });

        Map<Long, List<ISlashInteraction>> commandsToRegister = collectedCommands.stream()
            .flatMap(c -> Optional.ofNullable(c.getGuilds())
                .filter(Predicate.not(List::isEmpty))
                .map(ids -> ids.stream().map(id -> new AbstractMap.SimpleEntry<>(id, c)))
                .orElseGet(() -> Stream.of(new AbstractMap.SimpleEntry<>(-1L, c))))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(
                    Map.Entry::getValue,
                    Collectors.toList()
                )
            ));

        Optional.ofNullable(shardManager.getShardById(0)).ifPresent((JDA shard) ->
            commandsToRegister.forEach((id, commands) -> {
                List<CommandData> data = commands.stream().map(ISlashInteraction::getCommand).toList();
                if (id == -1) {
                    shard.updateCommands().addCommands(data).queue(
                    );
                }
                else {
                    Optional.ofNullable(shard.getGuildById(id))
                        .ifPresentOrElse(
                            guild -> guild.updateCommands().addCommands(data).queue(
                                cmds -> LOGGER.info("Global commands were successfully updated for guild {}-{}.", id, guild.getName()),
                                t -> LOGGER.error("Commands could not be updated for guild.", t)
                            ),
                            () -> LOGGER.warn("No guild found for ID {}. Commands subscribed to this guild will not be updated.", id)
                        );
                }
            })
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
     * commands defined and appropriately annotated in InteractionCenter, SlashPing, or SlashTenor,
     * would all be collected by this method.
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
                    LOGGER.warn("An object of type {} is not an instance of ISlashInteraction. It will not be registered", o.getClass().getSimpleName());
                    return false;
                }
            })
            .map(ISlashInteraction.class::cast)
            .toList();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        LOGGER.debug("Capturing slash event for command {}", event.getName());
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
        LOGGER.debug("Capturing button interaction event for button {}", event.getComponentId());
        IButtonInteraction handler = buttonHandlers.get(event.getComponentId());
        try {
            if (handler != null) handler.onInteract(event);
        }
        catch (Exception e) {
            LOGGER.error("An uncaught exception was thrown while processing a button interaction.", e);
        }
    }
}
