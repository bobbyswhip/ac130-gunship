package com.founderfinder.ac130gunship;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class AC130Overlay extends Overlay
{
	private static final Color THERMAL_BG = new Color(0, 20, 0, 160);
	private static final Color SCANLINE = new Color(0, 255, 0, 15);
	private static final Color CROSSHAIR_COLOR = new Color(200, 255, 200, 180);
	private static final Color HUD_GREEN = new Color(0, 255, 0, 220);
	private static final Color TARGET_RED = new Color(255, 50, 50, 150);
	private static final Font HUD_FONT = new Font("Courier New", Font.BOLD, 14);
	private static final Font HUD_LARGE = new Font("Courier New", Font.BOLD, 18);
	private static final Font POPUP_FONT = new Font("Arial", Font.BOLD, 16);
	private static final Font KILL_FEED_FONT = new Font("Arial", Font.BOLD, 12);

	private final Client client;
	private final AC130GunshipPlugin plugin;

	@Inject
	public AC130Overlay(Client client, AC130GunshipPlugin plugin)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		AC130Manager mgr = plugin.getAc130Manager();
		if (mgr == null || !mgr.isActive())
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = client.getCanvasWidth();
		int h = client.getCanvasHeight();

		drawThermalBackground(g, w, h);
		drawPlayerHeat(g);
		drawScorePopups(g, mgr);
		drawScreenFlash(g, mgr, w, h);
		drawCrosshair(g);
		drawHUD(g, mgr, w, h);
		drawKillFeed(g, mgr, w);

		return null;
	}

	private void drawThermalBackground(Graphics2D g, int w, int h)
	{
		g.setColor(THERMAL_BG);
		g.fillRect(0, 0, w, h);

		g.setColor(SCANLINE);
		for (int y = 0; y < h; y += 3)
		{
			g.drawLine(0, y, w, y);
		}
	}

	private void drawPlayerHeat(Graphics2D g)
	{
		AC130Manager mgr = plugin.getAc130Manager();

		for (Player player : client.getPlayers())
		{
			if (player == null)
			{
				continue;
			}

			boolean isLocal = (player == client.getLocalPlayer());

			// Dying players: show death animation phase, then hide
			if (mgr != null && mgr.isPlayerDying(player))
			{
				if (!isLocal && mgr.isPlayerAnimatingDeath(player))
				{
					Shape hull = player.getConvexHull();
					if (hull != null)
					{
						g.setColor(new Color(255, 80, 0, 40));
						g.setStroke(new BasicStroke(8));
						g.draw(hull);

						g.setColor(new Color(255, 60, 0, 150));
						g.fill(hull);

						g.setColor(new Color(255, 40, 0, 80));
						g.setStroke(new BasicStroke(2));
						g.draw(hull);
					}
				}
				continue;
			}

			Shape hull = player.getConvexHull();
			if (hull == null)
			{
				continue;
			}

			// Outer glow
			g.setColor(new Color(255, 255, 255, isLocal ? 20 : 40));
			g.setStroke(new BasicStroke(8));
			g.draw(hull);

			// Thermal body fill
			g.setColor(new Color(255, 255, 255, isLocal ? 80 : 170));
			g.fill(hull);

			// Hot edge
			g.setColor(new Color(255, 255, 255, isLocal ? 40 : 100));
			g.setStroke(new BasicStroke(2));
			g.draw(hull);

			// Red targeting box on enemies (MW2 style)
			if (!isLocal)
			{
				Rectangle bounds = hull.getBounds();
				int pad = 6;
				g.setColor(TARGET_RED);
				g.setStroke(new BasicStroke(1));
				g.drawRect(
					bounds.x - pad, bounds.y - pad,
					bounds.width + pad * 2, bounds.height + pad * 2
				);
			}
		}

		g.setStroke(new BasicStroke(1));
	}

	private void drawScorePopups(Graphics2D g, AC130Manager mgr)
	{
		g.setFont(POPUP_FONT);
		FontMetrics fm = g.getFontMetrics();

		for (AC130Manager.ScorePopup popup : mgr.getScorePopups())
		{
			Point canvasPoint = Perspective.getCanvasTextLocation(
				client, g, popup.location, popup.text, 150);
			if (canvasPoint == null)
			{
				continue;
			}

			float progress = popup.getProgress();
			int yOffset = (int) (progress * -50);
			int alpha = Math.max(0, Math.min(255, (int) (255 * (1.0f - progress))));

			int tx = canvasPoint.getX() - fm.stringWidth(popup.text) / 2;
			int ty = canvasPoint.getY() + yOffset;

			g.setColor(new Color(0, 0, 0, alpha));
			g.drawString(popup.text, tx + 2, ty + 2);

			g.setColor(new Color(255, 255, 255, alpha));
			g.drawString(popup.text, tx, ty);
		}
	}

	private void drawScreenFlash(Graphics2D g, AC130Manager mgr, int w, int h)
	{
		long flashAge = System.currentTimeMillis() - mgr.getLastFlashTimeMs();
		if (flashAge >= 0 && flashAge < 500)
		{
			float flashProgress = flashAge / 500f;
			int alpha = Math.max(0, Math.min(255, (int) (180 * (1.0f - flashProgress))));
			g.setColor(new Color(255, 255, 255, alpha));
			g.fillRect(0, 0, w, h);
		}
	}

	private void drawCrosshair(Graphics2D g)
	{
		Point mouse = client.getMouseCanvasPosition();
		int mx = mouse.getX();
		int my = mouse.getY();

		g.setColor(CROSSHAIR_COLOR);
		g.setStroke(new BasicStroke(1.5f));

		g.drawOval(mx - 60, my - 60, 120, 120);
		g.drawOval(mx - 30, my - 30, 60, 60);

		g.drawLine(mx - 80, my, mx - 10, my);
		g.drawLine(mx + 10, my, mx + 80, my);
		g.drawLine(mx, my - 80, mx, my - 10);
		g.drawLine(mx, my + 10, mx, my + 80);

		g.fillOval(mx - 2, my - 2, 4, 4);

		g.setStroke(new BasicStroke(1));
		for (int i = 20; i <= 60; i += 20)
		{
			g.drawLine(mx - i, my - 4, mx - i, my + 4);
			g.drawLine(mx + i, my - 4, mx + i, my + 4);
			g.drawLine(mx - 4, my - i, mx + 4, my - i);
			g.drawLine(mx - 4, my + i, mx + 4, my + i);
		}
	}

	private void drawHUD(Graphics2D g, AC130Manager mgr, int w, int h)
	{
		int margin = 20;

		g.setFont(HUD_FONT);
		g.setColor(HUD_GREEN);
		g.drawString("WHOT", margin, margin + 14);

		g.setFont(HUD_LARGE);
		AC130Weapon weapon = mgr.getCurrentWeapon();
		g.drawString(weapon.getDisplayName(), margin, margin + 36);

		g.setFont(HUD_FONT);
		int currentAmmo = mgr.getCurrentAmmo();
		int maxAmmo = mgr.getCurrentMaxAmmo();

		int barX = margin;
		int barY = margin + 44;
		int barWidth = 150;
		int barHeight = 10;

		g.setColor(new Color(0, 100, 0, 150));
		g.fillRect(barX, barY, barWidth, barHeight);

		float ammoPercent = (float) currentAmmo / maxAmmo;
		Color ammoColor = ammoPercent > 0.3f ? HUD_GREEN : new Color(255, 50, 50, 220);
		g.setColor(ammoColor);
		g.fillRect(barX, barY, (int) (barWidth * ammoPercent), barHeight);

		g.setColor(CROSSHAIR_COLOR);
		g.drawRect(barX, barY, barWidth, barHeight);

		g.setColor(HUD_GREEN);
		g.drawString(currentAmmo + ":" + maxAmmo, barX + barWidth + 8, barY + 10);

		int selectorY = barY + 22;
		AC130Weapon[] weapons = AC130Weapon.values();
		for (int i = 0; i < weapons.length; i++)
		{
			boolean selected = weapons[i] == weapon;
			String label = "[" + (i + 1) + "] " + weapons[i].getDisplayName();
			g.setColor(selected ? HUD_GREEN : new Color(0, 150, 0, 80));
			g.drawString(label, margin, selectorY + (i * 16));
		}

		Font killFont = new Font("Courier New", Font.BOLD, 26);
		g.setFont(killFont);
		String killsText = "KILLS: " + mgr.getKills();
		FontMetrics killFm = g.getFontMetrics();
		int killX = (w - killFm.stringWidth(killsText)) / 2;
		g.setColor(new Color(0, 0, 0, 180));
		g.drawString(killsText, killX + 2, margin + 22);
		g.setColor(HUD_GREEN);
		g.drawString(killsText, killX, margin + 20);

		g.setFont(HUD_FONT);
		String fireStatus = mgr.canFire() ? "READY" : "COOLDOWN";
		Color fireColor = mgr.canFire() ? HUD_GREEN : new Color(255, 50, 50, 220);
		g.setColor(fireColor);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(fireStatus, w - margin - fm.stringWidth(fireStatus), margin + 18);

		g.setFont(HUD_LARGE);
		g.setColor(HUD_GREEN);
		String bottomText = "AC-130 ABOVE";
		fm = g.getFontMetrics();
		g.drawString(bottomText, (w - fm.stringWidth(bottomText)) / 2, h - margin);

		g.setFont(HUD_FONT);
		g.setColor(new Color(0, 200, 0, 100));
		g.drawString("ALT: Exit | 1/2/3: Switch", margin, h - margin);
	}

	private void drawKillFeed(Graphics2D g, AC130Manager mgr, int w)
	{
		java.util.List<AC130Manager.KillFeedEntry> feed = mgr.getKillFeed();
		if (feed.isEmpty())
		{
			return;
		}

		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		int vh = client.getViewportHeight();

		g.setFont(KILL_FEED_FONT);
		FontMetrics fm = g.getFontMetrics();
		int lineHeight = fm.getHeight() + 5;
		int margin = 14;
		long now = System.currentTimeMillis();

		int bottomY = vy + vh - 40;

		for (int i = 0; i < feed.size(); i++)
		{
			AC130Manager.KillFeedEntry entry = feed.get(i);
			long age = now - entry.createdMs;

			float fadeIn = Math.min(1f, age / 300f);
			float fadeOut = entry.getAlpha();
			float alpha = fadeIn * fadeOut;
			if (alpha <= 0.01f)
			{
				continue;
			}

			float slideT = Math.min(1f, age / 300f);
			slideT = 1f - (1f - slideT) * (1f - slideT);
			int xSlide = (int) ((1f - slideT) * -40);

			float animatedSlot = i;
			if (i > 0)
			{
				long newerAge = now - feed.get(i - 1).createdMs;
				if (newerAge < 250)
				{
					float pushT = newerAge / 250f;
					pushT = pushT * pushT * (3f - 2f * pushT);
					animatedSlot = (i - 1) + pushT;
				}
			}

			int x = vx + margin + xSlide;
			int y = bottomY - (int) (animatedSlot * lineHeight);

			int a = (int) (alpha * 255);
			String text = entry.killerName + " killed " + entry.victimName;

			g.setColor(new Color(0, 0, 0, (int) (alpha * 180)));
			g.drawString(text, x + 1, y + 1);

			g.setColor(new Color(255, 255, 255, a));
			g.drawString(text, x, y);
		}
	}
}
