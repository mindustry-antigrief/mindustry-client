![Logo](foo.png)

[![Release Update](../../workflows/Release%20Update/badge.svg)](https://mindustry-antigrief.github.io/mindustry-client)
[![Tests (Unstable)](../../workflows/Java%20Tests/badge.svg?branch=v8)](https://mindustry-antigrief.github.io/mindustry-client-v8-builds)
[![Discord](https://img.shields.io/discord/741710208501547161.svg?logo=discord&logoColor=white&logoWidth=20&labelColor=7289DA&label=Discord&color=17cf48)](https://discord.gg/yp9ZW7j)

# Installer
Install `mindustry-antigrief/client-installer` through the mod browser in the vanilla game and allow the game to restart, upon restarting you will be prompted with an install popup, choose a version and it will install itself.

## [Changelog](./core/assets/changelog)
## [Development/Unstable Builds](../../../mindustry-client-v8-builds)
## Steam
### Windows
There are 3 methods to install the client on steam for windows.
#### Automatic Installer
Refer to [the installer section](https://github.com/mindustry-antigrief/mindustry-client/tree/v8#installer)
#### Single Command Installer
Run this command in a CMD window [**as admin**](https://www.howtogeek.com/howto/windows-vista/run-a-command-as-administrator-from-the-windows-vista-run-box/) (replace the path if needed): `cd /d "C:\Program Files (x86)\Steam\steamapps\common\Mindustry" && del Mindustry.exe && mklink Mindustry.exe C:\Windows\System32\cmd.exe && curl -L -o jre\client.jar https://github.com/mindustry-antigrief/mindustry-client-v8-builds/releases/latest/download/desktop.jar && echo Done!` then [set the game's launch options](https://support.steampowered.com/kb_article.php?ref=1040-JWMT-2947) to `/c java -jar "%cd%\jre\client.jar"`
#### Semi Automated Install
1. [Download](../../../mindustry-client-v8-builds/releases/latest/download/desktop.jar) the `desktop.jar` file from the latest release.
2. [Download](https://github.com/mindustry-antigrief/mindustry-client/blob/v8/steam_appid.txt) the `steam_appid.txt` file and place it in the same folder as the jar.
3. Place the `desktop.jar` and `steam_appid.txt` in the same folder.
4. Ensure steam is running and you are logged in, double click the jar and it should then open the client on steam.
#### Manual Install
*Removed due to this no longer working correctly in v7+, may rewrite later.*

### Mac OS
1. [Download](../../../mindustry-client-v8-builds/releases/latest/download/desktop.jar) the `desktop.jar` file from the latest release.
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

Unstable builds are generated automatically for every commit. You can see them [here](https://github.com/mindustry-antigrief/mindustry-client-v8-builds/releases).

If you'd rather compile on your own, follow these instructions.
First, make sure you have [JDK 17](https://adoptium.net/archive.html?variant=openjdk17&jvmVariant=hotspot) installed. **Other JDK versions will not work.** Open a terminal in the Mindustry directory and run the following commands:

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

### Android

1. Install the Android SDK [here.](https://developer.android.com/studio#command-tools) Make sure you're downloading the "Command line tools only", as Android Studio is not required.
2. In the unzipped Android SDK folder, find the cmdline-tools directory. Then create a folder inside of it called `latest` and put all of its contents into the newly created folder.
3. In the same directory run the command `sdkmanager --licenses` (or `./sdkmanager --licenses` if on linux/mac)
4. Set the `ANDROID_HOME` environment variable to point to your unzipped Android SDK directory.
5. Enable developer mode on your device/emulator. If you are on testing on a phone you can follow [these instructions](https://developer.android.com/studio/command-line/adb#Enabling), otherwise you need to google how to enable your emulator's developer mode specifically.
6. Run `gradlew android:assembleDebug` (or `./gradlew` if on linux/mac). This will create an unsigned APK in `android/build/outputs/apk`.

To debug the application on a connected device/emulator, run `gradlew android:installDebug android:run`.

### Troubleshooting

#### Permission Denied

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` before running `./gradlew`. *This is a one-time procedure.*

#### Where is the `mindustry.gen` package?

As the name implies, `mindustry.gen` is generated *at build time* based on other code. You will not find source code for this package in the repository, and it should not be edited by hand.

The following is a non-exhaustive list of the "source" of generated code in `mindustry.gen`:

- `Call`, `*Packet` classes: Generated from methods marked with `@Remote`.
- All entity classes (`Unit`, `EffectState`, `Posc`, etc): Generated from component classes in the `mindustry.entities.comp` package, and combined using definitions in `mindustry.content.UnitTypes`.
- `Sounds`, `Musics`, `Tex`, `Icon`, etc: Generated based on files in the respective asset folders.

---

Gradle may take up to several minutes to download files. Be patient. <br>
After building, the output .JAR file should be in `/desktop/build/libs/Mindustry.jar`

## Feature Requests

Please post feature requests and bug reports in the [discord](https://discord.gg/yp9ZW7j)
