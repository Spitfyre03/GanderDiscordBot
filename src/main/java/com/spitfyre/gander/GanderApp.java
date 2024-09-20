package com.spitfyre.gander;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spitfyre.gander.chat.ChatCenter;
import com.spitfyre.gander.interactions.InteractionCenter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.internal.utils.Checks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Spitfyre03
 */
public class GanderApp {
	public static final Logger LOGGER = LoggerFactory.getLogger(GanderApp.class);
	private static ScheduledFuture<?> mappingSaver;

	private static final GanderApp INSTANCE = new GanderApp();
	public static GanderApp getInstance() { return INSTANCE; }

	private final ScheduledExecutorService scheduledExecutor;
	private ShardManager shardManager = null;

	private GanderApp() {
		this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	}

	public static void main(String[] args) throws FileNotFoundException {
		getInstance().startBot(args);
	}

	private void startBot(String[] args) throws FileNotFoundException {
		LOGGER.info("Loading JDA Application token.");
		InputStream secrets = GanderApp.class.getResourceAsStream("/assets/secrets.json");
		JsonObject secretsTree = Optional.ofNullable(secrets)
				.map(InputStreamReader::new).map(JsonParser::parseReader).map(JsonElement::getAsJsonObject)
				.orElseThrow(() -> new FileNotFoundException("Secrets json not found in assets folder!"));
		String token = Optional.ofNullable(secretsTree.get("bot_token"))
				.map(JsonElement::getAsString)
				.orElseThrow(() -> new IllegalArgumentException("Missing bot_token in secrets json file!"));
		Checks.notBlank(token, "bot_token");
		LOGGER.info("JDA Bot token successfully retrieved.");

		this.shardManager = this.buildShardManager(token);
		this.startShutdownThread();
	}

	private ShardManager buildShardManager(String token) {
		DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createLight(token, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS);
		ChatCenter chtCntr = ChatCenter.getInstance();
		mappingSaver = scheduledExecutor.scheduleAtFixedRate(chtCntr::saveMappings, 1, 10, TimeUnit.MINUTES);
		builder.addEventListeners(InteractionCenter.getSingleton(), chtCntr);
		return builder.build();
	}

	public ShardManager getShardManager() {
		return this.shardManager;
	}

	private void startShutdownThread() {
		new Thread(() -> {
			Scanner input = new Scanner(System.in);
			while (input.hasNext()) {
				String cmd = input.next();
				if (cmd.equals("stop")) {
					this.shardManager.shutdown();
					LOGGER.info("Shutting down bot shards.");
					break;
				}
			}
			input.close();
			scheduledExecutor.shutdown();
			mappingSaver.cancel(false);
			ChatCenter.getInstance().saveMappings();
		}, "Gander-Shutdown-Hook").start();
	}
}
