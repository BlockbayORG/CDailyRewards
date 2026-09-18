# CDailyRewards

A 30-day daily rewards plugin for Paper 1.21+. Players claim one reward per day from a fully configurable calendar menu. Rewards escalate over the month, with big milestone days.

## Features

- **30-day calendar** (any length) with milestone days that have their own look and broadcast
- **Reward types:** commands, items, vouchers, Vault money, XP, messages, broadcasts, sounds, weighted random pools
- **Vouchers:** right-click to redeem, each with a unique ID so duplicated copies are refused
- **Configurable menu:** character-based layout, auto pages, custom buttons with actions, per-state day items, live countdown
- **Cooldown:** 24h after the last claim (default) or a fixed daily reset time with timezone
- **Missed days:** reset to day 1 (default), keep progress, pause, or go back N days, with optional grace hours
- **Extras:** playtime requirement, rank multipliers, full-inventory handling, per-reward chance and permission
- **Storage:** YAML or SQLite, all IO off the main thread
- **PlaceholderAPI** support

## Requirements

| Dependency | Required | Used for |
|---|---|---|
| Paper 1.21+ | Yes | Server |
| Java 21 | Yes | Runtime |
| Vault + economy plugin | No | `money` rewards |
| PlaceholderAPI | No | Placeholders in menus and messages; `%cdr_*%` placeholders |

## Installation

1. Put `CDailyRewards-<version>.jar` in `plugins/`.
2. Start the server. Config files are created in `plugins/CDailyRewards/`.
3. Edit `rewards.yml` and `vouchers.yml` to match your server (the examples use `crates` and `lp` commands).
4. Run `/cdr reload`.

## Building

```
mvn clean package
```

Output: `target/CDailyRewards-<version>.jar`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/daily` (aliases `/dailyrewards`, `/dr`) | `cdr.use` | Open the menu |
| `/daily claim` | `cdr.claim` | Claim without the menu |
| `/daily preview` | `cdr.preview` | View rewards without claiming |
| `/cdr reload` | `cdr.admin.reload` | Reload all config files |
| `/cdr reset <player>` | `cdr.admin.reset` | Reset a player's progress |
| `/cdr setday <player> <day>` | `cdr.admin.setday` | Set a player's current day |
| `/cdr skipcooldown <player>` | `cdr.admin.cooldown` | Let a player claim now |
| `/cdr give <player> <day>` | `cdr.admin.give` | Give a day's rewards (no progress change) |
| `/cdr voucher give <player> <id> [amount]` | `cdr.admin.voucher` | Give vouchers |
| `/cdr info <player>` | `cdr.admin.info` | Show a player's progress |

`reset`, `setday`, `skipcooldown` and `info` also work on offline players.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `cdr.use` | everyone | Open the menu |
| `cdr.claim` | everyone | Claim rewards |
| `cdr.preview` | everyone | Preview rewards |
| `cdr.admin` | op | All admin commands |
| `cdr.bypass.cooldown` | nobody | Claim without waiting |
| `cdr.bypass.playtime` | nobody | Skip the playtime requirement |
| `cdr.multiplier.<name>` | nobody | Reward multiplier from `config.yml` (highest wins) |

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Storage, cooldown, missed-day rules, multipliers, effects |
| `rewards.yml` | Rewards for each day |
| `vouchers.yml` | Voucher items and redeem actions |
| `menus/main.yml` | Menu layout and items |
| `messages.yml` | All messages |

Text supports [MiniMessage](https://docs.advntr.dev/minimessage/format) and legacy `&` codes.

### config.yml

| Option | Values | Default |
|---|---|---|
| `storage` | `YAML`, `SQLITE` (restart to change) | `YAML` |
| `days` | Number of days | `30` |
| `cooldown-mode` | `ROLLING_24H` (24h after claim), `FIXED_RESET` (daily reset time) | `ROLLING_24H` |
| `reset-time`, `timezone` | e.g. `"00:00"`, `"Europe/London"` (FIXED_RESET only) | |
| `on-miss` | `RESET`, `KEEP`, `PAUSE`, `GO_BACK_N` | `RESET` |
| `go-back-days` | Days lost with `GO_BACK_N` | `3` |
| `grace-hours` | Extra hours before a miss counts | `0` |
| `after-last-day` | `RESTART`, `LOOP_LAST`, `STOP` | `RESTART` |
| `require-playtime` | Minutes played since last claim (0 = off) | `0` |
| `full-inventory` | `DROP` (at feet), `DENY` (refuse claim) | `DROP` |
| `multipliers` | `name: factor`; scales money, XP, item and voucher amounts | |
| `join-notify` | Chat notice on join when a reward is ready | on |
| `claim-effects` | Sound and particle when claiming | |

**Missed day:** in `ROLLING_24H` mode, a day counts as missed when the player doesn't claim within 48h (+ grace) of the last claim. In `FIXED_RESET` mode, it counts as missed when a whole reset period passes without a claim.

### rewards.yml

```yaml
days:
  7:
    milestone: true                 # use milestone-states in the menu
    broadcast: "<yellow>%player% reached a 7 day streak!"
    icon: { material: NETHER_STAR } # optional: override the menu item
    rewards:
      - type: money
        amount: 1000                # or "500-1500" for a random range
      - type: voucher
        id: rare_key
        amount: 1
      - type: command
        as: console                 # or player
        command: "lp user %player% permission settemp tag.week1 true 7d"
        display-name: "<gold>Week 1 tag"
      - type: item
        chance: 25
        item:
          material: DIAMOND
          amount: 3
          name: "<aqua>Lucky Diamonds"
      - type: random-pool
        picks: 1
        unique: true
        pool:
          - { weight: 70, type: xp, levels: 5 }
          - { weight: 30, type: xp, levels: 15 }
