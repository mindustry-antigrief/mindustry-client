# Different installation steps for different operating systems

## Linux

### Debian Based (Manual):
```bash
sudo apt -yqq install git openjdk-8-jdk
git clone https://github.com/blahblahbloopster/mindustry-client.git
cd mindustry-client/
chmod +x gradlew
```
To run:
```bash
cd /path/to/mindustry-client/
./gradlew desktop:run
```
To update:
```bash
cd /path/to/mindustry-client/
git pull
```
--------------------
## Windows

### Manually:
- ~~Todo~~ Too much effort
   - Just use the automatic one lol
### Manually (Steam):
1. Install dependencies.
   1. [Install Git](https://git-scm.com/download/win) if needed.
   1. [Install OpenJDK](https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot) if needed.
1. Run **(Replace the mindustry install folder on the third and fourth line if needed).**
   ```bash
   cd /d %TEMP%
   git clone https://github.com/blahblahbloopster/mindustry-client.git
   del /F C:\Steam\steamapps\common\Mindustry\Mindustry.exe
   mklink C:\Steam\steamapps\common\Mindustry\Mindustry.exe c:\windows\system32\cmd.exe
1. Go to the games properties and set launch options, paste this in **(Replace the mindustry install folder C:\Steam\steamapps\common\Mindustry if needed)**
   ```bash
   /c "cd %TEMP%&&git pull&&gradlew.bat desktop:dist --no-daemon&&cd C:\Steam\steamapps\common\Mindustry&&start %TEMP%\mindustry-client\desktop\build\libs\Mindustry.jar"
1. Try running the game, it should now work as intended

### **Automatic (Steam):**
Download, run and follow instructions in Windows 10 (Steam).bat
### **Automatic:**
Download, run and follow instructions in Windows 10.bat
