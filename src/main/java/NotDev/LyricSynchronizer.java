package NotDev;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import org.json.JSONArray;
import org.json.JSONObject;
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
    private static final Map<String, String> lyricsCache = new HashMap<>();
    private static LyricOverlay overlay;

    private static volatile String currentTrackId = "";
    private static volatile long basePlayerPosMs = 0;
    private static volatile long lastSyncNano = 0;
    private static volatile long softOffsetMs = 0;
    private static volatile long totalDurationSec = 0;

    private static volatile boolean isPaused = true;
    private static volatile boolean isLoading = false;
    private static long lastDisplayedTimestamp = -1;
    private static volatile boolean isRunning = true;

    public static void main(String[] args) {
        try { System.setOut(new PrintStream(System.out, true, "UTF-8")); } catch (Exception ignored) {}
        overlay = new LyricOverlay();
        startUpdateThread();

        while (isRunning) {
            if (!isLoading && !isPaused) render();
            try { Thread.sleep(15); } catch (InterruptedException e) { break; }
        }
    }

    private static void render() {
        long now = System.nanoTime();
        long elapsedSinceSync = (now - lastSyncNano) / 1_000_000;

        if (Math.abs(softOffsetMs) > 2) {
            long adj = (long) Math.ceil(softOffsetMs / 20.0);
            if (adj == 0) adj = (softOffsetMs > 0) ? 1 : -1;
            basePlayerPosMs += adj;
            softOffsetMs -= adj;
        }

        long currentTotalMs = basePlayerPosMs + elapsedSinceSync + 100; // +100ms Bias

        synchronized (lyricsMap) {
            var currentEntry = lyricsMap.floorEntry(currentTotalMs);
            if (currentEntry != null) {
                long timestamp = currentEntry.getKey();
                if (timestamp != lastDisplayedTimestamp) {
                    lastDisplayedTimestamp = timestamp;
                    overlay.updateLyrics(
                            lyricsMap.lowerEntry(timestamp) != null ? lyricsMap.lowerEntry(timestamp).getValue() : "",
                            currentEntry.getValue(),
                            lyricsMap.higherEntry(timestamp) != null ? lyricsMap.higherEntry(timestamp).getValue() : ""
                    );
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
                            long playerMs = session.getMedia().getPosition();
                            if (playerMs < 10000 && playerMs > 0) playerMs *= 1000;

                            long internalMs = basePlayerPosMs + (System.nanoTime() - lastSyncNano) / 1_000_000;
                            long diff = playerMs - internalMs;

                            if (Math.abs(diff) > 1500) {
                                basePlayerPosMs = playerMs;
                                lastSyncNano = System.nanoTime();
                            } else if (Math.abs(diff) > 50) {
                                softOffsetMs = diff;
                            }

                            isPaused = (playerMs == basePlayerPosMs && (System.nanoTime() - lastSyncNano)/1_000_000 > 1500);
                            totalDurationSec = session.getMedia().getDuration();

                            String artist = session.getMedia().getArtist();
                            String title = session.getMedia().getTitle();
                            String t = (artist != null ? artist : "") + title;

                            if (!t.equals(currentTrackId)) {
                                currentTrackId = t;
                                overlay.updateArt(session.getMedia().getArtworkPng());
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
        overlay.updateLyrics("", "Поиск...", "");
        new Thread(() -> {
            fetchLyrics(artist, title);
            isLoading = false;
        }).start();
    }

    private static void fetchLyrics(String artist, String title) {
        // Базовая фильтрация рекламы
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("реклама") || lowerTitle.contains("advertisement") || title.length() < 3) {
            System.out.println("[Skip] Looks like an ad or invalid title.");
            overlay.updateLyrics("", "", "");
            return;
        }

        String cleanTitle = title.replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").trim();
        String cacheKey = artist + title;

        if (lyricsCache.containsKey(cacheKey)) {
            parseLrc(lyricsCache.get(cacheKey));
            return;
        }

        // Пытаемся заменить сокращения
        String normArtist = (artist != null) ? artist.toLowerCase().replace("плм", "полматери") : "";

        // Шаг 1: Полный поиск (Артист + Название)
        System.out.println("[Step 1] Full search...");
        if (performSearch(normArtist + " " + cleanTitle, cacheKey)) return;

        // Шаг 2: Брутфорс по артистам (если фиты или неточное совпадение)
        if (artist != null) {
            String[] parts = artist.split("[,&/]|feat\\.?|ft\\.?");
            if (parts.length > 1) { // Запускаем брутфорс только если артистов несколько
                for (String part : parts) {
                    String candidate = part.trim().replace("плм", "полматери");
                    if (candidate.length() < 2) continue;

                    System.out.println("[Step 2] Trying artist: " + candidate);
                    if (performSearch(candidate + " " + cleanTitle, cacheKey)) return;

                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                }
            }
        }

        // Step 3 УДАЛЕН (Поиск только по названию больше не беспокоит)

        System.out.println("[Net] No lyrics for this track.");
        overlay.updateLyrics("", "Lyrics not found", "");
    }

    private static boolean performSearch(String query, String cacheKey) {
        try {
            System.out.println("  -> Querying: " + query);
            String url = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            var res = Jsoup.connect(url).ignoreContentType(true).timeout(15000).execute();
            if (res.statusCode() == 200) {
                JSONArray results = new JSONArray(res.body());
                for (int i = 0; i < Math.min(results.length(), 5); i++) {
                    JSONObject match = results.getJSONObject(i);
                    if (!match.isNull("syncedLyrics") && !match.getString("syncedLyrics").isEmpty()) {
                        System.out.println("  [!] SUCCESS: Found lyrics for " + match.getString("artistName"));
                        saveAndParse(cacheKey, match.getString("syncedLyrics"));
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  [x] Error: " + e.getMessage());
        }
        return false;
    }

    private static void saveAndParse(String key, String lrc) {
        lyricsCache.put(key, lrc);
        parseLrc(lrc);
    }

    private static void parseLrc(String lrc) {
        synchronized (lyricsMap) {
            lyricsMap.clear();
            for (String line : lrc.split("\n")) {
                try {
                    if (!line.startsWith("[")) continue;
                    int timeEnd = line.indexOf("]");
                    if (timeEnd == -1) continue;
                    String timeStr = line.substring(1, timeEnd);
                    String text = line.substring(timeEnd + 1).trim();
                    if (text.isEmpty()) text = "...";
                    String[] parts = timeStr.split(":");
                    long ms = (Long.parseLong(parts[0]) * 60 * 1000) + (long)(Double.parseDouble(parts[1].replace(",", ".")) * 1000);
                    lyricsMap.put(ms, text);
                } catch (Exception ignored) {}
            }
        }
    }
}