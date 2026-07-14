package wtf.opal.utility.player.protocol;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

final class ViaFabricPlus18Api {
    private ViaFabricPlus18Api() {
    }

    static boolean isTargeting1_8() {
        return ProtocolVersion.v1_8.equalTo(ViaFabricPlus.getImpl().getTargetVersion());
    }

    static String getTargetVersionName() {
        final ProtocolVersion targetVersion = ViaFabricPlus.getImpl().getTargetVersion();
        return targetVersion == null ? "unknown" : targetVersion.getName();
    }
}
