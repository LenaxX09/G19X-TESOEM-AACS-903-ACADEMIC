@echo off
rem Lanza build.cmd con salida a b13.log (evita problemas de escapado).
cd /d C:\CPP\android
call .\build.cmd > b13.log 2>&1
