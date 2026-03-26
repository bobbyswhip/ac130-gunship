package com.founderfinder.ac130gunship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;

@Slf4j
public class AC130Manager
{
	private static final int LOCAL_TILE_SIZE = 128;
	private static final int DEATH_ANIM = 836;
	private static final int HUGE_SMOKE_GFX = 189;
	private static final long ORBIT_MS_PER_JAU = 60;
	private static final long DEATH_ANIM_PHASE_MS = 2000;
	private static final long DEATH_EFFECT_DURATION_MS = 3500;
	private static final long POPUP_DURATION_MS = 2000;
	private static final long PENDING_FIRE_WINDOW_MS = 300;

	// Projectile params — starts from AC130 altitude, synced travel time
	private static final int TRAVEL_GFX = 1465;
	private static final int SRC_HEIGHT = 2200;
	private static final int SRC_OFFSET = 64;   // half-tile Y offset (slight angle)
	private static final int SLOPE = 15;         // low arc = steep vertical descent

	static class PendingImpact
	{
		LocalPoint target;
		AC130Weapon weapon;
		long impactTimeMs;
	}

	static class ScorePopup
	{
		final LocalPoint location;
		final String text;
		final long createdMs;

		ScorePopup(LocalPoint location, String text)
		{
			this.location = location;
			this.text = text;
			this.createdMs = System.currentTimeMillis();
		}

		float getProgress()
		{
			return Math.min(1.0f, (float) (System.currentTimeMillis() - createdMs) / POPUP_DURATION_MS);
		}

		boolean isExpired()
		{
			return System.currentTimeMillis() - createdMs >= POPUP_DURATION_MS;
		}
	}

	private static final long KILL_FEED_DURATION_MS = 5000;
	private static final int KILL_FEED_MAX = 6;

	static class KillFeedEntry
	{
		final String killerName;
		final String victimName;
		final long createdMs;

		KillFeedEntry(String killerName, String victimName)
		{
			this.killerName = killerName;
			this.victimName = victimName;
			this.createdMs = System.currentTimeMillis();
		}

		float getAlpha()
		{
			long age = System.currentTimeMillis() - createdMs;
			if (age > KILL_FEED_DURATION_MS - 1000)
			{
				return Math.max(0f, 1f - (float) (age - (KILL_FEED_DURATION_MS - 1000)) / 1000f);
			}
			return 1f;
		}

		boolean isExpired()
		{
			return System.currentTimeMillis() - createdMs >= KILL_FEED_DURATION_MS;
		}
	}

	private static class DyingPlayer
	{
		final Player player;
		final String playerName;
		final long deathTimeMs;

		DyingPlayer(Player player)
		{
			this.player = player;
			this.playerName = player.getName();
			this.deathTimeMs = System.currentTimeMillis();
		}

		boolean isExpired()
		{
			return System.currentTimeMillis() - deathTimeMs >= DEATH_EFFECT_DURATION_MS;
		}
	}

	private final Client client;

	@Getter
	private boolean active = false;

	@Getter
	private AC130Weapon currentWeapon = AC130Weapon.CANNON_25MM;

	@Getter
	private final int[] ammo = new int[3];

	private final long[] cooldownUntilMs = new long[3];

	@Getter
	private int kills = 0;

	@Getter
	private final List<PendingImpact> pendingImpacts = new ArrayList<>();

	@Getter
	private final List<ScorePopup> scorePopups = new ArrayList<>();

	@Getter
	private long lastFlashTimeMs = 0;

	@Getter
	private final List<KillFeedEntry> killFeed = new ArrayList<>();

	private final List<DyingPlayer> dyingPlayers = new ArrayList<>();

	// Permanent kill tracking by name — players stay hidden until AC130 deactivated
	// Uses player name instead of object reference since RuneLite recycles Player objects
	private final Set<String> killedPlayerNames = new HashSet<>();

	private LocalPoint lastKnownTarget;
	private boolean pendingFire = false;
	private long pendingFireTimeMs = 0;

	private int savedPitch;
	private int savedYaw;
	private long activatedTimeMs;
	private int startYaw;

	public AC130Manager(Client client)
	{
		this.client = client;
	}

