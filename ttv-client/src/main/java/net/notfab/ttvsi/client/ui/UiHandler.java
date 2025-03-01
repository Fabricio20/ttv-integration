package net.notfab.ttvsi.client.ui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.SocketState;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.events.NetworkStateEvent;
import net.notfab.ttvsi.client.models.events.TwitchAuthorizedEvent;
import net.notfab.ttvsi.client.models.events.TwitchWsStateEvent;
import net.notfab.ttvsi.client.network.NetworkAPI;
import net.notfab.ttvsi.client.twitch.TwitchAPI;
import net.notfab.ttvsi.client.twitch.TwitchOAuthManager;
import net.notfab.ttvsi.common.protocol.RoomMemberSyncEvent;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Component
public class UiHandler {

    private static final Color COLOR_RED = new Color(204, 53, 61);
    private static final Color COLOR_GREEN = new Color(32, 119, 32);

    private final DesignUI ui;
    private final TwitchOAuthManager oauth;
    private final NetworkAPI network;
    private final JMenuItem btnDestructive = new JMenuItem("Destructive Actions: Disabled");
    private final JMenuItem btnLogs = new JMenuItem("Debug Logs: Disabled");

    public UiHandler(TwitchOAuthManager oauth, NetworkAPI network) {
        this.oauth = oauth;
        this.network = network;

        this.ui = new DesignUI();
        this.render();
        this.ui.pack();
        this.ui.setVisible(true);
    }

    private void render() {
        this.ui.getLblTwitch().setForeground(COLOR_RED);
        this.ui.getLblTwitch().setText("Disconnected");

        this.ui.getBtnTwitch().setText("Connect to Twitch");
        this.ui.getBtnTwitch().addActionListener(this::onTwitchLink);

        this.ui.getLblNetwork().setForeground(COLOR_RED);
        this.ui.getLblNetwork().setText("Disconnected");

        this.ui.getBtnNetwork().setEnabled(false);
        this.ui.getBtnNetwork().setText("Connect to Network");
        this.ui.getBtnNetwork().addActionListener(this::onNetworkLink);

        // Network defaults
        this.ui.getTxtUrl().setText("wss://twitch.notfab.net/websocket");
        this.ui.getTxtUrl().setEchoChar((char) 0);
        this.ui.getTxtInvite().setText(UUID.randomUUID().toString());
        this.ui.getTxtInvite().setEchoChar((char) 0);

        // Populate menu
        JMenuBar menuBar = new JMenuBar();
        {
            JMenu menu = new JMenu("Twitch Menu");
            menu.add(this.btnDestructive);
            menuBar.add(menu);
            this.btnDestructive.addActionListener(this::onDestructive);
            JMenuItem cleanup = new JMenuItem("Disconnect");
            menu.add(cleanup);
            cleanup.addActionListener(this::onCleanup);
        }
        {
            JMenu menu = new JMenu("Logs");
            menu.add(this.btnLogs);
            menuBar.add(menu);
            this.btnLogs.addActionListener(this::onLogs);
        }
        this.ui.setJMenuBar(menuBar);
    }

    private void onCleanup(ActionEvent event) {
        log.info("Disconnecting Twitch");
        this.oauth.disconnect();
    }

    private void onLogs(ActionEvent event) {
        Logger logger = (Logger) LoggerFactory.getLogger("net.notfab");
        if (logger.getLevel() == null || logger.getLevel() == Level.INFO) {
            logger.setLevel(Level.DEBUG);
            this.btnLogs.setText("Debug Logs: Enabled");
            log.info("Debug logs activated");
        } else if (logger.getLevel() == Level.DEBUG) {
            logger.setLevel(Level.INFO);
            this.btnLogs.setText("Debug Logs: Disabled");
            log.info("Debug logs deactivated");
        }
    }

    private void onDestructive(ActionEvent event) {
        TwitchAPI api = this.oauth.getApi();
        api.getDestructive().set(!api.getDestructive().get());
        if (api.getDestructive().get()) {
            this.btnDestructive.setText("Destructive Actions: Enabled");
        } else {
            this.btnDestructive.setText("Destructive Actions: Disabled");
        }
        log.info("Destructive actions {}", api.getDestructive().get() ? "Enabled" : "Disabled");
    }

