package com.github.gander;

import com.github.gander.chat.ChatCenter;
import com.github.gander.chat.ResponseCenter;
import com.github.gander.interactions.InteractionCenter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.internal.utils.Checks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Spitfyre03
 */
public class App {

	public static String TOKEN;
	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
	private static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> mappingSaver;

	private App() {}

	public static void main(String[] args) throws InterruptedException, LoginException {
		try {
			LOGGER.info("Loading JDA Application token.");
			// TODO copy token to assets to make key configurable
			InputStream secrets = App.class.getResourceAsStream("/assets/secrets.json");
			JsonObject secretsTree = JsonParser.parseReader(new InputStreamReader(secrets)).getAsJsonObject();
			TOKEN = secretsTree.get("bot_token").getAsString();
			Checks.notBlank(TOKEN, "bot_token");
			LOGGER.info("JDA Bot token successfully retrieved.");
		}
		catch (Exception e) {
			LOGGER.error("There was an error while reading the token file.", e);
			return;
		}

		JDABuilder builder = JDABuilder.createLight(TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS);
		InteractionCenter cmdCntr = InteractionCenter.getSingleton();
		//ResponseCenter rspCntr = ResponseCenter.getSingleton();
		ChatCenter chtCntr = ChatCenter.getInstance();
		mappingSaver = scheduledExecutor.scheduleAtFixedRate(ChatCenter::saveMappings, 1, 10, TimeUnit.MINUTES);
		builder.addEventListeners(cmdCntr, chtCntr);
		JDA bot = builder.build();
		bot.awaitReady();

		cmdCntr.registerCommands(bot);
		new Thread(() -> {
			Scanner input = new Scanner(System.in);
			while (true) {
				if (input.hasNext()) {
					String cmd = input.next();
					if (cmd.equals("stop")) {
						bot.shutdown();
						LOGGER.info("Bot shutting down.");
						break;
					}
					else if (cmd.equals("kill")) {
						bot.shutdownNow();
						LOGGER.warn("Bot is being forcefully shut down.");
						break;
					}
				}
			}
			input.close();
			scheduledExecutor.shutdown();
			mappingSaver.cancel(false);
			ChatCenter.saveMappings();
		}, "App-Shutdown-Hook").start();
	}
}
