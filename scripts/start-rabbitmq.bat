@echo off
REM 启动本地 RabbitMQ（开发用，非 Windows 服务）
set ERLANG_HOME=D:\dev\erlang
set RABBITMQ_BASE=D:\dev\rabbitmq-data
set RABBITMQ_NODENAME=rabbit@localhost
set PATH=%ERLANG_HOME%\bin;%PATH%
call D:\dev\rabbitmq\rabbitmq_server-3.13.7\sbin\rabbitmq-server.bat
