package com.founderfinder.ac130gunship;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AC130Weapon
{
	CANNON_25MM("25MM", 200, 2, 100, 1667, 510, 1467, true),
	CANNON_40MM("40MM", 600, 3, 20, 1487, 2299, 1467, false),
	HOWITZER_105MM("105MM", 3360, 5, 8, 1487, 2299, 1467, false);

	private final String displayName;
	private final int cooldownMs;
	private final int blastRadius;
	private final int maxAmmo;
	private final int fireSound;
	private final int hitSound;
	private final int impactGfx;
	private final boolean autoFire;

	public int getImpactDelayMs()
	{
		switch (this)
		{
			case CANNON_25MM: return 1000;
			case CANNON_40MM: return 1500;
			case HOWITZER_105MM: return 2000;
			default: return 1000;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
