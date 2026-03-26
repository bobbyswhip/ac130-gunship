package com.founderfinder.ac130gunship;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "AC-130 Gunship",
	description = "MW2-style AC-130 killstreak mode with camera control, auto-fire weapons, and kill feed overlay",
	tags = {"ac130", "gunship", "effects", "stream", "overlay", "fun"},
	enabledByDefault = true
)
public class AC130GunshipPlugin extends Plugin implements KeyListener
{
	@Inject
	private Client client;

	@Inject
	private AC130GunshipConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AC130Overlay ac130Overlay;

	@Getter
	private AC130Manager ac130Manager;

	private AC130GunshipPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		log.info("AC-130 Gunship plugin started");

		ac130Manager = new AC130Manager(client);
		panel = new AC130GunshipPanel(this, config);

		navButton = NavigationButton.builder()
			.tooltip("AC-130 Gunship")
			.icon(getIcon())
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(ac130Overlay);
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		log.info("AC-130 Gunship plugin stopped");
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(ac130Overlay);
		keyManager.unregisterKeyListener(this);

		if (ac130Manager != null && ac130Manager.isActive())
		{
			ac130Manager.deactivate();
		}
	}

	// ---- KeyListener ----

	@Override
	public void keyPressed(KeyEvent e)
	{
		// Alt toggles AC-130 mode (when enabled in config)
		if (e.getKeyCode() == KeyEvent.VK_ALT && config.ac130Enabled())
		{
			toggleAC130();
		}

		// 1/2/3 switch AC-130 weapons
		if (ac130Manager != null && ac130Manager.isActive())
		{
			if (e.getKeyCode() == KeyEvent.VK_1)
			{
				ac130Manager.switchWeapon(0);
			}
			else if (e.getKeyCode() == KeyEvent.VK_2)
			{
				ac130Manager.switchWeapon(1);
			}
			else if (e.getKeyCode() == KeyEvent.VK_3)
			{
				ac130Manager.switchWeapon(2);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	// ---- AC-130 Controls ----

	public void toggleAC130()
	{
		clientThread.invokeLater(() ->
		{
			if (ac130Manager.isActive())
			{
				ac130Manager.deactivate();
				if (config.showChatMessages() && client.getGameState() == GameState.LOGGED_IN)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"[AC-130] Gunship deactivated", null);
				}
			}
			else
			{
				ac130Manager.activate();
				if (config.showChatMessages() && client.getGameState() == GameState.LOGGED_IN)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"[AC-130] INCOMING!", null);
				}
			}
			panel.refreshStatus();
		});
	}

	// ---- Event Handlers ----

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// AC-130 mode: block ALL movement/combat (firing handled by tryAutoFire)
		if (ac130Manager != null && ac130Manager.isActive())
		{
			event.consume();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ac130Manager != null && ac130Manager.isActive())
		{
			ac130Manager.onGameTick();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (ac130Manager != null && ac130Manager.isActive())
		{
			ac130Manager.onClientTick();
			ac130Manager.tryAutoFire();
		}
	}

	// ---- Icon ----

	private BufferedImage getIcon()
	{
		int size = 24;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(new java.awt.Color(239, 68, 68));
		g.fillRoundRect(0, 0, size, size, 6, 6);

		g.setColor(java.awt.Color.WHITE);
		g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 11));
		java.awt.FontMetrics fm = g.getFontMetrics();
		String text = "AC";
		int tx = (size - fm.stringWidth(text)) / 2;
		int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(text, tx, ty);

		g.dispose();
		return img;
	}

	@Provides
	AC130GunshipConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AC130GunshipConfig.class);
	}
}
