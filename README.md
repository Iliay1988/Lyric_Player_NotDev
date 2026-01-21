# 🎵 Lyric Player NotDev v5.5

Высокоточный движок для синхронизации текста песен в реальном времени. Программа определяет играющий трек в Windows (Spotify, Яндекс Музыка, браузеры) и отображает синхронизированный текст (LRC) в настраиваемом графическом оверлее.

[![GitHub Repository](https://img.shields.io/badge/GitHub-Repository-blue?style=flat-square&logo=github)](https://github.com/Iliay1988/Lyric_Player_NotDev)

## 🚀 Основные возможности

* **Turbo Sync Engine**: Использование наносекундной интерполяции (`System.nanoTime`) позволяет тексту плавно обновляться даже при задержках системного таймера плеера.
* **Smart Search & Fallback**: Если прямой запрос к API не удает результат (404), система автоматически переключается на глобальный поиск по ключевым словам.
* **Overlay UI**: Полноценное окно на Swing с черным фоном и поддержкой HTML-рендеринга для корректного отображения длинных строк.
* **Memory Caching**: Система кэширования сохраняет тексты прослушанных песен в оперативной памяти, исключая повторные запросы к API.
* **Regex Cleaning**: Автоматическая очистка названий треков от "мусора" (например, *[Official Video]*, *(Radio Edit)*) для максимально точного поиска.

## 🛠 Технологический стек

* **Language:** Java 17+
* **Networking:** [Jsoup](https://jsoup.org/)
* **JSON Parsing:** [org.json](https://github.com/stleary/JSON-java)
* **Media Info:** [media-player-info](https://github.com/redstones/media-player-info) (Windows SMTC Integration)
* **API:** [LRCLIB](https://lrclib.net/)

## 📦 Установка и запуск

1. Склонируйте репозиторий:
   ```bash
   git clone [https://github.com/Iliay1988/Lyric_Player_NotDev.git](https://github.com/Iliay1988/Lyric_Player_NotDev.git)
