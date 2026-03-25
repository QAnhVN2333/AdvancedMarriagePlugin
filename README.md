# AdvancedMarriage

AdvancedMarriage is a modular marriage plugin for Spigot/Paper servers (API 1.16+, Java 17).

It focuses on practical gameplay features (marriage requests, shared home/chest, partner tools), clean configuration, and flexible database support for both single-server and cross-server setups.

## Features

- Player-to-player marriage flow with request timeout and accept/deny/cancel actions.
- Priest mode to marry two players directly.
- Divorce flow with confirmation, cooldown, and shared chest safety checks.
- Shared home system: set, teleport, and remove home.
- Partner teleport with countdown and partner opt-in toggle.
- Shared chest with Base64 inventory storage and optional cross-server locking.
- Partner chat toggle and partner online/offline status notifications.
- Partner "seen" command (online or offline time estimate).
- Couple list GUI with pagination and display avatar selection.
- Partner PvP toggle.
- Revenge system with configurable radar, buffs, and rewards.
- Config and messages live reload with YAML default merge support.

## Compatibility

- Server software: Spigot / Paper (and compatible forks)
- Minecraft API: `1.16+`
- Java: `17+`

## Installation

1. Build or download the plugin jar.
2. Place the jar in your server `plugins/` directory.
3. Start the server once to generate default files.
4. Edit `plugins/AdvancedMarriage/config.yml` and `plugins/AdvancedMarriage/messages.yml` as needed.
5. Restart the server (or use `/marry reload` for message/config changes).

## Commands

Main command: `/marry`  
Aliases: `/am`, `/marriage`

| Command | Description |
| --- | --- |
| `/marry list` | Open the couples GUI list. |
| `/marry display <player>` | Set couple display avatar (self or partner). |
| `/marry marry <player>` | Send a marriage request. |
| `/marry marry <player1> <player2>` | Priest command: instantly marry two players. |
| `/marry accept [player]` | Accept an incoming request. |
| `/marry deny [player]` | Deny an incoming request. |
| `/marry cancel` | Cancel your outgoing request. |
| `/marry divorce confirm` | Confirm divorce. |
| `/marry chat` | Toggle partner chat. |
| `/marry home` | Teleport to shared home. |
| `/marry sethome` | Set shared home. |
| `/marry delhome` | Delete shared home. |
| `/marry tp` | Teleport to partner (if allowed). |
| `/marry tptoggle` | Toggle whether your partner can teleport to you. |
| `/marry pvptoggle` | Toggle PvP between partners. |
| `/marry chest` | Open shared chest. |
| `/marry seen` | Check partner online status / last seen. |
| `/marry reload` | Reload config and messages. |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `advancedmarriage.admin` | `op` | Allows `/marry reload`. |
| `advancedmarriage.priest` | `op` | Allows priest marriage command (`/marry marry <player1> <player2>`). |
| `advancedmarriage.home` | `true` | Allows home-related commands. |
| `advancedmarriage.tp` | `true` | Allows `/marry tp`. |
| `advancedmarriage.chest` | `true` | Allows `/marry chest`. |

## Configuration Highlights

### Core settings (`config.yml`)

- `server-id`: Unique server identifier (important for shared database and cross-server behavior).
- `features.home`, `features.tp`, `features.chest`, `features.pvp_toggle`: Enable/disable feature modules.
- `defaults.chest_size`: Shared chest size (must be a multiple of 9).
- `defaults.divorce_cooldown_minutes`: Cooldown after divorce before re-marry.
- `defaults.marry_request_timeout_seconds`: Marriage request expiration.
- `defaults.teleport_countdown_seconds`: Countdown for `/marry tp` and `/marry home`.
- `world_filters.*`: World whitelist/blacklist per feature and global command blocking.

### Database

Supported database types via `db.type`:

- `sqlite` (default, local file)
- `mysql`
- `mariadb`
- `postgres`

Notes:

- SQLite is simple and local (`advancedmarriage.db` in plugin folder).
- Remote databases use HikariCP pool settings from `db.pool-settings`.
- MySQL driver properties are configurable under `db.property` (SSL, encoding, timezone, etc.).

### Optional economy cost

`marry_cost` can require payment for marriage requests.

- `currency: Vault` or `PlayerPoints`
- If `Vault` is selected but Vault is missing, the plugin auto-disables `marry_cost.enabled`.

### Revenge system

The `revenge` section supports:

- Time-limited revenge targets
- Distance-based radar warnings
- Configurable potion effect tiers
- Optional reward commands/messages when revenge is completed

## Optional Integrations

The plugin soft-depends on:

- Vault
- PlayerPoints

They are optional and only required if you enable related features (such as marriage cost with that provider).

## Build From Source

```bash
mvn clean package
```

Output jar:

- `target/advancedmarriage-x.x.x.jar`
