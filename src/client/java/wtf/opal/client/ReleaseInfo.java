package wtf.opal.client;

public final class ReleaseInfo {

    public static final ReleaseChannel CHANNEL = ReleaseChannel.STABLE;
    public static final String VERSION = "b2";
    public static final String NAME = "OpenOpal";

    public enum ReleaseChannel {
        STABLE("stable"),
        PUBLIC("public"),
        BETA("beta"),
        DEVELOPMENT("development");

        private final String name;

        ReleaseChannel(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
