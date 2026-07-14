package wtf.opal.client.music.model;

public enum MusicQuality {
    STANDARD("standard"),
    HIGHER("higher"),
    EXHIGH("exhigh"),
    LOSSLESS("lossless"),
    HIRES("hires"),
    JYEFFECT("jyeffect"),
    SKY("sky"),
    JYMASTER("jymaster");

    private final String apiName;

    MusicQuality(final String apiName) {
        this.apiName = apiName;
    }

    public String getApiName() {
        return apiName;
    }

    public static MusicQuality fromApiName(final String value) {
        if (value != null) {
            for (final MusicQuality quality : values()) {
                if (quality.apiName.equalsIgnoreCase(value)) {
                    return quality;
                }
            }
        }
        return STANDARD;
    }
}
