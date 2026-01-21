@echo off
:: Устанавливаем кодировку UTF-8 для корректного отображения текста
chcp 65001 >nul
title Lyric Player Runner

:: Переходим в папку, где лежит сам батник (Рабочий стол)
cd /d "%~dp0"

echo Проверка файла...
if not exist "LyricPlayer-5.6-all.jar" (
    echo [ОШИБКА] Файл LyricPlayer-5.6-all.jar не найден на Рабочем столе!
    dir /b *.jar
    pause
    exit
)

echo Запуск Lyric Player v5.6...
:: Запускаем программу с выделением памяти и кодировкой
java -Dfile.encoding=UTF-8 -jar "LyricPlayer-5.6-all.jar"

if %errorlevel% neq 0 (
    echo.
    echo [!] Программа завершилась с ошибкой или Java не установлена.
)
pause
