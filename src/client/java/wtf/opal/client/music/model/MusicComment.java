package wtf.opal.client.music.model;

public record MusicComment(
        long id,
        long userId,
        String nickname,
        String avatarUrl,
        String content,
        long likedCount,
        int replyCount,
        long time,
        String timeText
) {
    public MusicComment {
        nickname = nickname == null ? "Unknown user" : nickname;
        avatarUrl = avatarUrl == null ? "" : avatarUrl;
        content = content == null ? "" : content;
        timeText = timeText == null ? "" : timeText;
    }
}
