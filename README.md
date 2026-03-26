# AC-130 Gunship

A RuneLite plugin that adds a MW2-style AC-130 killstreak mode to Old School RuneScape. Activate the gunship to enter a thermal vision overlay with an orbiting camera, crosshair targeting system, and three selectable weapons that fire projectiles at other players.

**This is a client-side visual effect only.** No actual damage is dealt and no game rules are broken. Other players cannot see your AC-130 effects. It is purely cosmetic entertainment for streamers and content creators.

## Features

- **Thermal Vision Overlay** - Green-tinted night vision with scanline effects and white-hot player highlighting
- **Orbiting Camera** - Automatic slow orbit around your character at a top-down angle, simulating an aerial gunship
- **Three Weapons**
  - **25mm Cannon** - Rapid-fire automatic, 100 rounds, small blast radius
  - **40mm Bofors** - Medium fire rate, 20 rounds, medium blast radius with screen flash
  - **105mm Howitzer** - Slow-firing heavy cannon, 8 rounds, large blast radius with screen flash and smoke
- **Projectile System** - Visible projectiles descend from altitude to the targeted tile with synced travel time
- **Ground Explosions** - Impact effects with explosion graphics and smoke clouds at the landing point
- **Kill Detection** - Players caught in the blast radius play a death animation, then fade from the thermal view
- **Kill Feed** - Animated bottom-left kill feed with slide-in and fade-out transitions
- **Score Popups** - Floating "+100 KILL" text above eliminated players
- **HUD Elements** - Weapon name, ammo bar, weapon selector, kill counter, fire readiness indicator
- **Crosshair** - Dual-circle targeting reticle with range tick marks that follows your mouse
- **Sound Effects** - Distinct fire and impact sounds for each weapon

## Controls

| Key | Action |
|-----|--------|
| `Alt` | Toggle AC-130 mode on/off |
| `1` | Switch to 25mm Cannon |
| `2` | Switch to 40mm Bofors |
| `3` | Switch to 105mm Howitzer |
| `Left Click` | Fire weapon (hold for 25mm auto-fire) |

You can also toggle from the side panel button.

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Enabled | Allow AC-130 activation via Alt key or panel | `true` |
| Show Chat Messages | Display activation/deactivation messages in chat | `true` |

## Installation

### From the Plugin Hub
Search for **AC-130 Gunship** in the RuneLite Plugin Hub.

### Manual / Sideloading
1. Clone this repository
2. Build with `./gradlew build -x test`
3. Copy the jar from `build/libs/` to `~/.runelite/sideloaded-plugins/`

## Building from Source

```bash
git clone https://github.com/bobbyswhip/ac130-gunship.git
cd ac130-gunship
./gradlew build
```

Requires Java 11+.

## How It Works

When activated, the plugin:
1. Locks the camera to a top-down pitch and begins a slow yaw orbit
2. Renders a thermal vision overlay on top of the game scene
3. Highlights all visible players as white-hot thermal signatures with red targeting boxes
4. Consumes all menu click events so your character stays stationary
5. On left click, spawns a projectile from altitude to the cursor's tile position
6. After the travel delay, resolves the impact: plays sounds, spawns explosion graphics, and checks for players in the blast radius
7. Hit players get a death animation, spot animation, kill feed entry, and score popup

All effects are local to your client. Nothing is sent to the server or visible to other players.

## License

BSD 2-Clause License. See [LICENSE](LICENSE) for details.
