@echo off
:setInstall
echo This script will (mostly) automatically install the client and will automatically keep it up to date. 
echo Before continuing, please ensure that both git and OpenJDK 8 are installed.
pause
echo Please specify install location (something like C:\Desktop or D:\Games):
set /p location=
if not exist %location% (echo Error: %location% could not be found.&&goto setInstall)
cd /d %location%
echo Success: Install directory exists.
echo Beginning client installation
git clone https://github.com/blahblahbloopster/mindustry-client.git
echo Success: Client installed successfully.
echo Creating auto update script
echo cd /d %location%\mindustry-client>foos-client.bat&&echo git pull>>foos-client.bat&&echo gradlew.bat desktop:run>>foos-client.bat
pause
echo Success: Auto update script created, to launch the client simply open %location%\foos-client.bat (you can move this script anywhere)
pause
