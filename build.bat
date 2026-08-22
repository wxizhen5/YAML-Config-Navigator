@echo off
REM ============================================================
REM YAML Config Navigator 插件编译脚本
REM 自动生成 PyCharm 所有 jar 的 classpath，使用 JDK 17 编译
REM 适配 PyCharm 2025.1+（python-ce 插件分离、模块化结构）
REM ============================================================

REM ==================== 配置 ====================
set PYCHARM_HOME=D:\soft\JetBrains\PyCharm 2025.1
set JAVA_HOME=D:\soft\Java\jdk17
REM ================================================

set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo PyCharm 目录: %PYCHARM_HOME%
echo JDK 目录: %JAVA_HOME%
java -version
echo ========================================
echo.

REM 检查 PyCharm 目录
if not exist "%PYCHARM_HOME%\lib" (
    echo [错误] PyCharm 目录不存在: %PYCHARM_HOME%
    pause
    exit /b 1
)

REM 动态生成 classpath，包含以下目录的所有 jar：
REM 1. lib/                          - IntelliJ Platform 核心
REM 2. plugins/python-ce/lib/        - Python PSI 核心（PyCharm 2025.1+）
REM 3. plugins/python/lib/           - Python 专业版功能（含子目录 modules）
REM 4. plugins/yaml/lib/             - YAML 插件（含子目录 modules）
set "CP="
for %%f in ("%PYCHARM_HOME%\lib\*.jar") do call set "CP=%%CP%%;%%f"
for %%f in ("%PYCHARM_HOME%\plugins\python-ce\lib\*.jar") do call set "CP=%%CP%%;%%f"
for /r "%PYCHARM_HOME%\plugins\python\lib" %%f in (*.jar) do call set "CP=%%CP%%;%%f"
for /r "%PYCHARM_HOME%\plugins\yaml\lib" %%f in (*.jar) do call set "CP=%%CP%%;%%f"

REM 去掉开头的分号
set "CP=%CP:~1%"

echo Classpath 已生成
echo.

REM 编译打包
echo 开始编译...
call mvn clean package -DskipTests "-Dpycharm.cp=%CP%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 编译成功！
    echo 插件 jar: target\yaml-config-navigator-1.0.0.jar
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 编译失败，错误码: %ERRORLEVEL%
    echo ========================================
)

pause
