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

    private static final int SYSTEM_LATENCY_BIAS = 100;

    public static void main(String[] args) {
        try { System.setOut(new PrintStream(System.out, true, "UTF-8")); } catch (Exception ignored) {}
        overlay = new LyricOverlay();
        startUpdateThread();

        while (isRunning) {
            if (!isLoading && !isPaused) render();
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    private static void render() {
        long now = System.nanoTime();
        long elapsedSinceSync = (now - lastSyncNano) / 1_000_000;

        // Плавная подстройка времени
        if (Math.abs(softOffsetMs) > 2) {
            long adj = (long) Math.ceil(softOffsetMs / 20.0);
            if (adj == 0) adj = (softOffsetMs > 0) ? 1 : -1;
            basePlayerPosMs += adj;
            softOffsetMs -= adj;
        }

        long currentTotalMs = basePlayerPosMs + elapsedSinceSync + SYSTEM_LATENCY_BIAS;

        synchronized (lyricsMap) {
            var currentEntry = lyricsMap.floorEntry(currentTotalMs);

            // Если нашли строку и она по времени подходит
            if (currentEntry != null) {
                long timestamp = currentEntry.getKey();

                // Передаем в оверлей только если это новая временная метка
                if (timestamp != lastDisplayedTimestamp) {
                    lastDisplayedTimestamp = timestamp;

                    String current = currentEntry.getValue();
                    var prevEntry = lyricsMap.lowerEntry(timestamp);
                    String prev = (prevEntry != null) ? prevEntry.getValue() : "";
                    var nextEntry = lyricsMap.higherEntry(timestamp);
                    String next = (nextEntry != null) ? nextEntry.getValue() : "";

                    overlay.updateLyrics(prev, current, next);
                }
            }
        }

        overlay.updateProgress(currentTotalMs / 1000, totalDurationSec);
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

                            if (playerMs < 10000 && playerMs > 0) playerMs *= 1000;
                            playerMs += ((reqEnd - reqStart) / 2_000_000);

                            long internalMs = basePlayerPosMs + (System.nanoTime() - lastSyncNano) / 1_000_000;
                            long diff = playerMs - internalMs;

                            if (Math.abs(diff) > 1500) {
                                basePlayerPosMs = playerMs;
                                lastSyncNano = System.nanoTime();
                                softOffsetMs = 0;
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
                                byte[] art = session.getMedia().getArtworkPng();
                                overlay.updateArt(art);
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
            softOffsetMs = 0;
        }
        overlay.updateLyrics("", "Searching...", "");
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
            parseLrc(lyricsCache.get(cacheKey));
            return;
        }

        try {
            String url = "https://lrclib.net/api/get?artist_name=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8) + "&track_name=" + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8);
            var response = Jsoup.connect(url).ignoreContentType(true).timeout(10000).execute();
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                if (!json.isNull("syncedLyrics")) {
                    saveAndParse(cacheKey, json.getString("syncedLyrics"));
                    return;
                }
            }
        } catch (Exception ignored) {}

        try {
            String sUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(cleanArtist + " " + cleanTitle, StandardCharsets.UTF_8);
            var res = Jsoup.connect(sUrl).ignoreContentType(true).timeout(15000).execute();
            if (res.statusCode() == 200) {
                JSONArray results = new JSONArray(res.body());
                for (int i = 0; i < results.length(); i++) {
                    JSONObject match = results.getJSONObject(i);
                    if (!match.isNull("syncedLyrics")) {
                        saveAndParse(cacheKey, match.getString("syncedLyrics"));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        overlay.updateLyrics("", "Lyrics not found", "");
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