@echo off
REM ============================================================
REM 安装 Python 和 YAML 插件 jar 到本地 Maven 仓库
REM 运行方式：install_plugins.bat "D:\soft\PyCharm 2024.1"
REM ============================================================

set PYCHARM_HOME=%1

if "%PYCHARM_HOME%"=="" (
    echo 用法: install_plugins.bat "PyCharm安装目录"
    echo 例如: install_plugins.bat "D:\soft\PyCharm 2024.1"
    pause
    exit /b 1
)

echo ========================================
echo PyCharm 目录: %PYCHARM_HOME%
echo ========================================
echo.

REM 查找 Python 插件 jar
set PYTHON_JAR=
for %%f in ("%PYCHARM_HOME%\plugins\python\lib\python*.jar") do (
    set PYTHON_JAR=%%f
    goto :found_python
)
:found_python

if "%PYTHON_JAR%"=="" (
    echo [警告] 未找到 Python 插件 jar: %PYCHARM_HOME%\plugins\python\lib\python*.jar
) else (
    echo 安装 Python 插件: %PYTHON_JAR%
    call mvn install:install-file -Dfile="%PYTHON_JAR%" ^
        -DgroupId=com.jetbrains.plugins -DartifactId=python ^
        -Dversion=2024.1 -Dpackaging=jar
)

echo.

REM 查找 YAML 插件 jar
set YAML_JAR=
for %%f in ("%PYCHARM_HOME%\plugins\yaml\lib\yaml*.jar") do (
    set YAML_JAR=%%f
    goto :found_yaml
)
:found_yaml

if "%YAML_JAR%"=="" (
    echo [警告] 未找到 YAML 插件 jar: %PYCHARM_HOME%\plugins\yaml\lib\yaml*.jar
) else (
    echo 安装 YAML 插件: %YAML_JAR%
    call mvn install:install-file -Dfile="%YAML_JAR%" ^
        -DgroupId=com.jetbrains.plugins -DartifactId=yaml ^
        -Dversion=2024.1 -Dpackaging=jar
)

echo.
echo ========================================
echo 安装完成！
echo 请在 pom.xml 中取消 python 和 yaml 依赖的注释
echo ========================================
pause
