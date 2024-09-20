package com.spitfyre.gander;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/**
 * @author Spitfyre03
 */
public class GanderApp {
	public static final Logger LOGGER = LoggerFactory.getLogger(GanderApp.class);
	private static final GanderApp INSTANCE = new GanderApp();
	public static GanderApp getInstance() { return INSTANCE; }

	private ShardManager shardManager = null;

	private GanderApp() {}

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

		this.shardManager = this.buildShareManager(token);
		this.startShutdownThread();
	}

	private ShardManager buildShareManager(String token) {
		DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createLight(token);
		return builder.build();
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
		}, "Gander-Shutdown-Hook").start();
	}
}
