# Yes v7 builds exist, check em out, they are pretty stable. https://github.com/mindustry-antigrief/mindustry-client-v7-builds/releases
This is my fork of mindustry v6.  It will have various quality of life and automation features when complete.
![Logo](core/assets-raw/sprites/ui/logo.png)

[![Release Update](../../workflows/Release%20Update/badge.svg)](https://mindustry-antigrief.github.io/mindustry-client)
[![Tests (Unstable)](../../workflows/Java%20Tests/badge.svg?branch=v6)](https://mindustry-antigrief.github.io/mindustry-client-v6-builds)
[![Discord](https://img.shields.io/discord/741710208501547161.svg?logo=discord&logoColor=white&logoWidth=20&labelColor=7289DA&label=Discord&color=17cf48)](https://discord.gg/yp9ZW7j)

A sandbox tower defense game written in Java.

_[Trello Board](https://trello.com/b/aE2tcUwF/mindustry-40-plans)_  
_[Wiki](https://mindustrygame.github.io/wiki)_  
_[Javadoc](https://mindustrygame.github.io/docs/)_

## [Changelog](./core/assets/changelog)
## [Development/Unstable Builds](../../../mindustry-client-v6-builds)
### Running The Jar On Mac
For whatever reason, MacOS refuses to be normal. Running the jar is slightly harder, open terminal and type `java -XstartOnFirstThread -jar <jar>` where `<jar>` is the path to the jar file (just click and drag the file in).
## Steam
* Windows easy installer, run this command in a CMD window [**as admin**](https://www.howtogeek.com/howto/windows-vista/run-a-command-as-administrator-from-the-windows-vista-run-box/) (replace the path if needed): `cd /d "C:\Program Files (x86)\Steam\steamapps\common\Mindustry" && del Mindustry.exe && mklink Mindustry.exe C:\Windows\System32\cmd.exe && curl -L -o jre\client.jar https://github.com/mindustry-antigrief/mindustry-client-v6/releases/latest/download/desktop.jar && echo Done!` then [set the game's launch options](https://support.steampowered.com/kb_article.php?ref=1040-JWMT-2947) to `/c java -jar "%cd%\jre\client.jar"`
1. Installing the client on steam is rather easy, [download](../../releases/latest/download/desktop.jar) the `desktop.jar` file from the latest release.
2. Continue with the steps below for your operating system.

### Windows

3. Move the file to the `jre` folder where the game is installed as seen [here](core/assets/steamInfo.png).
4. Accept the file replacement prompt.
5. Open the game, and it should work just fine.
    1. If you don't want to do this every time the game updates, rename the new `desktop.jar` to `client.jar`, start a cmd window as **admin**, run `cd /d "C:\Program Files (x86)\Steam\steamapps\common\Mindustry" && ren jre\desktop.jar client.jar & del Mindustry.exe && mklink Mindustry.exe C:\Windows\System32\cmd.exe && echo Done!` (replace the path at the start if needed). Now, right click mindustry on steam, click properties then paste `/c java -jar "%cd%\jre\client.jar"` into the launch options (changing the steam path again if needed). You should no longer need to install the client every time the game updates.

### Mac OS

3. Open the game install folder, right click the `Mindustry.app` file and click `Show Package Contents`.
4. Navigate to the `Resources` folder `Contents > Resources`.
5. Replace the `desktop.jar` with the one you just downloaded.
6. Launching the game should now start the client.
- To uninstall the client, delete the `desktop.jar` file in `Resources` as well as the `Mindustry` file in the `MacOS` folder. Start the game, accept the error and start it again.

### Linux

3. You are using linux, I'm sure you can figure this out yourself.

## Contributing

See [CONTRIBUTING](CONTRIBUTING.md).

## Building

Bleeding-edge builds are generated automatically for every commit. You can see them [here](https://github.com/mindustry-antigrief/mindustry-client-v6-builds/releases/latest).

If you'd rather compile on your own, follow these instructions.
First, make sure you have [JDK 15](https://adoptopenjdk.net/)(will require digging into openjdk archive(at least until this uses jdk 16)) installed. Open a terminal in the root directory, `cd` to the Mindustry folder and run the following commands:

### Windows

_Running:_ `gradlew desktop:run`  
_Building:_ `gradlew desktop:dist`  
_Sprite Packing:_ `gradlew tools:pack`

### Linux/Mac OS

_Running:_ `./gradlew desktop:run`  
_Building:_ `./gradlew desktop:dist`  
_Sprite Packing:_ `./gradlew tools:pack`

### Server

Server builds are bundled with each released build (in Releases). If you'd rather compile on your own, replace 'desktop' with 'server', e.g. `gradlew server:dist`.

### Android

1. Install the Android SDK [here.](https://developer.android.com/studio#downloads) Make sure you're downloading the "Command line tools only", as Android Studio is not required.
2. Set the `ANDROID_HOME` environment variable to point to your unzipped Android SDK directory.
3. Run `gradlew android:assembleDebug` (or `./gradlew` if on linux/mac). This will create an unsigned APK in `android/build/outputs/apk`.

To debug the application on a connected phone, run `gradlew android:installDebug android:run`.(do note that android builds are nonfunctional at the moment)

### Troubleshooting

#### Permission Denied

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` before running `./gradlew`. *This is a one-time procedure.*

---

Gradle may take up to several minutes to download files. Be patient. <br>
After building, the output .JAR file should be in `/desktop/build/libs/Mindustry.jar` for desktop builds, and in `/server/build/libs/server-release.jar` for server builds.

## Feature Requests

Post feature requests in [discord](https://discord.gg/rdv3sBW)

## Downloads
[here](https://github.com/mindustry-antigrief/mindustry-client/releases)  
