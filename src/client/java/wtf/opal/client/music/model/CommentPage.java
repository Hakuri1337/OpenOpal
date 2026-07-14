package wtf.opal.client.music.model;

import java.util.List;

public record CommentPage(
        long total,
        int page,
        int pageSize,
        boolean hasMore,
        List<MusicComment> hotComments,
        List<MusicComment> comments
) {
    public CommentPage {
        hotComments = hotComments == null ? List.of() : List.copyOf(hotComments);
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