```

**Reward types**

| Type | Options |
|---|---|
| `command` | `command`, `as: console\|player` |
| `item` | `item:` (see item format) |
| `voucher` | `id`, `amount` |
| `money` | `amount` (number or `min-max`) |
| `xp` | `levels` or `points` |
| `message` / `broadcast` | `text` |
| `sound` | `sound`, `volume`, `pitch` |
| `random-pool` | `picks`, `unique`, `pool` (each entry has a `weight`) |

**Options for every reward:** `chance` (0-100), `permission`, `display-name` (menu lore line), `display: false` (hide from menu), `multiply: false` (ignore multipliers).

Placeholders in rewards: `%player%`, `%uuid%`, `%day%`.

### Item format

Used by rewards, vouchers and menu items:

```yaml
material: DIAMOND_SWORD
amount: 1
name: "<red>Blade"
lore: ["<gray>Line one", "<gray>Line two"]
enchants: { sharpness: 5, unbreaking: 3 }
flags: [HIDE_ENCHANTS]
custom-model-data: 1001
glow: true
unbreakable: true
skull: "%player%"        # player heads only
```

### vouchers.yml

```yaml
rare_key:
  permission: ""          # optional permission needed to redeem
  item:
    material: TRIPWIRE_HOOK
    name: "<blue>Rare Key Voucher"
    lore: ["<gray>Right-click to redeem."]
    glow: true
  actions:
    - "[console] crates key give %player% rare 1"
    - "[message] <blue>You got a rare key!"
    - "[sound] ENTITY_PLAYER_LEVELUP 1 1"
```

Actions: `[console]`, `[player]`, `[message]`, `[broadcast]`, `[sound] NAME volume pitch`.

Redeemed voucher IDs are stored in `data/redeemed-vouchers.txt`. A duplicated voucher is removed on use and logged.

### menus/main.yml

```yaml
title: "<dark_gray>Daily Rewards <gray>(%page%/%pages%)"
update-interval: 20       # ticks between refreshes (live countdown)
open-sound: BLOCK_CHEST_OPEN
layout:                   # 1-6 rows of 9 chars; D = day slot
  - "#########"
  - "#DDDDDDD#"
  - "#DDDDDDD#"
  - "#DDDDDDD#"
  - "#DDDDDDD#"
  - "#<##I##>X"
items:
  "X":
    material: BARRIER
    name: "<red>Close"
    actions: ["[close]"]
```

- Day slots fill in order. If there are more days than `D` slots, the menu adds pages.
- Button actions: `[close]`, `[page] next|prev`, plus all voucher actions.
- `day-states` and `milestone-states` set the item for each state: `available`, `claimed`, `cooldown`, `locked`.
- Day item placeholders: `%day%`, `%rewards_lore%` (one line per reward), `%time_left%`.
- Menu placeholders: `%player%`, `%cdr_day%`, `%cdr_streak%`, `%cdr_total%`, `%time_left%`, `%page%`, `%pages%`, plus PlaceholderAPI.

## PlaceholderAPI

| Placeholder | Value |
|---|---|
| `%cdr_day%` | Current day |
| `%cdr_streak%` | Current streak |
| `%cdr_total_claims%` | Total claims |
| `%cdr_time_left%` | Time until next claim |
| `%cdr_can_claim%` | `true` / `false` |
| `%cdr_days%` | Days in the calendar |

## Data

- YAML: `plugins/CDailyRewards/data/players/<uuid>.yml`
- SQLite: `plugins/CDailyRewards/data.db`

Changing `storage` does not migrate existing data.
