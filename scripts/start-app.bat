@echo off
REM 启动城探票务应用。请先在当前终端设置数据库和 Redis 密码：
REM   set MYSQL_PASSWORD=你的密码
REM   set REDIS_PASSWORD=你的密码
if "%MYSQL_PASSWORD%"=="" (
    echo [ERROR] 请先设置 MYSQL_PASSWORD 环境变量
    exit /b 1
)
if "%REDIS_PASSWORD%"=="" (
    echo [ERROR] 请先设置 REDIS_PASSWORD 环境变量
    exit /b 1
)

REM Windows 控制台默认 GBK，日志里的中文会变乱码，这里切到 UTF-8
chcp 65001 >nul
set MAVEN_OPTS=-Dfile.encoding=UTF-8

mvn spring-boot:run
