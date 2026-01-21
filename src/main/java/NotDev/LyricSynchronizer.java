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

    private static volatile boolean isPaused = true;
    private static volatile boolean isLoading = false;

    private static long lastDisplayedTimestamp = -1;
    private static volatile boolean isRunning = true;

    private static final int SYSTEM_LATENCY_BIAS = 100;

    public static void main(String[] args) {
        try { System.setOut(new PrintStream(System.out, true, "UTF-8")); } catch (Exception ignored) {}

        overlay = new LyricOverlay();
        System.out.println("=== Lyric Engine v6.1 [Resilient Sync] ===");

        startUpdateThread();

        while (isRunning) {
            if (!isLoading && !isPaused) {
                render();
            }
            try { Thread.sleep(5); } catch (InterruptedException e) { break; }
        }
    }

    private static void render() {
        long now = System.nanoTime();
        long elapsedSinceSync = (now - lastSyncNano) / 1_000_000;

        if (Math.abs(softOffsetMs) > 2) {
            long adjustment = (long) Math.ceil(softOffsetMs / 10.0);
            if (adjustment == 0) adjustment = (softOffsetMs > 0) ? 1 : -1;
            basePlayerPosMs += adjustment;
            softOffsetMs -= adjustment;
        }

        long currentTotalMs = basePlayerPosMs + elapsedSinceSync + SYSTEM_LATENCY_BIAS;

        synchronized (lyricsMap) {
            var entry = lyricsMap.floorEntry(currentTotalMs);
            if (entry != null) {
                long timestamp = entry.getKey();
                if (timestamp > lastDisplayedTimestamp) {
                    overlay.updateText(entry.getValue());
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

                            long reqStart = System.nanoTime();
                            long playerMs = session.getMedia().getPosition();
                            long reqEnd = System.nanoTime();

                            long reqDelay = (reqEnd - reqStart) / 1_000_000;
                            if (playerMs < 10000 && playerMs > 0) playerMs *= 1000;
                            playerMs += (reqDelay / 2);

                            long internalMs = basePlayerPosMs + (System.nanoTime() - lastSyncNano) / 1_000_000;
                            long diff = playerMs - internalMs;

                            if (diff < -5000 || diff > 3600000) {
                                basePlayerPosMs = playerMs;
                                lastSyncNano = System.nanoTime();
                                softOffsetMs = 0;
                            } else if (Math.abs(diff) > 1500) {
                                System.out.printf("[Jump] Syncing: %d ms.%n", diff);
                                basePlayerPosMs = playerMs;
                                lastSyncNano = System.nanoTime();
                                softOffsetMs = 0;
                            } else if (Math.abs(diff) > 40) {
                                softOffsetMs = diff;
                            }

                            isPaused = (playerMs == basePlayerPosMs && (System.nanoTime() - lastSyncNano)/1_000_000 > 1500);

                            String t = (session.getMedia().getArtist() != null ? session.getMedia().getArtist() : "") + session.getMedia().getTitle();
                            if (!t.equals(currentTrackId)) {
                                currentTrackId = t;
                                prepareNewTrack(session.getMedia().getArtist(), session.getMedia().getTitle());
                            }
                        }
                    }
                    Thread.sleep(400);
                } catch (Exception ignored) {}
            }
        });
        worker.setPriority(Thread.MAX_PRIORITY);
        worker.setDaemon(true);
        worker.start();
    }

    private static void prepareNewTrack(String artist, String title) {
        isLoading = true;
        synchronized (lyricsMap) {
            lyricsMap.clear();
            lastDisplayedTimestamp = -1;
            softOffsetMs = 0;
        }
        System.out.println("[Track] " + artist + " - " + title);
        overlay.updateText("Loading lyrics...");
        new Thread(() -> {
            fetchLyrics(artist, title);
            isLoading = false;
        }).start();
    }

    private static void fetchLyrics(String artist, String title) {
        String cleanTitle = title.replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").trim();
        String cleanArtist = (artist != null) ? artist.replaceAll("\\(.*\\)", "").trim() : "";
        String cacheKey = cleanArtist + cleanTitle;

        if (lyricsCache.containsKey(cacheKey)) {
            System.out.println("[Cache] Loaded from memory.");
            parseLrc(lyricsCache.get(cacheKey));
            return;
        }

        // 1. Попытка прямого GET-запроса
        try {
            String url = "https://lrclib.net/api/get?artist_name=" +
                    URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8) +
                    "&track_name=" + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8);

            var response = Jsoup.connect(url).ignoreContentType(true).timeout(10000).execute();
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                if (!json.isNull("syncedLyrics")) {
                    saveAndParse(cacheKey, json.getString("syncedLyrics"));
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("[Search] Direct lookup failed.");
        }

        // 2. Глобальный поиск с механизмом повторов (Retry)
        int retry = 0;
        int maxRetries = 2;
        while (retry <= maxRetries) {
            try {
                System.out.println("[Search] Global search attempt " + (retry + 1) + "...");
                String sUrl = "https://lrclib.net/api/search?q=" +
                        URLEncoder.encode(cleanArtist + " " + cleanTitle, StandardCharsets.UTF_8);

                var res = Jsoup.connect(sUrl).ignoreContentType(true).timeout(20000).execute();

                if (res.statusCode() == 200) {
                    JSONArray results = new JSONArray(res.body());
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject match = results.getJSONObject(i);
                        if (!match.isNull("syncedLyrics")) {
                            System.out.println("[Search] Success! Lyrics found.");
                            saveAndParse(cacheKey, match.getString("syncedLyrics"));
                            return;
                        }
                    }
                    break; // Текст не найден в базе вообще
                }
            } catch (java.net.SocketTimeoutException e) {
                retry++;
                System.out.println("[Warn] Server timed out. Retrying in 2s...");
                try { Thread.sleep(2000); } catch (Exception ignored) {}
            } catch (Exception ex) {
                System.out.println("[Error] Search error: " + ex.getMessage());
                break;
            }
        }

        System.out.println("[Net] No lyrics available.");
        overlay.updateText("Lyrics not found");
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
            System.out.println("[Parser] Parsed " + lyricsMap.size() + " lines.");
        }
    }
}