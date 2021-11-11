![Logo](core/assets-raw/sprites/ui/foo.png)

[![Release Update](../../workflows/Release%20Update/badge.svg)](https://mindustry-antigrief.github.io/mindustry-client)
[![Tests (Unstable)](../../workflows/Java%20Tests/badge.svg?branch=v7)](https://mindustry-antigrief.github.io/mindustry-client-v7-builds)
[![Discord](https://img.shields.io/discord/741710208501547161.svg?logo=discord&logoColor=white&logoWidth=20&labelColor=7289DA&label=Discord&color=17cf48)](https://discord.gg/yp9ZW7j)

# Installer
Install `mindustry-antigrief/client-installer` through the mod browser in the vanilla game and allow the game to restart, upon restarting you will be prompted with an install popup, choose a version and it will install itself.

## [Changelog](./core/assets/changelog)
## [Development/Unstable Builds](../../../mindustry-client-v7-builds)
### Running The Jar On Mac
For whatever reason, MacOS refuses to be normal. Running the jar is slightly harder, open terminal and type `java -XstartOnFirstThread -jar <jar>` where `<jar>` is the path to the jar file (just click and drag the file in).
## Steam
### Windows
There are 3 methods to install the client on steam for windows.
#### Automatic Installer
Refer to [the installer section](https://github.com/mindustry-antigrief/mindustry-client/tree/v7#installer)
#### Single Command Installer
Run this command in a CMD window [**as admin**](https://www.howtogeek.com/howto/windows-vista/run-a-command-as-administrator-from-the-windows-vista-run-box/) (replace the path if needed): `cd /d "C:\Program Files (x86)\Steam\steamapps\common\Mindustry" && del Mindustry.exe && mklink Mindustry.exe C:\Windows\System32\cmd.exe && curl -L -o jre\client.jar https://github.com/mindustry-antigrief/mindustry-client-v7-builds/releases/latest/download/desktop.jar && echo Done!` then [set the game's launch options](https://support.steampowered.com/kb_article.php?ref=1040-JWMT-2947) to `/c java -jar "%cd%\jre\client.jar"`
#### Semi Automated Install
1. [Download](../../../mindustry-client-v7-builds/releases/latest/download/desktop.jar) the `desktop.jar` file from the latest release.
2. [Download](https://github.com/mindustry-antigrief/mindustry-client/blob/v7/steam_appid.txt) the `steam_appid.txt` file and place it in the same folder as the jar.
3. Place the `desktop.jar` and `steam_appid.txt` in the same folder.
4. Ensure steam is running and you are logged in, double click the jar and it should then open the client on steam.
#### Manual Install
*Removed due to this no longer working correctly in v7, may rewrite later.*

### Mac OS
1. [Download](../../../mindustry-client-v7-builds/releases/latest/download/desktop.jar) the `desktop.jar` file from the latest release.
1. Open the game install folder, right click the `Mindustry.app` file and click `Show Package Contents`.
1. Navigate to the `Resources` folder `Contents > Resources`.
1. Replace the `desktop.jar` with the one you just downloaded.
1. Launching the game should now start the client.
- To uninstall the client, delete the `desktop.jar` file in `Resources` as well as the `Mindustry` file in the `MacOS` folder. Start the game, accept the error and start it again.

### Linux
1. You are using linux, I'm sure you can figure this out yourself.

## Contributing

See [CONTRIBUTING](CONTRIBUTING.md).

## Building

Unstable builds are generated automatically for every commit. You can see them [here](https://github.com/mindustry-antigrief/mindustry-client-v7-builds/releases).

If you'd rather compile on your own, follow these instructions.
First, make sure you have [JDK 16-17](https://adoptium.net/archive.html?variant=openjdk17&jvmVariant=hotspot) installed. **Other JDK versions will not work.** Open a terminal in the Mindustry directory and run the following commands:

### Windows

_Running:_ `gradlew desktop:run`  
_Building:_ `gradlew desktop:dist`  
_Sprite Packing:_ `gradlew tools:pack`

### Linux/Mac OS

_Running:_ `./gradlew desktop:run`  
_Building:_ `./gradlew desktop:dist`  
_Sprite Packing:_ `./gradlew tools:pack`

### Server

The client doesn't work as a server believe it or not.

### Troubleshooting

#### Permission Denied

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` before running `./gradlew`. *This is a one-time procedure.*

---

Gradle may take up to several minutes to download files. Be patient. <br>
After building, the output .JAR file should be in `/desktop/build/libs/Mindustry.jar`

## Feature Requests

Please post feature requests and bug reports in the [discord](https://discord.gg/yp9ZW7j)