	public void activate()
	{
		savedPitch = client.getCameraPitchTarget();
		savedYaw = client.getCameraYawTarget();
		startYaw = savedYaw;
		activatedTimeMs = System.currentTimeMillis();

		client.setCameraPitchTarget(512);

		for (AC130Weapon w : AC130Weapon.values())
		{
			ammo[w.ordinal()] = w.getMaxAmmo();
			cooldownUntilMs[w.ordinal()] = 0;
		}

		currentWeapon = AC130Weapon.CANNON_25MM;
		kills = 0;
		pendingImpacts.clear();
		scorePopups.clear();
		killFeed.clear();
		dyingPlayers.clear();
		killedPlayerNames.clear();
		lastKnownTarget = null;
		pendingFire = false;
		lastFlashTimeMs = 0;
		active = true;

		log.info("AC-130 activated");
	}

	public void deactivate()
	{
		active = false;

		// Reset animations on dying players before clearing
		for (DyingPlayer dp : dyingPlayers)
		{
			try
			{
				dp.player.setAnimation(-1);
			}
			catch (Exception ignored)
			{
			}
		}

		client.setCameraPitchTarget(savedPitch);
		client.setCameraYawTarget(savedYaw);

		pendingImpacts.clear();
		scorePopups.clear();
		killFeed.clear();
		dyingPlayers.clear();
		killedPlayerNames.clear();
		lastKnownTarget = null;
		pendingFire = false;

		log.info("AC-130 deactivated");
	}

