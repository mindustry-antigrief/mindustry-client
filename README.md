This is SByte's and BalaM314's v7 fork of Zxtej's fork of buthed010203's fork of Anuken's mindustry.  It will have various quality of life and automation features when complete.

![Logo](core/assets/icons/pi_64.png)



flarogus

_[Trello Board](https://trello.com/b/aE2tcUwF/mindustry-40-plans)_  
_[Wiki](https://mindustrygame.github.io/wiki)_  
_[Javadoc](https://mindustrygame.github.io/docs/)_
## [Changelog](./core/assets/changelog)
<<<<<<< HEAD
=======
## [Development/Unstable Builds](../../../mindustry-client-v7-builds)
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
>>>>>>> foos/v7

## Installation

Go to the [client builds repository](https://github.com/stormybytes/mindustry-client-builds/) and get the latest release. 

## Contributing

See [CONTRIBUTING](CONTRIBUTING.md).

## Building

Unstable builds are generated automatically for every commit. You can see them [here](https://github.com/stormybytes/mindustry-client-builds/releases).

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
