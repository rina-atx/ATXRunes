# ATXRunes

ATXRunes is a Bukkit/Paper plugin that adds passive player runes backed by ATXCore.

## Features

- `/runes` command with subcommands for slots, storage, forging, re-forging, trading, admin give, and reload.
- GUI for equipping and unequipping passive runes.
- GUI for forging random tier 1 runes.
- GUI for re-forging stored runes into higher tiers from 1 to 7.
- GUI rune storage.
- Two-sided rune trading GUI with offers and accept confirmation.
- Passive rune effects while equipped in rune slots, using ATXCore mechanics.
- Per-player rune data stored in `plugins/ATXRunes/data/<uuid>.yml`.
- Editable rune definitions in `plugins/ATXRunes/rune-types.yml`.
- Editable GUI titles, sizes, slots, button materials, lore, and custom model data in `config.yml`.
- Text supports `&` color codes, `&#RRGGBB`, `<#RRGGBB>`, and common MiniMessage-style tags like `<red>` and `<bold>`.
- Required `ATXCore` dependency declared in `plugin.yml`.

## Commands

- `/runes` opens the main menu.
- `/runes slots` opens the passive rune slots menu.
- `/runes forge` opens the forging menu.
- `/runes reforge` opens the re-forging menu.
- `/runes storage` opens rune storage.
- `/runes trade <player>` opens the trade selector.
- `/runes give <player> <type> <tier>` gives a rune. Requires `atxrunes.admin`.
- `/runes reload` reloads config and data. Requires `atxrunes.admin`.

## Rune Types and Effects

Rune types are configured in `plugins/ATXRunes/rune-types.yml`.

ATXRunes uses an Eco-style effects section backed by ATXCore mechanics. Each effect has an `id`, a `data` or `args` section, and optional `triggers`, `filters`, `mutators`, and `conditions`.

Use either `data:` or `args:` in `rune-types.yml`. `data:` matches ATXCore-style examples, while `args:` remains supported for older configs.

Effects with no `triggers` are permanent while the rune is equipped:

```yaml
runes:
  VITALITY:
    enabled: true
    display-name: "<red>Rune of Vitality"
    item:
      material: HEART_OF_THE_SEA
      custom-model-data: 0
    description:
      - "<gray>Increases maximum health by <red>%health%<gray>."
    placeholders:
      health: 2 + (2 * %tier%)
    effects:
      - id: add_max_health
        data:
          amount: "%health%"
```

Triggered effects put `triggers` under the effect:

```yaml
runes:
  MY_RUNE:
    enabled: true
    display-name: "<#66CCFF>My Rune"
    item:
      material: AMETHYST_SHARD
      custom-model-data: 1001
    description:
      - "<gray>Runs an ATXCore effect."
    base-value: 1.0
    placeholders:
      damage: 4 * %tier%
      cooldown: 20 - %level%
    effects:
      - id: damage
        data:
          amount: "%damage%"
          chance: 25
          cooldown: "%cooldown%"
        triggers:
          - melee_attack
        filters:
          not_entities:
            - creeper
        mutators:
          - id: target_victim
        conditions:
          - id: actor_health_above
            data:
              amount: 10
```

ATXRunes supports these Eco-style optional effect args:

- `chance`: percent chance from `0` to `100`.
- `cooldown`: seconds before this effect can run again.
- `cooldown_group`: shared cooldown key.
- `delay`: ticks before running the effect.
- `repeat.times` and `repeat.interval`: repeat the effect.

Forged runes start at tier 1. Re-forging upgrades a rune by one tier until tier 7. Power scaling is configured manually in `rune-types.yml` through placeholders, so every rune can scale differently.

## Math Equations And Common Placeholders

ATXRunes resolves placeholders inside rune lore, custom placeholders, effect `data`/`args`, conditions, filters, and mutators before ATXCore runs the mechanic.

Common built-in placeholders:

- `%tier%` / `{tier}`: the rune tier from 1 to 7.
- `%level%` / `{level}`: alias for tier.
- `%rune_tier%` / `{rune_tier}`: alias for tier.
- `%rune_level%` / `{rune_level}`: alias for tier.
- `%max_tier%` / `{max_tier}`: maximum rune tier, currently 7.
- `{value}`: `base-value * tier`.
- `{value_int}`: rounded `{value}`.
- `%value%` / `%v%`: Eco-style aliases for `{value}`.
- `{base_value}`
- `{rune_type}`
- `{rune_id}`

Custom placeholders defined under a rune's `placeholders:` section become available as both `%name%` and `{name}`.

Math equations support:

- `+`, `-`, `*`, `/`
- Parentheses, such as `(2 + %tier%) * 3`
- Decimal values, such as `0.01 * %tier%`
- Negative values, such as `10 - %tier%`

