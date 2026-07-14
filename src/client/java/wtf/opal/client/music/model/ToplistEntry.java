package wtf.opal.client.music.model;

public record ToplistEntry(long id, String name, String updateFrequency) {
    public ToplistEntry {
        name = name == null ? "Unknown chart" : name;
        updateFrequency = updateFrequency == null ? "" : updateFrequency;
    }
}
