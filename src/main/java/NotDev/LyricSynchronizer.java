package NotDev;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import org.jsoup.Jsoup;

import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LyricSynchronizer {

    private static final TreeMap<Long, String> lyricsMap = new TreeMap<>();
    private static final Map<String, String> lyricsCache = new HashMap<>(); // ХРАНИЛИЩЕ КЭША
    private static LyricOverlay overlay;

    private static volatile String currentTrackId = "";
    private static volatile long basePlayerPosMs = 0;
    private static volatile long lastSyncNano = 0;
    private static volatile boolean isPaused = true;
    private static volatile boolean isLoading = false;

    private static long lastDisplayedTimestamp = -1;
    private static volatile boolean isRunning = true;

    public static void main(String[] args) {
        try { System.setOut(new PrintStream(System.out, true, "UTF-8")); } catch (Exception ignored) {}

        overlay = new LyricOverlay();
        System.out.println("=== Lyric Engine v5.5 (Stable + Search Fallback) ===");

        startUpdateThread();

        while (isRunning) {
            if (!isLoading && !isPaused) {
                render();
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    private static void render() {
        long elapsedSinceSync = (System.nanoTime() - lastSyncNano) / 1_000_000;
        long currentTotalMs = basePlayerPosMs + elapsedSinceSync;

        synchronized (lyricsMap) {
            var entry = lyricsMap.floorEntry(currentTotalMs);
            if (entry != null) {
                long timestamp = entry.getKey();
                if (timestamp > lastDisplayedTimestamp) {
                    String text = entry.getValue();
                    overlay.updateText(text);
                    lastDisplayedTimestamp = timestamp;
                }
            }
        }
    }

    private static void startUpdateThread() {
        Thread worker = new Thread(() -> {
            while (isRunning) {
                try {
                    MediaPlayerInfo instance = MediaPlayerInfo.Instance;
                    if (instance == null) { Thread.sleep(1000); continue; }

                    List<IMediaSession> sessions = instance.getMediaSessions();
                    if (sessions != null && !sessions.isEmpty()) {
                        IMediaSession session = sessions.get(0);
                        if (session != null && session.getMedia() != null) {

                            String title = session.getMedia().getTitle();
                            String artist = session.getMedia().getArtist();
                            if (title == null || title.isEmpty()) continue;

                            long rawPos = session.getMedia().getPosition();
                            long playerMs = (rawPos < 10000 && rawPos > 0) ? rawPos * 1000 : rawPos;

                            long internalMs = basePlayerPosMs + (System.nanoTime() - lastSyncNano) / 1_000_000;
                            if (Math.abs(internalMs - playerMs) > 600 || isPaused) {
                                basePlayerPosMs = playerMs;
                                lastSyncNano = System.nanoTime();
                            }

                            isPaused = (playerMs == basePlayerPosMs && (System.nanoTime() - lastSyncNano)/1_000_000 > 1200);

                            String trackId = (artist != null ? artist : "") + " - " + title;
                            if (!trackId.equals(currentTrackId)) {
                                currentTrackId = trackId;
                                prepareNewTrack(artist, title);
                            }
                        }
                    }
                    Thread.sleep(400);
                } catch (Exception ignored) {}
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private static void prepareNewTrack(String artist, String title) {
        isLoading = true;
        synchronized (lyricsMap) {
            lyricsMap.clear();
            lastDisplayedTimestamp = -1;
        }
        overlay.updateText("Loading lyrics...");
        new Thread(() -> {
            fetchLyrics(artist, title);
            isLoading = false;
        }).start();
    }

    private static void fetchLyrics(String artist, String title) {
        // Очистка названия для более точного поиска
        String cleanTitle = title.replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").trim();
        String cleanArtist = (artist != null) ? artist.replaceAll("\\(.*\\)", "").trim() : "";
        String cacheKey = cleanArtist + cleanTitle;

        // 1. Проверка кэша
        if (lyricsCache.containsKey(cacheKey)) {
            System.out.println("[Cache] Найдено в памяти.");
            parseLrc(lyricsCache.get(cacheKey));
            return;
        }

        try {
            // 2. Попытка через прямой запрос
            String url = "https://lrclib.net/api/get?artist_name=" +
                    URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8) +
                    "&track_name=" + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8);

            var response = Jsoup.connect(url).ignoreContentType(true).timeout(15000).execute();
            if (response.statusCode() == 200) {
                processJsonResponse(response.body(), cacheKey);
                return;
            }
        } catch (Exception e) {
            // 3. Fallback: Глобальный поиск, если прямой запрос выдал 404
            try {
                System.out.println("[System] Прямой поиск не удался, пробуем глобальный...");
                String searchUrl = "https://lrclib.net/api/search?q=" +
                        URLEncoder.encode(cleanArtist + " " + cleanTitle, StandardCharsets.UTF_8);

                String searchRes = Jsoup.connect(searchUrl).ignoreContentType(true).timeout(15000).execute().body();
                JSONArray results = new JSONArray(searchRes);

                for (int i = 0; i < results.length(); i++) {
                    JSONObject match = results.getJSONObject(i);
                    if (!match.isNull("syncedLyrics")) {
                        String lrc = match.getString("syncedLyrics");
                        lyricsCache.put(cacheKey, lrc);
                        parseLrc(lrc);
                        System.out.println("[Net] Найдено через глобальный поиск!");
                        return;
                    }
                }
            } catch (Exception ex) {
                System.out.println("[Error] Ничего не найдено.");
            }
        }
        overlay.updateText("Lyrics not found");
    }

    private static void processJsonResponse(String body, String cacheKey) {
        JSONObject json = new JSONObject(body);
        if (!json.isNull("syncedLyrics")) {
            String lrc = json.getString("syncedLyrics");
            lyricsCache.put(cacheKey, lrc);
            parseLrc(lrc);
            System.out.println("[Net] Успешно загружено.");
        } else {
            overlay.updateText("No synced lyrics");
        }
    }

    private static void parseLrc(String lrc) {
        synchronized (lyricsMap) {
            lyricsMap.clear();
            for (String line : lrc.split("\n")) {
                try {
                    if (!line.startsWith("[")) continue;
                    int timeEnd = line.indexOf("]");
                    String timeStr = line.substring(1, timeEnd);
                    String text = line.substring(timeEnd + 1).trim();
                    if (text.isEmpty()) continue;

                    String[] parts = timeStr.split(":");
                    long ms = (Long.parseLong(parts[0]) * 60 * 1000) + (long)(Double.parseDouble(parts[1].replace(",", ".")) * 1000);
                    lyricsMap.put(ms, text);
                } catch (Exception ignored) {}
            }
        }
    }
}