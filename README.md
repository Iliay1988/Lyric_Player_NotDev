# 🎵 Lyric Player NotDev v6.1 (Stable)

[![GitHub Repository](https://img.shields.io/badge/GitHub-Repository-blue?style=flat-square&logo=github)](https://github.com/Iliay1988/Lyric_Player_NotDev)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk)](https://github.com/Iliay1988/Lyric_Player_NotDev)

**Lyric Player NotDev** — это высокоточный движок для синхронизации текста песен в реальном времени. Программа автоматически определяет активный медиа-поток в Windows (Spotify, Яндекс Музыка, браузеры, Telegram и др.) и отображает синхронизированный текст (LRC) в кастомизируемом графическом оверлее поверх всех окон.

---

## 🚀 Почему NotDev? (Precision Engine)

В отличие от большинства браузерных расширений, этот плеер использует **Ultra-Precision Sync** технологию:
* **Latency Compensation**: Мы учитываем время отклика ОС Windows (SMTC). Программа замеряет задержку каждого запроса и корректирует тайминг, что позволяет достичь идеального "острого" тайминга, который не могут предложить другие решения.
* **Nano-Interpolation**: Использование `System.nanoTime()` исключает "дрейф" времени. Текст обновляется плавно и остается синхронным даже при длительном прослушивании.
* **Resilient Global Search**: Умный поиск с системой повторных попыток (**Retry Logic**). Если API перегружено, программа сделает до 3-х попыток с таймаутом до 20 сек.
* **Anti-Drift Protection**: Алгоритм "Жесткого захвата" исправляет рассинхроны более 1.5 сек мгновенно (например, при перемотке).

---

## 📦 Установка и запуск

### Способ 1: Для пользователей (Рекомендуется)
1. Перейдите в раздел **[Releases](https://github.com/Iliay1988/Lyric_Player_NotDev/releases)**.
2. Скачайте последний релизный архив.
3. Убедитесь, что файлы `LyricPlayer-6.1-all.jar` и `run.bat` лежат в одной папке.
4. Запустите программу через `run.bat`.

### Способ 2: Для разработчиков (Сборка)
1. Склонируйте репозиторий:
   ```bash
   git clone [https://github.com/Iliay1988/Lyric_Player_NotDev.git](https://github.com/Iliay1988/Lyric_Player_NotDev.git)
