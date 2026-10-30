@echo off
rem Lanza build.cmd con salida a b13.log (evita problemas de escapado).
rem Correcciones 29/10: portable con %~dp0 (carpeta de este .cmd).
cd /d %~dp0
call .\build.cmd > b13.log 2>&1