    private void onTwitchLink(ActionEvent actionEvent) {
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return;
        }
        try {
            desktop.browse(URI.create(this.oauth.getOAuthUrl()));
        } catch (IOException ex) {
            log.error("Failed to browse", ex);
        }
        log.info("Opened twitch for initial connection");
    }

    @SuppressWarnings("deprecation")
    private void onNetworkLink(ActionEvent actionEvent) {
        if (this.network.isConnecting()) {
            this.network.disconnect();
        } else {
            String url = this.ui.getTxtUrl().getText();
            if (url == null || url.isBlank()) {
                JOptionPane.showMessageDialog(this.ui, "Invalid server URL", "Invalid URL", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String invite = this.ui.getTxtInvite().getText();
            if (invite == null || invite.isBlank()) {
                JOptionPane.showMessageDialog(this.ui, "Invalid invite", "Invalid Invite", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log.info("Attempting connection to {} ({})", url, invite);
            this.network.connect(url, invite);
            this.ui.getLblNetwork().setText("Reconnecting...");
            this.ui.getBtnNetwork().setText("Disconnect");
        }
    }

    @EventListener
    public void onTwitchAuthorized(TwitchAuthorizedEvent event) {
        this.ui.getBtnTwitch().setEnabled(false);
        this.ui.getLblTwitch().setForeground(COLOR_GREEN);
        this.ui.getLblTwitch().setText("Authorized to " + event.profile().getChannelName());
        this.ui.getBtnNetwork().setEnabled(true);
    }

    @EventListener
    public void onTwitchState(TwitchWsStateEvent event) {
        if (event.state() == SocketState.CONNECTING) {
            this.ui.getLblTwitch().setForeground(COLOR_RED);
            this.ui.getLblTwitch().setText("Reconnecting...");
        } else if (event.state() == SocketState.CONNECTED) {
            this.ui.getLblTwitch().setForeground(COLOR_GREEN);
            TwitchProfile profile = this.oauth.getApi().getProfile();
            if (profile != null) {
                this.ui.getLblTwitch().setText("Connected (" + profile.getChannelName() + ")");
            } else {
                this.ui.getLblTwitch().setText("Connected (???)");
            }
        } else {
            this.ui.getLblTwitch().setForeground(COLOR_RED);
            this.ui.getLblTwitch().setText("Disconnected");
        }
    }

    @EventListener
    public void onNetworkState(NetworkStateEvent event) {
        if (event.state() == SocketState.CONNECTING) {
            this.ui.getLblNetwork().setForeground(COLOR_RED);
            this.ui.getLblNetwork().setText("Reconnecting...");
            this.ui.getBtnNetwork().setText("Disconnect");
            this.ui.setUsers(new HashSet<>());
            this.ui.getTxtUrl().setEnabled(false);
            this.ui.getTxtUrl().setEchoChar('*');
            this.ui.getTxtInvite().setEnabled(false);
            this.ui.getTxtInvite().setEchoChar('*');
        } else if (event.state() == SocketState.CONNECTED) {
            this.ui.getLblNetwork().setForeground(COLOR_GREEN);
            this.ui.getLblNetwork().setText("Successfully connected");
            this.ui.getBtnNetwork().setText("Disconnect");
            this.ui.getTxtUrl().setEnabled(false);
            this.ui.getTxtUrl().setEchoChar('*');
            this.ui.getTxtInvite().setEnabled(false);
            this.ui.getTxtInvite().setEchoChar('*');
        } else {
            this.ui.getLblNetwork().setForeground(COLOR_RED);
            this.ui.getLblNetwork().setText("Disconnected");
            this.ui.getBtnNetwork().setText("Connect to Network");
            this.ui.setUsers(new HashSet<>());
            this.ui.getTxtUrl().setEnabled(true);
            this.ui.getTxtUrl().setEchoChar((char) 0);
            this.ui.getTxtInvite().setEnabled(true);
            this.ui.getTxtInvite().setEchoChar((char) 0);
        }
    }

    @EventListener
    public void onMemberSync(RoomMemberSyncEvent event) {
        this.ui.setUsers(event.members());
    }

}