	/**
	 * Returns true if the player is dead (either animating or permanently hidden).
	 * Used by the overlay to hide dead players from the heatmap.
	 */
	public boolean isPlayerDying(Player player)
	{
		String name = player.getName();
		if (name != null && killedPlayerNames.contains(name))
		{
			return true;
		}
		for (DyingPlayer dp : dyingPlayers)
		{
			// Match by object ref OR by name (object ref can go stale)
			if (dp.player == player || (name != null && name.equals(dp.playerName)))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the player is in the visible death animation phase (first 2s).
	 * After this phase they should be fully hidden.
	 */
	public boolean isPlayerAnimatingDeath(Player player)
	{
		String name = player.getName();
		long now = System.currentTimeMillis();
		for (DyingPlayer dp : dyingPlayers)
		{
			if (dp.player == player || (name != null && name.equals(dp.playerName)))
			{
				return now - dp.deathTimeMs < DEATH_ANIM_PHASE_MS;
			}
		}
		return false;
	}

	public void onClientTick()
	{
		if (!active)
		{
			return;
		}

		client.setCameraPitchTarget(512);

		long elapsed = System.currentTimeMillis() - activatedTimeMs;
		int orbitOffset = (int) (elapsed / ORBIT_MS_PER_JAU) % 2048;
		int currentYaw = (startYaw + orbitOffset) % 2048;
		client.setCameraYawTarget(currentYaw);

		Iterator<DyingPlayer> dIt = dyingPlayers.iterator();
		while (dIt.hasNext())
		{
			DyingPlayer dp = dIt.next();
			if (dp.isExpired())
			{
				try
				{
					dp.player.setAnimation(-1);
				}
				catch (Exception ignored)
				{
				}
				// Move to permanent kill set by name — stays hidden until AC130 deactivated
				if (dp.playerName != null)
				{
					killedPlayerNames.add(dp.playerName);
				}
				dIt.remove();
				continue;
			}
			try
			{
				dp.player.setAnimation(DEATH_ANIM);
			}
			catch (Exception e)
			{
				// Player object went stale — move to permanent kill set
				if (dp.playerName != null)
				{
					killedPlayerNames.add(dp.playerName);
				}
				dIt.remove();
			}
		}
	}

	public boolean isMouseInViewport()
	{
		Point mouse = client.getMouseCanvasPosition();
		int mx = mouse.getX();
		int my = mouse.getY();
		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		int vw = client.getViewportWidth();
		int vh = client.getViewportHeight();
		return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
	}

	/**
	 * All weapons fire through this method on every client tick.
	 * Tracks last valid tile so clicks over gaps/sky still fire.
	 * Buffers single-fire weapon clicks so quick clicks aren't missed.
	 */
	public void tryAutoFire()
	{
		if (!active)
		{
			return;
		}

		// Always track the last valid tile under the cursor
		Tile tile = client.getSelectedSceneTile();
		if (tile != null && tile.getLocalLocation() != null)
		{
			lastKnownTarget = tile.getLocalLocation();
		}

		boolean mouseDown = client.getMouseCurrentButton() == 1 && isMouseInViewport();

		if (mouseDown && lastKnownTarget != null)
		{
			if (currentWeapon.isAutoFire())
			{
				// Auto-fire weapons (25mm): fire every tick the button is held
				fire(lastKnownTarget);
			}
			else if (!pendingFire)
			{
				// Single-fire weapons (40mm, 105mm): buffer the click
				pendingFire = true;
				pendingFireTimeMs = System.currentTimeMillis();
			}
		}

		// Process buffered click — retries for PENDING_FIRE_WINDOW_MS until cooldown clears
		if (pendingFire && lastKnownTarget != null)
		{
			long now = System.currentTimeMillis();
			if (now - pendingFireTimeMs > PENDING_FIRE_WINDOW_MS)
			{
				pendingFire = false;
			}
			else if (canFire())
			{
				fire(lastKnownTarget);
				pendingFire = false;
			}
		}
	}

	public void onGameTick()
	{
		if (!active)
		{
			return;
		}

		long now = System.currentTimeMillis();

		Iterator<PendingImpact> pIt = pendingImpacts.iterator();
		while (pIt.hasNext())
		{
			PendingImpact p = pIt.next();
			if (now >= p.impactTimeMs)
			{
				resolveImpactAt(p.target, p.weapon);
				pIt.remove();
			}
		}

		scorePopups.removeIf(ScorePopup::isExpired);
		killFeed.removeIf(KillFeedEntry::isExpired);
	}

	public void switchWeapon(int index)
	{
		AC130Weapon[] weapons = AC130Weapon.values();
		if (index >= 0 && index < weapons.length)
		{
			currentWeapon = weapons[index];
		}
	}

	public void fire(LocalPoint target)
	{
		if (!active || target == null)
		{
			return;
		}

		int idx = currentWeapon.ordinal();
		long now = System.currentTimeMillis();

		if (now < cooldownUntilMs[idx] || ammo[idx] <= 0)
		{
			return;
		}

		ammo[idx]--;
		cooldownUntilMs[idx] = now + currentWeapon.getCooldownMs();

		// Fire sound
		client.playSoundEffect(currentWeapon.getFireSound());

		// Single fire surge projectile from above to target
		spawnProjectile(target, currentWeapon);

		// Schedule impact after delay
		PendingImpact p = new PendingImpact();
		p.target = target;
		p.weapon = currentWeapon;
		p.impactTimeMs = now + currentWeapon.getImpactDelayMs();
		pendingImpacts.add(p);
	}

	/**
	 * Projectile from AC130 altitude straight down to target.
	 * Flight cycles synced to weapon impact delay so explosion = projectile arrival.
	 */
	@SuppressWarnings("deprecation")
	private void spawnProjectile(LocalPoint target, AC130Weapon weapon)
	{
		try
		{
			int cycle = client.getGameCycle();
			// 1 client tick ≈ 20ms, sync flight time to impact delay
			int flightCycles = weapon.getImpactDelayMs() / 20;

			client.createProjectile(
				TRAVEL_GFX,
				client.getPlane(),
				target.getX(),
				target.getY() + SRC_OFFSET,
				SRC_HEIGHT,
				cycle,
				cycle + flightCycles,
				SLOPE,
				0, 0, null,
				target.getX(),
				target.getY()
			);
		}
		catch (Exception e)
		{
			log.warn("Projectile error: {}", e.getMessage());
		}
	}

	/**
	 * Spawn a ground-level explosion graphic at the impact point.
	 * Uses a short projectile starting just above the tile so the
	 * explosion graphic renders right at the landing spot.
	 */
	@SuppressWarnings("deprecation")
	private void spawnExplosionEffect(LocalPoint target, AC130Weapon weapon)
	{
		try
		{
			int cycle = client.getGameCycle();
			// Primary explosion — Galvek boom at ground level
			client.createProjectile(
				weapon.getImpactGfx(),
				client.getPlane(),
				target.getX(), target.getY(),
				50,            // start just above ground
				cycle,
				cycle + 50,    // ~1s display
				0,
				0, 0, null,
				target.getX(), target.getY()
			);

			// Smoke cloud on all weapons
			client.createProjectile(
				HUGE_SMOKE_GFX,
				client.getPlane(),
				target.getX(), target.getY(),
				10,
				cycle + 5,
				cycle + 60,
				0,
				0, 0, null,
				target.getX(), target.getY()
			);
		}
		catch (Exception e)
		{
			log.debug("Explosion effect error: {}", e.getMessage());
		}
	}

	private void resolveImpactAt(LocalPoint target, AC130Weapon weapon)
	{
		// Explosion sound
		client.playSoundEffect(weapon.getHitSound());

		// Ground explosion visual at impact tile
		spawnExplosionEffect(target, weapon);

		// Screen flash for 40mm and 105mm
		if (weapon == AC130Weapon.CANNON_40MM || weapon == AC130Weapon.HOWITZER_105MM)
		{
			lastFlashTimeMs = System.currentTimeMillis();
		}

		// Square hitbox: 1x1 / 2x2 / 3x3 tile area
		// halfWidth = blastRadius * 128 / 2 → 64 / 128 / 192
		int halfWidth = weapon.getBlastRadius() * LOCAL_TILE_SIZE / 2;
		for (Player player : client.getPlayers())
		{
			if (player == client.getLocalPlayer())
			{
				continue;
			}

			LocalPoint pLoc = player.getLocalLocation();
			if (pLoc == null)
			{
				continue;
			}

			int dx = Math.abs(pLoc.getX() - target.getX());
			int dy = Math.abs(pLoc.getY() - target.getY());

			if (dx <= halfWidth && dy <= halfWidth)
			{
				onPlayerHit(player, weapon);
			}
		}
	}

	private void onPlayerHit(Player player, AC130Weapon weapon)
	{
		// Skip if already dying
		if (isPlayerDying(player))
		{
			return;
		}

		try
		{
			player.setAnimation(DEATH_ANIM);
			player.setAnimationFrame(0);

			// Galvek boom as spot anim ON the player (this works)
			player.createSpotAnim(0, weapon.getImpactGfx(), 0, 0);

			if (weapon == AC130Weapon.HOWITZER_105MM)
			{
				player.createSpotAnim(1, HUGE_SMOKE_GFX, 0, 5);
			}

			DyingPlayer dp = new DyingPlayer(player);
			dyingPlayers.add(dp);

			// Immediately record the name so even if the Player object goes
			// stale before the death animation expires, the overlay still
			// knows to hide this player
			if (dp.playerName != null)
			{
				killedPlayerNames.add(dp.playerName);
			}
		}
		catch (Exception e)
		{
			log.debug("Hit effect error: {}", e.getMessage());
		}

		// Kill feed entry
		String victimName = player.getName();
		Player localPlayer = client.getLocalPlayer();
		String killerName = localPlayer != null ? localPlayer.getName() : "You";
		if (victimName != null)
		{
			killFeed.add(0, new KillFeedEntry(killerName != null ? killerName : "You", victimName));
			while (killFeed.size() > KILL_FEED_MAX)
			{
				killFeed.remove(killFeed.size() - 1);
			}
		}

		LocalPoint pLoc = player.getLocalLocation();
		if (pLoc != null)
		{
			scorePopups.add(new ScorePopup(pLoc, "+100 KILL"));
		}
		kills++;
	}

	public int getCurrentAmmo()
	{
		return ammo[currentWeapon.ordinal()];
	}

	public int getCurrentMaxAmmo()
	{
		return currentWeapon.getMaxAmmo();
	}

	public boolean canFire()
	{
		int idx = currentWeapon.ordinal();
		return System.currentTimeMillis() >= cooldownUntilMs[idx] && ammo[idx] > 0;
	}
}
