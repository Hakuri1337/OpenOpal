package wtf.opal.utility.player.protocol;

import net.fabricmc.loader.api.FabricLoader;

public final class ViaFabricPlusSupport {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("viafabricplus");

    private ViaFabricPlusSupport() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isTargeting1_8() {
        if (!LOADED) {
            return false;
        }

        try {
            return ViaFabricPlus18Api.isTargeting1_8();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String getTargetVersionName() {
        if (!LOADED) {
            return "not installed";
        }

        try {
            return ViaFabricPlus18Api.getTargetVersionName();
        } catch (Throwable throwable) {
            return "unavailable (" + throwable.getClass().getSimpleName() + ")";
        }
    }
}
