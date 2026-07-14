package wtf.opal.client.music.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.opal.client.music.model.AudioSource;
import wtf.opal.client.music.model.CommentPage;
import wtf.opal.client.music.model.LyricDocument;
import wtf.opal.client.music.model.MusicComment;
import wtf.opal.client.music.model.MusicQuality;
import wtf.opal.client.music.model.RemotePlaylist;
import wtf.opal.client.music.model.Song;
import wtf.opal.client.music.model.ToplistEntry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MusicApiClient {
    public static final URI DEFAULT_BASE_URI = URI.create("https://nextmusic.toubiec.cn");

    private final HttpClient httpClient;
    private final URI baseUri;

    public MusicApiClient() {
        this(DEFAULT_BASE_URI);
    }

    public MusicApiClient(final URI baseUri) {
        this.baseUri = baseUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public CompletableFuture<Song> getSongInfo(final long songId) {
        return post("getSongInfo", withId(songId)).thenApply(root -> parseSong(data(root)));
    }

    public CompletableFuture<List<Song>> search(final String keyword, final int limit, final int offset) {
        final JsonObject request = new JsonObject();
        request.addProperty("keyword", keyword);
        request.addProperty("type", 1);
        request.addProperty("limit", Math.clamp(limit, 1, 100));
        request.addProperty("offset", Math.max(0, offset));
        return post("search", request).thenApply(root -> parseSongs(data(root)));
    }

    public CompletableFuture<AudioSource> resolveAudio(final long songId, final MusicQuality quality) {
        return requestAudio("getSongUrl", songId, quality)
                .handle((source, error) -> source != null && source.uri() != null
                        && source.actualQuality() == quality ? CompletableFuture.completedFuture(source)
                        : requestAudio("getMusicUrl", songId, quality))
                .thenCompose(future -> future)
                .thenApply(source -> {
                    if (source.uri() == null) {
                        throw new MusicApiException("No playable URL returned for song " + songId);
                    }
                    return source;
                });
    }

    public CompletableFuture<LyricDocument> getLyrics(final long songId) {
        return post("getSongLyric", withId(songId)).thenApply(root -> {
            final JsonObject value = asObject(data(root));
            return new LyricDocument(
                    string(value, "lrc"),
                    string(value, "tlyric"),
                    string(value, "romalrc"),
                    string(value, "klyric")
            );
        });
    }

    public CompletableFuture<List<ToplistEntry>> getToplists() {
        return post("toplist", new JsonObject()).thenApply(root -> {
            final List<ToplistEntry> entries = new ArrayList<>();
            for (final JsonElement element : findArray(data(root))) {
                final JsonObject object = asObject(element);
                entries.add(new ToplistEntry(number(object, "id"), string(object, "name"), string(object, "updateFrequency")));
            }
            return List.copyOf(entries);
        });
    }

    public CompletableFuture<RemotePlaylist> getPlaylist(final long playlistId) {
        return getPlaylistPage(playlistId, 0, new ArrayList<>(), null);
    }

    public CompletableFuture<RemotePlaylist> getAlbum(final long albumId) {
        return post("getAlbum", withId(albumId)).thenApply(root -> parsePlaylist(data(root), albumId, parseSongs(data(root))));
    }

    public CompletableFuture<CommentPage> getComments(final long songId, final int page, final int pageSize) {
        final JsonObject request = withId(songId);
        request.addProperty("type", 0);
        request.addProperty("sortType", 2);
        request.addProperty("pageNo", Math.max(1, page));
        request.addProperty("pageSize", Math.clamp(pageSize, 1, 100));
        request.addProperty("showInner", true);
        request.addProperty("fetchAll", false);
        return post("getcomments", request).thenApply(root -> {
            final JsonObject value = asObject(data(root));
            return new CommentPage(
                    number(value, "total"),
                    (int) number(value, "pageNo"),
                    (int) number(value, "pageSize"),
                    bool(value, "hasMore"),
                    parseComments(value.get("hotComments")),
                    parseComments(value.get("comments"))
            );
        });
    }

    public CompletableFuture<Long> getPublishTime(final long songId) {
        return post("song/wiki", withId(songId)).thenApply(root -> number(asObject(data(root)), "publishTime"));
    }

    public CompletableFuture<JsonObject> getServiceStats() {
        return post("topen", new JsonObject()).thenApply(root -> asObject(data(root)));
    }

    private CompletableFuture<AudioSource> requestAudio(final String endpoint, final long songId, final MusicQuality quality) {
        final JsonObject request = withId(songId);
        request.addProperty("level", quality.getApiName());
        return post(endpoint, request).thenApply(root -> {
            final JsonObject value = asObject(data(root));
            final String url = string(value, "url");
            return new AudioSource(
                    songId,
                    url.isBlank() ? null : URI.create(url),
                    quality,
                    MusicQuality.fromApiName(string(value, "level")),
                    number(value, "size"),
                    string(value, "md5")
            );
        });
    }

    private CompletableFuture<RemotePlaylist> getPlaylistPage(
            final long playlistId,
            final int offset,
            final List<Song> songs,
            final RemotePlaylist metadata
    ) {
        if (offset >= 50_000) {
            return CompletableFuture.failedFuture(new MusicApiException("Playlist pagination exceeded safety limit"));
        }
        final JsonObject request = withId(playlistId);
        request.addProperty("limit", 500);
        request.addProperty("offset", offset);
        return post("playlist_trackall", request).thenCompose(root -> {
            final JsonElement value = data(root);
            final List<Song> page = parseSongs(value);
            songs.addAll(page);
            final RemotePlaylist current = metadata == null ? parsePlaylist(value, playlistId, List.of()) : metadata;
            if (page.size() < 500) {
                return CompletableFuture.completedFuture(new RemotePlaylist(
                        current.id(), current.name(), current.coverUrl(), current.description(), current.creator(),
                        current.playCount(), Math.max(current.songCount(), songs.size()), songs
                ));
            }
            return getPlaylistPage(playlistId, offset + 500, songs, current);
        });
    }

    private CompletableFuture<JsonObject> post(final String endpoint, final JsonObject body) {
        body.addProperty("timestamp", System.currentTimeMillis());
        final HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/" + endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 429) {
                        throw new MusicApiException("Music API rate limited the request");
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new MusicApiException("Music API returned HTTP " + response.statusCode());
                    }
                    try {
                        final JsonElement parsed = JsonParser.parseString(response.body());
                        final JsonObject root = asObject(parsed);
                        if (root.has("code") && root.get("code").isJsonPrimitive() && root.get("code").getAsInt() != 200) {
                            throw new MusicApiException("Music API returned code " + root.get("code").getAsInt());
                        }
                        return root;
                    } catch (final MusicApiException exception) {
                        throw exception;
                    } catch (final RuntimeException exception) {
                        throw new MusicApiException("Invalid JSON from music API", exception);
                    }
                });
    }

    private static JsonObject withId(final long id) {
        final JsonObject request = new JsonObject();
        request.addProperty("id", Long.toString(id));
        return request;
    }

    private static JsonElement data(final JsonObject root) {
        return root.has("data") ? root.get("data") : root;
    }

    private static Song parseSong(final JsonElement element) {
        final JsonObject object = asObject(element);
        return new Song(
                number(object, "id"),
                firstString(object, "name", "title"),
                firstString(object, "singer", "artist", "author"),
                firstString(object, "album", "albumName"),
                firstString(object, "picimg", "picUrl", "coverImgUrl", "coverImage"),
                parseDuration(object.get("duration")),
                !object.has("free") || bool(object, "free"),
                (int) number(object, "copyright")
        );
    }

    private static List<Song> parseSongs(final JsonElement element) {
        final List<Song> songs = new ArrayList<>();
        for (final JsonElement item : findArray(element)) {
            try {
                songs.add(parseSong(item));
            } catch (final RuntimeException ignored) {
            }
        }
        return List.copyOf(songs);
    }

    private static JsonArray findArray(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonArray();
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        if (!element.isJsonObject()) {
            return new JsonArray();
        }
        final JsonObject object = element.getAsJsonObject();
        for (final String key : List.of("songs", "tracks", "list", "data")) {
            if (object.has(key)) {
                final JsonArray candidate = findArray(object.get(key));
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return new JsonArray();
    }

    private static RemotePlaylist parsePlaylist(final JsonElement element, final long fallbackId, final List<Song> songs) {
        final JsonObject object = asObject(element);
        return new RemotePlaylist(
                number(object, "id") == 0 ? fallbackId : number(object, "id"),
                firstString(object, "name", "title"),
                firstString(object, "coverImage", "coverImgUrl", "picUrl", "picimg"),
                string(object, "description"),
                extractCreator(object),
                number(object, "playCount"),
                (int) number(object, "songCount"),
                songs
        );
    }

    private static String extractCreator(final JsonObject object) {
        for (final String key : List.of("creator", "author", "user", "owner")) {
            if (!object.has(key)) continue;
            final JsonElement value = object.get(key);
            if (value.isJsonPrimitive()) return value.getAsString();
            if (value.isJsonObject()) return firstString(value.getAsJsonObject(), "nickname", "name", "userName");
        }
        return "";
    }

    private static List<MusicComment> parseComments(final JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        final List<MusicComment> comments = new ArrayList<>();
        for (final JsonElement item : element.getAsJsonArray()) {
            final JsonObject object = asObject(item);
            comments.add(new MusicComment(
                    number(object, "id"), number(object, "userId"), string(object, "nickname"),
                    string(object, "avatarUrl"), string(object, "content"), number(object, "likedCount"),
                    (int) number(object, "replyCount"), number(object, "time"), string(object, "timeText")
            ));
        }
        return List.copyOf(comments);
    }

    private static long parseDuration(final JsonElement element) {
        if (element == null || element.isJsonNull()) return 0;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            final long raw = element.getAsLong();
            return raw > 1000 ? raw : raw * 1000;
        }
        final String value = element.getAsString();
        final String[] parts = value.split(":");
        try {
            if (parts.length == 2) return (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
            if (parts.length == 3) return (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])) * 1000;
            return Long.parseLong(value);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private static JsonObject asObject(final JsonElement element) {
        if (element != null && element.isJsonObject()) return element.getAsJsonObject();
        return new JsonObject();
    }

    private static String firstString(final JsonObject object, final String... keys) {
        for (final String key : keys) {
            final String value = string(object, key);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String string(final JsonObject object, final String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        } catch (final RuntimeException ignored) {
            return "";
        }
    }

    private static long number(final JsonObject object, final String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0;
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean bool(final JsonObject object, final String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
        } catch (final RuntimeException ignored) {
            return false;
        }
    }
}
