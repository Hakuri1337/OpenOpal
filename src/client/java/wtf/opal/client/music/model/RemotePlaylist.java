package wtf.opal.client.music.model;

import java.util.List;

public record RemotePlaylist(
        long id,
        String name,
        String coverUrl,
        String description,
        String creator,
        long playCount,
        int songCount,
        List<Song> songs
) {
    public RemotePlaylist {
        name = name == null || name.isBlank() ? "Unknown playlist" : name;
        coverUrl = coverUrl == null ? "" : coverUrl;
        description = description == null ? "" : description;
        creator = creator == null ? "" : creator;
        songs = songs == null ? List.of() : List.copyOf(songs);
    }
}
