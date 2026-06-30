package wtf.opal.utility.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.function.Function;

public class CustomRenderLayers {

    public static final RenderLayer POS_COL_QUADS_NO_DEPTH_TEST = RenderLayer.of(
            "renderer/always_depth_pos_color",
            1024,
            false, true,
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("renderer", "pipeline/pos_col_quads_nodepth"))
                    .withCull(true)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(true)
                    .build()
            ),
            RenderLayer.MultiPhaseParameters.builder()
                    .build(false)
    );
    public static final RenderLayer POS_COL_QUADS_WITH_DEPTH_TEST = RenderLayer.of(
            "renderer/lequal_depth_pos_color",
            1024,
            false, true,
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("renderer", "pipeline/pos_col_quads_depth"))
                    .withCull(true)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .build()
            ),
            RenderLayer.MultiPhaseParameters.builder()
                    .build(false)
    );
    private static final RenderPipeline LINES_NODEPTH_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("renderer", "pipeline/lines_nodepth"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(true)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)

            .build()
    );
    public static final Function<Double, RenderLayer> LINES_NO_DEPTH_TEST = Util.memoize(width -> RenderLayer.of(
            "renderer/always_depth_lines",
            1024,
            false, true, LINES_NODEPTH_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(width == 0d ? OptionalDouble.empty() : OptionalDouble.of(width)))
                    .build(false)
    ));
    private static final RenderPipeline LINES_DEPTH_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("renderer", "pipeline/lines_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
            .build()
    );
    public static final Function<Double, RenderLayer> LINES = Util.memoize(width -> RenderLayer.of(
            "renderer/lines",
            1024,
            false, true, LINES_DEPTH_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(width == 0d ? OptionalDouble.empty() : OptionalDouble.of(width)))
                    .build(false)
    ));

    public static RenderLayer getPositionColorQuads(boolean throughWalls) {
        if (throughWalls) return POS_COL_QUADS_NO_DEPTH_TEST;
        else return POS_COL_QUADS_WITH_DEPTH_TEST;
    }

    public static RenderLayer getLines(float width, boolean throughWalls) {
        if (throughWalls) return LINES_NO_DEPTH_TEST.apply((double) width);
        else return LINES.apply((double) width);
    }

}
