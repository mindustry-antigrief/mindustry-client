This is SByte's and BalaM314's v7 fork of buthed010203's fork of mindustry.  It will have various quality of life and automation features when complete.

![Logo](core/assets/icons/pi_64.png)



flarogus

_[Trello Board](https://trello.com/b/aE2tcUwF/mindustry-40-plans)_  
_[Wiki](https://mindustrygame.github.io/wiki)_  
_[Javadoc](https://mindustrygame.github.io/docs/)_
## [Changelog](./core/assets/changelog)

## Installation


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
