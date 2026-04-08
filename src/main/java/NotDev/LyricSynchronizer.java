package NotDev;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LyricSynchronizer {

    private static final TreeMap<Long, String> lyricsMap = new TreeMap<>();

    private static final Map<String, String> lyricsCache = Collections.synchronizedMap(
            new LinkedHashMap<>(30, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > 30;
                }
            }
    );

    private static LyricOverlay overlay = new LyricOverlay();
    private static volatile String currentTrackId = "";
    private static volatile long basePlayerPosMs = 0;
    private static volatile long lastSyncNano = 0;
    private static volatile long softOffsetMs = 0;

    private static volatile boolean isPaused = true;
    private static volatile boolean isLoading = false;
    private static long lastDisplayedTimestamp = -1;
    private static volatile boolean isRunning = true;

    private static final int SYSTEM_LATENCY = 80;

    public static void main(String[] args) {
        System.out.println("LyricSynchronizer started...");
        start();

        while (isRunning) {
            render();
            try { Thread.sleep(15); } catch (InterruptedException e) { break; }
        }
    }

    public static void start() {
        isRunning = true;
        startUpdateThread();
    }

    public static void stop() {
        isRunning = false;
    }

    public static boolean isPaused() {
        return isPaused;
    }

    public static void render() {
        if (isLoading || lyricsMap.isEmpty()) return;

        long now = System.nanoTime();
        long elapsedSinceSync = (now - lastSyncNano) / 1_000_000;

        if (Math.abs(softOffsetMs) > 1) {
            long adj = (softOffsetMs > 0) ? 1 : -1;
            basePlayerPosMs += adj;
            softOffsetMs -= adj;
        }

        long currentTotalMs = basePlayerPosMs + elapsedSinceSync + SYSTEM_LATENCY;

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
                            long durationMs = session.getMedia().getDuration();

                            if (playerMs < 10000 && playerMs > 0) playerMs *= 1000;
                            if (durationMs < 10000 && durationMs > 0) durationMs *= 1000;

                            long now = System.nanoTime();
                            long internalMs = basePlayerPosMs + (now - lastSyncNano) / 1_000_000;
                            long diff = playerMs - internalMs;

                            if (Math.abs(diff) > 1000) {
                                basePlayerPosMs = playerMs;
                                lastSyncNano = now;
                                softOffsetMs = 0;
                            } else {
                                softOffsetMs = diff;
                            }

                            isPaused = (playerMs == basePlayerPosMs && (now - lastSyncNano)/1_000_000 > 1000);

                            String artist = session.getMedia().getArtist();
                            String title = session.getMedia().getTitle();
                            String trackKey = (artist != null ? artist : "") + title;

                            if (!trackKey.equals(currentTrackId)) {
                                currentTrackId = trackKey;
                                overlay.updateArt(session.getMedia().getArtworkPng());
                                prepareNewTrack(artist, title, durationMs / 1000);
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

    private static void prepareNewTrack(String artist, String title, long durationSec) {
        isLoading = true;
        synchronized (lyricsMap) {
            lyricsMap.clear();
            lastDisplayedTimestamp = -1;
        }
        overlay.updateLyrics("", "Поиск текста...", "");

        new Thread(() -> {
            fetchLyrics(artist, title, durationSec);
            isLoading = false;
        }).start();
    }

    private static void fetchLyrics(String artist, String title, long durationSec) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("реклама") || lowerTitle.contains("advertisement") || title.length() < 2) {
            overlay.updateLyrics("", "", "");
            return;
        }

        String cleanTitle = title.replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").trim();
        String cacheKey = artist + title;

        if (lyricsCache.containsKey(cacheKey)) {
            parseLrc(lyricsCache.get(cacheKey));
            return;
        }

        String normArtist = (artist != null) ? artist.toLowerCase().replace("плм", "полматери") : "";

        if (performSearch(normArtist + " " + cleanTitle, cacheKey, durationSec)) return;

        if (artist != null) {
            String[] parts = artist.split("[,&/]|feat\\.?|ft\\.?");
            for (String part : parts) {
                String candidate = part.trim().replace("плм", "полматери");
                if (candidate.length() < 2) continue;
                if (performSearch(candidate + " " + cleanTitle, cacheKey, durationSec)) return;
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        overlay.updateLyrics("", "Текст не найден", "");
    }

    private static boolean performSearch(String query, String cacheKey, long targetDurationSec) {
        try {
            String url = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            var res = Jsoup.connect(url).ignoreContentType(true).timeout(10000).execute();

            if (res.statusCode() == 200) {
                JSONArray results = new JSONArray(res.body());
                JSONObject bestMatch = null;
                double minScore = Double.MAX_VALUE;

                for (int i = 0; i < Math.min(results.length(), 8); i++) {
                    JSONObject match = results.getJSONObject(i);
                    String lrc = match.optString("syncedLyrics", "");
                    if (lrc.isEmpty()) continue;

                    double score = 0;
                    if (targetDurationSec > 0 && !match.isNull("duration")) {
                        score += Math.abs(targetDurationSec - match.getDouble("duration"));
                    }

                    long lastLineSec = getLastTimestamp(lrc) / 1000;
                    if (targetDurationSec > 0 && lastLineSec > 0) {
                        if (lastLineSec > (targetDurationSec + 5)) score += 2000;
                        score += Math.abs(targetDurationSec - lastLineSec);
                    }

                    if (score < minScore) {
                        minScore = score;
                        bestMatch = match;
                    }
                }

                if (bestMatch != null && minScore < 1000) {
                    saveAndParse(cacheKey, bestMatch.getString("syncedLyrics"));
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static long getLastTimestamp(String lrc) {
        try {
            String[] lines = lrc.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.startsWith("[") && line.contains("]")) {
                    int end = line.indexOf("]");
                    String timeStr = line.substring(1, end);
                    if (!timeStr.isEmpty() && Character.isDigit(timeStr.charAt(0))) {
                        return parseTime(timeStr);
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void saveAndParse(String key, String lrc) {
        lyricsCache.put(key, lrc);
        parseLrc(lrc);
    }

    private static void parseLrc(String lrc) {
        synchronized (lyricsMap) {
            lyricsMap.clear();
            long offset = 0;
            for (String line : lrc.split("\n")) {
                try {
                    line = line.trim();
                    if (!line.startsWith("[")) continue;
                    if (line.startsWith("[offset:")) {
                        offset = Long.parseLong(line.replaceAll("[^-\\d]", ""));
                        continue;
                    }
                    int timeEnd = line.indexOf("]");
                    if (timeEnd == -1) continue;
                    String timeStr = line.substring(1, timeEnd);
                    String text = line.substring(timeEnd + 1).trim();
                    if (timeStr.isEmpty() || !Character.isDigit(timeStr.charAt(0))) continue;
                    long ms = parseTime(timeStr);
                    lyricsMap.put(ms + offset, text.isEmpty() ? "..." : text);
                } catch (Exception ignored) {}
            }
        }
    }

    private static long parseTime(String timeStr) {
        String[] parts = timeStr.split(":");
        return (long)(Long.parseLong(parts[0]) * 60 * 1000 + Double.parseDouble(parts[1].replace(",", ".")) * 1000);
    }
}
