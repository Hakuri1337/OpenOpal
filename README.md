# OpenOpal

OpenOpal is a free and open-source Fabric client mod for Minecraft 1.21.10.
It is based on Opal v2 and focuses on maintaining a usable modern client with
combat, movement, utility, visual, and world modules adapted for the current
codebase.

## Status

The project currently builds with:

- Minecraft 1.21.10
- Fabric Loader 0.19.2
- Fabric Loom 1.14.6
- Java 21
- Yarn 1.21.10+build.3

Recent work includes updates around KillAura, FakeAB, AntiKB/Velocity handling,
AutoBucket, ChestStealer, InventoryMove, FastWeb, AntiTNT, TargetStrafe,
NoSlow NoC0F, Scaffold-related utilities, ClickGUI behavior, configuration
loading, and Dynamic Island visual options.

## Building

Install JDK 21, then run:

```powershell
.\gradlew.bat build
```

The remapped mod jar is generated under:

```text
build/libs/
```

On Linux or macOS, use:

```sh
./gradlew build
```

## Configuration

OpenOpal loads the default configuration on startup and saves it on shutdown.
The in-game command system includes configuration commands through `.c`.

Common commands include:

- `.t <module>` toggles a module.
- `.bind modules <module> <key>` binds a module.
- `.c` manages configs.

## Module Areas

OpenOpal currently registers modules across these categories:

- Combat: KillAura, Criticals, Velocity, Backtrack, CrystalAura, Teams, Reach,
  AutoClicker, AttackDelay, AutoHead, Block, Piercing.
- Movement: Speed, Flight, NoSlow, Inventory Move, FastWeb, TargetStrafe,
  Phase, LongJump, FastStop, Strafe, Physics, Spider, Clipper, SafeWalk, Stuck.
- Utility: AutoBucket, ChestStealer, InventoryManager, AutoArmor, Disabler,
  AntiTNT, AntiVoid, AntiBots, AntiStaff, FastUse, FastPlace, AutoTool,
  AutoChest, AutoHypixel, Blink, KillSay, NoRotate, Spammer, PartySpam.
- World: Scaffold, Breaker, Timer, FastBreak.
- Visual: ClickGUI, Overlay, ESP, Chams, Animations, FullBright, NoFOV,
  Dynamic Island/overlay features, StreamerMode, Cape, Ambience, AttackEffects.
  Anthropic (an evil company,may be...)

## Thanks for

- OpenAI's GPT5.5 xhigh (a stupid company,they can't even handle a simple quota issue)
- yufeiovo's OpenUitems (help us fix our killaura)
- Margele's OpenZen and Naven (NOOOO im not a Malaysian)
- Malkuth's SouthsideNextgen (SSNG's Scaffold is perfect)
- CCBlueX's Liquidbounce (it seems that everyone who hacks in Minecraft should thank them)
- DemonCat's trashy Heypixel server (Zhang Mengyuan and Liu Xinlan do you know i love u?)
- may be more...

## Repository Notes

The repository intentionally excludes local research, decompiled output,
Gradle caches, build outputs, local tools, and run directories. These are
covered by `.gitignore`.

## License

OpenOpal is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) for details.

## More
If You want to get a business license, which is used to sell your openopal-base client, please submit a “Issues”
