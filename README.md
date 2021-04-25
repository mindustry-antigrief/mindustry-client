# You're probably looking for [the V6 client](https://github.com/mindustry-antigrief/mindustry-client-v6)

![Logo](core/assets-raw/sprites/ui/logo.png)

![Java CI](https://github.com/blahblahbloopster/mindustry-client/workflows/Java%20CI/badge.svg?branch=custom_client)
[![Discord](https://img.shields.io/discord/741710208501547161.svg)](https://discord.gg/rdv3sBW)

Hi!  I'm blahblahbloopster (aka foo), and this is my fork of Anuke's mindustry.  I've added some handy features such as automated bombing, auto-mine, and auto-build.

The buttons on the top of the screen are:<br>
file icon - start new path<br>
2nd icon - place waypoint<br>
arrow icon - follow waypoints<br>
exit icon - stop following/recording waypoints<br>
eraser icon - clear waypoints<br>
power icon - fixpower redundant but only connects nodes<br>
2nd file icon - Begin path-finding path (path-following paths use the A* search algorithm to find the optimal path between two points while avoiding enemy turrets)
2nd exit icon - End path-finding path (will lag for a moment)
In the player list there are more options:<br>
magnifying glass - watch from their point of view (click on your own player's magnifying glass to switch back)<br>
copy - follow player and build for them (again, click on you own player's copy icon to go back)<br>
shield - blocks player from performing build/breaks  (note: unreliable, doesn't work for quick-building blocks)<br>
Arrow keys move your view, press r to return to player<br>
Press n to make your player come to your viewpoint<br>
Z is same as n but uses the A* algorithm
Pressing b toggles auto-build, your player will fly towards any unbuilt blocks<br>
Semicolon toggles auto-mine, makes you behave like a draug but with unlimited range and when you're in a mech with better drill power it can mine titanium/thorium<br>
chain and chain2 in transportation blocks place chains when used<br>
There is a health bar under the minimap<br>
In settings you can change if ambient lighting is used and flying unit transparency (good for seeing under rev swarms)<br>
Ctrl+click on a tile for a log of places and breaks

A sandbox tower defense game written in Java.

_[Trello Board](https://trello.com/b/aE2tcUwF/mindustry-40-plans)_  
_[Wiki](https://mindustrygame.github.io/wiki)_ 

### Contributing

See [CONTRIBUTING](CONTRIBUTING.md).

### Building

Bleeding-edge live builds are generated automatically for every commit. You can see them [here](https://github.com/Anuken/MindustryBuilds/releases). Old builds might still be on [jenkins](https://jenkins.hellomouse.net/job/mindustry/).

If you'd rather compile on your own, follow these instructions.
First, make sure you have [JDK 8](https://adoptopenjdk.net/) installed. Open a terminal in the root directory, `cd` to the Mindustry folder and run the following commands:

#### Windows

_Running:_ `gradlew.bat desktop:run`  
_Building:_ `gradlew.bat desktop:dist`  
_Sprite Packing:_ `gradlew.bat tools:pack`

#### Linux/Mac OS

_Running:_ `./gradlew desktop:run`  
_Building:_ `./gradlew desktop:dist`  
_Sprite Packing:_ `./gradlew tools:pack`

#### Server

Server builds are bundled with each released build (in Releases). If you'd rather compile on your own, replace 'desktop' with 'server', e.g. `gradlew server:dist`.

#### Android

1. Install the Android SDK [here.](https://developer.android.com/studio#downloads) Make sure you're downloading the "Command line tools only", as Android Studio is not required.
2. Create a file named `local.properties` inside the Mindustry directory, with its contents looking like this: `sdk.dir=<Path to Android SDK you just downloaded, without these bracket>`. For example, if you're on Windows and installed the tools to C:\\tools, your local.properties would contain `sdk.dir=C:\\tools` (*note the double backslashes are required instead of single ones!*).
3. Run `gradlew android:assembleDebug` (or `./gradlew` if on linux/mac). This will create an unsigned APK in `android/build/outputs/apk`.
4. (Optional) To debug the application on a connected phone, do `gradlew android:installDebug android:run`. It is **highly recommended** to use IntelliJ for this instead, however.

##### Troubleshooting

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` before running `./gradlew`. *This is a one-time procedure.*

---

Gradle may take up to several minutes to download files. Be patient. <br>
After building, the output .JAR file should be in `/desktop/build/libs/Mindustry.jar` for desktop builds, and in `/server/build/libs/server-release.jar` for server builds.

### Feature Requests

Post feature requests and feedback [here](https://github.com/Anuken/Mindustry-Suggestions/issues/new/choose).

### Downloads

[<img src="https://static.itch.io/images/badge.svg"
     alt="Get it on Itch.io"
     height="60">](https://anuke.itch.io/mindustry)

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=io.anuke.mindustry)

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/io.anuke.mindustry/)
