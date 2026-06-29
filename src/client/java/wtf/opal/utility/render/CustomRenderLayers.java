package wtf.opal.utility.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;

public class CustomRenderLayers {

    public static RenderLayer getPositionColorQuads(boolean throughWalls) {
        return RenderLayers.debugFilledBox();
    }

    public static RenderLayer getLines(float width, boolean throughWalls) {
        return throughWalls ? RenderLayers.linesTranslucent() : RenderLayers.lines();
    }

}
