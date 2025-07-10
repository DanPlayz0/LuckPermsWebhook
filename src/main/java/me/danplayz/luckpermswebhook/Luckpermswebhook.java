package me.danplayz.luckpermswebhook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.log.LogReceiveEvent;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeClearEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.node.Node;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Luckpermswebhook extends JavaPlugin {

    private List<String> webhookUrls;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!isWorkingConfig()) return;
        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms != null) {
            EventBus eventBus = luckPerms.getEventBus();
            eventBus.subscribe(this, NodeAddEvent.class, this::onNodeAdd);
            eventBus.subscribe(this, NodeRemoveEvent.class, this::onNodeRemove);
            eventBus.subscribe(this, NodeClearEvent.class, this::onNodeClear);
            eventBus.subscribe(this, LogReceiveEvent.class, this::onLogEvent);
        } else {
            throw new IllegalStateException("LuckPerms not found");
        }
        getLogger().info("LuckPermsWebhook plugin has been enabled.");
    }

    public boolean isWorkingConfig() {
        FileConfiguration config = getConfig();
        Object rawWebhook = config.get("webhook_url");
        webhookUrls = new ArrayList<>();

        if (rawWebhook instanceof String) {
            webhookUrls.add((String) rawWebhook);
        } else if (rawWebhook instanceof List) {
            webhookUrls.addAll(config.getStringList("webhook_url"));
        } else {
            getLogger().warning("Invalid format for webhook_url. Must be string or list of strings.");
            return false;
        }

        webhookUrls.removeIf((url) -> url.equals("WEBHOOK_URL") || url.equals("https://discord.com/api/webhooks/..."));

        if (webhookUrls.isEmpty()) {
            getLogger().warning("No webhook URLs found. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        return true;
    }

    @Override
    public void onDisable() {
        getLogger().info("LuckPermsWebhook plugin has been disabled.");
    }

    private void onNodeAdd(NodeAddEvent event) {
        Node node = event.getNode();
        String targetUser = event.getTarget().getFriendlyName();
        String message = "**Permission Added** for " + targetUser + "( " + node.getKey() + " = " + node.getValue() + "@" + node.getContexts().toString() + ")";
        if (event.getNode().getKey().contains("*"))
            message = "@everyone " + message;
        sendToDiscord(message);
    }

    private void onLogEvent(LogReceiveEvent event) {
        String targetUser = event.getEntry().getTarget().getName() + " (" + event.getEntry().getTarget().getUniqueId() + ")";
        String message = "**Permission Remotely Modified** for " + targetUser + ": " + event.getEntry().getDescription() + " via " + event.getEntry().getSource().getName();
        if (event.getEntry().getDescription().contains("*"))
            message = "@everyone " + message;
        sendToDiscord(message);
    }

    private void onNodeRemove(NodeRemoveEvent event) {
        Node node = event.getNode();
        String targetUser = event.getTarget().getFriendlyName();
        String message = "**Permission Removed** from " + targetUser + " : " + node.getKey();
        if (event.getNode().getKey().contains("*"))
            message = "@everyone " + message;
        sendToDiscord(message);
    }

    private void onNodeClear(NodeClearEvent event) {
        String targetUser = event.getTarget().getFriendlyName();
        StringBuilder message = new StringBuilder("**All Permissions Cleared** for " + targetUser);
        for (Node node : event.getNodes())
            message.append("\n ").append(node.getKey()).append(" = ").append(node.getValue());
        sendToDiscord(message.toString());
    }

    private void sendToDiscord(String message) {
        for(String webhook_url : webhookUrls) {
            sendToDiscord(webhook_url, message);
        }
    }

    private void sendToDiscord(String webhook_url, String message) {
        try {
            String jsonPayload = "{\"content\": \"" + message + "\"}";
            URL url = new URL(webhook_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            try {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                if (os != null)
                    os.close();
            } catch (Throwable throwable) {
                if (os != null)
                    try {
                        os.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                throw throwable;
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == 204) {
                getLogger().info("Message successfully sent to Discord.");
            } else {
                getLogger().warning("Failed to send message to Discord. Response code: " + responseCode);
            }
        } catch (Exception e) {
            getLogger().severe("An error occurred while sending the message to Discord.");
            e.printStackTrace();
        }
    }
}
