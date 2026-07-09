package wtf.opal.client.feature.module.impl.visual;

import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class CapeModule extends Module {

    private final ModeProperty<CapeType> type = new ModeProperty<>("Type", CapeType.OPAL);

    public CapeModule() {
        super("Cape", "Gives you a cape of your choosing.", ModuleCategory.VISUAL);
        this.addProperties(type);
    }

    public CapeType getType() {
        return type.getValue();
    }

    @Override
    public String getSuffix() {
        return this.getType().toString();
    }

    @Override
    protected void onEnable() {
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    public enum CapeType {
        OPAL("Opal"),
        COBALT("Cobalt"),
        MIGRATOR("Migrator"),
        MINECON_2011("Minecon 2011"),
        MINECON_2012("Minecon 2012"),
        MINECON_2013("Minecon 2013"),
        MINECON_2015("Minecon 2015"),
        MINECON_2016("Minecon 2016"),
        MOJANG_STUDIOS("Mojang Studios"),
        MOJANG("Mojang");

        private final String name, slug;
        private final Identifier identifier;
        private final AssetInfo.TextureAsset textureAsset;

        CapeType(final String name) {
            this.name = name;
            this.slug = name.replace(' ', '_').toLowerCase();
            this.identifier = Identifier.of("opal", "capes/" + this.slug + ".png");
            this.textureAsset = new AssetInfo.TextureAssetInfo(this.identifier);
        }

        public String getSlug() {
            return slug;
        }

        public Identifier getIdentifier() {
            return identifier;
        }

        public AssetInfo.TextureAsset getTextureAsset() {
            return textureAsset;
        }

        @Override
        public String toString() {
            return name;
        }

        public static CapeType fromSlug(final String slug) {
            for (final CapeType type : values()) {
                if (type.slug.equals(slug)) {
                    return type;
                }
            }
            return null;
        }
    }

}