If the resolved value is only a math expression, ATXRunes evaluates it and passes a number to ATXCore. If it contains normal text, ATXRunes leaves it as text after placeholder replacement.

Examples:

```yaml
placeholders:
  damage: 4 * %tier%
  cooldown: 20 - %level%
  speed: 0.01 * %tier%
  amplifier: (%tier% - 1) / 3
```

```yaml
effects:
  - id: damage
    data:
      amount: "%damage%"
      cooldown: "%cooldown%"
```

Each rune can define custom placeholders:

```yaml
runes:
  SAMPLE:
    enabled: true
    display-name: "<red>Rune of Sample"
    item:
      material: IRON_SWORD
    base-value: 2.0
    placeholders:
      cooldown: 20 * %level%
      damage: 4 * %tier%
    description:
      - "<gray>Damage: <red>%damage%"
      - "<gray>Cooldown: <yellow>%cooldown% ticks"
    effects:
      - id: damage
        data:
          amount: "%damage%"
          cooldown: "%cooldown%"
```

Use quotes around equations in YAML when the expression contains symbols that YAML might parse oddly, especially `%`, `*`, or parentheses.

Supported triggers currently fired by ATXRunes:

- `melee_attack`
- `entity_damage`
- `entity_damage_by_entity`
- `block_break`
- `block_place`
- `player_interact`

Legacy-style trigger aliases are also accepted and normalized:

- `join` -> `player_join`
- `leave` -> `player_quit`
- `take_damage` -> `entity_damage`
- `take_entity_damage` -> `entity_damage_by_entity`
- `mine_block` -> `block_break`
- `place_block` -> `block_place`
- `click_block` / `alt_click` -> `player_interact`

For all available ATXCore mechanics, check ATXCore's generated guide or run `/atxcore registry`.

## Tutorials

### 1. Permanent Passive Rune

Permanent runes have no `triggers`. ATXRunes refreshes them while the rune is equipped.

Passive potion rune:

```yaml
runes:
  HASTE:
    enabled: true
    display-name: "<yellow>Rune of Haste"
    item:
      material: GOLDEN_PICKAXE
      custom-model-data: 1001
    description:
      - "<gray>Refreshes haste while equipped."
    base-value: 0
    effects:
      - id: add_potion
        data:
          type: FAST_DIGGING
          duration: 900
          amplifier: "%tier% - 1"
        filters:
          - id: player_only
```

### 2. Triggered Reward Rune

Triggered runes run when ATXRunes receives a matching event. Prefer the current ATXCore trigger IDs such as `block_break`, `block_place`, and `melee_attack`.

Block-break reward rune:

```yaml
runes:
  MINING_REWARD:
    enabled: true
    display-name: "<green>Rune of Paydirt"
    item:
      material: EMERALD
    description:
      - "<gray>Sometimes pays money when mining stone."
    base-value: 25
    effects:
      - id: vault_deposit
        data:
          amount: "25 * %tier%"
          chance: 10
        triggers:
          - block_break
        filters:
          blocks:
            - stone
      - id: action_bar
        data:
          message: "<gold>Rune reward triggered."
        triggers:
          - block_break
        filters:
          blocks:
            - stone
```

### 3. Targeting A Victim

Damage triggers start with useful actor and target data. Use `target_victim` when the effect should hit the entity that was attacked.

```yaml
runes:
  BLEEDING_EDGE:
    enabled: true
    display-name: "<red>Rune of Bleeding Edge"
    item:
      material: IRON_SWORD
    base-value: 3
    effects:
      - id: damage
        data:
          amount: "3 * %tier%"
          chance: 30
          cooldown: 4
        triggers:
          - melee_attack
        mutators:
          - id: target_victim
        filters:
          not_entities:
            - creeper
```

### 4. Full ATXCore Step Filters

Shortcut filters such as `blocks`, `entities`, and `not_entities` are useful for common rune checks. For any ATXCore filter, use list-style steps with `id` and `args`.

```yaml
runes:
  CREEPER_SAFE:
    enabled: true
    display-name: "<green>Creeper Safe Rune"
    item:
      material: EMERALD
    base-value: 4
    effects:
      - id: damage
        data:
          amount: "4 * %tier%"
        triggers:
          - melee_attack
        mutators:
          - id: target_victim
        filters:
          - id: not_target_entity_type
            data:
              type: "minecraft:creeper"
```

The default file includes working examples for simple permanent effects, triggered effects, filters, mutators, chance, and cooldown-style args.

## Build

Run:

```bash
mvn clean package
```

The plugin jar is created at `target/ATXRunes.jar`.
