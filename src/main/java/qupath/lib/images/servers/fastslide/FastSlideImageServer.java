package qupath.lib.images.servers.fastslide;

import dev.aifo.fastslide.Dimensions;
import dev.aifo.fastslide.FastSlide;
import dev.aifo.fastslide.Image;
import dev.aifo.fastslide.SlideProperties;
import dev.aifo.fastslide.SlideReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class FastSlideImageServer extends AbstractTileableImageServer {

    private static final Logger logger = LoggerFactory.getLogger(FastSlideImageServer.class);

    private final URI uri;
    private final String[] args;
    private final SlideReader reader;
    private final ImageServerMetadata metadata;

    public FastSlideImageServer(URI uri, String... args) throws IOException {
        super();
        this.uri = uri;
        this.args = args;

        Path filePath = GeneralTools.toPath(uri);
        String name;
        if (filePath != null && Files.exists(filePath)) {
            this.reader = FastSlide.open(filePath.toRealPath().toString());
            name = filePath.getFileName().toString();
        } else {
            this.reader = FastSlide.open(uri.toString());
            name = uri.toString();
        }

        this.metadata = buildMetadata(name);
    }

    private ImageServerMetadata buildMetadata(String name) {
        Dimensions baseDims = reader.getBaseDimensions();
        int levelCount = reader.getLevelCount();

        double[] downsamples = new double[levelCount];
        for (int i = 0; i < levelCount; i++) {
            downsamples[i] = reader.getLevelDownsample(i);
        }

        var builder = new ImageServerMetadata.Builder(
                        FastSlideImageServer.class,
                        uri.toString(),
                        baseDims.width(),
                        baseDims.height())
                .levelsFromDownsamples(downsamples)
                .rgb(true)
                .pixelType(PixelType.UINT8)
                .preferredTileSize(512, 512);

        if (name != null) {
            builder.name(name);
        }

        SlideProperties props = reader.getProperties();
        double mppX = props.mppX();
        double mppY = props.mppY();
        if (mppX > 0 && mppY > 0) {
            builder.pixelSizeMicrons(mppX, mppY);
        }

        double magnification = props.objectiveMagnification();
        if (magnification > 0) {
            builder.magnification(magnification);
        }

        return builder.build();
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();
        int level = tileRequest.getLevel();

        double downsample = reader.getLevelDownsample(level);
        int levelX = (int) Math.round(tileRequest.getImageX() / downsample);
        int levelY = (int) Math.round(tileRequest.getImageY() / downsample);

        try (Image image = reader.readRegion(levelX, levelY, tileWidth, tileHeight, level)) {

            byte[] rgb = image.copyData();
            BufferedImage out = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
            int[] pixels = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            int pixelCount = tileWidth * tileHeight;
            for (int i = 0; i < pixelCount; i++) {
                int r = rgb[i * 3] & 0xFF;
                int g = rgb[i * 3 + 1] & 0xFF;
                int b = rgb[i * 3 + 2] & 0xFF;
                pixels[i] = (r << 16) | (g << 8) | b;
            }
            return out;
        }
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
                FastSlideImageServerBuilder.class, null, uri, args);
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": " + uri.toString();
    }

    @Override
    public Collection<URI> getURIs() {
        return List.of(uri);
    }

    @Override
    public String getServerType() {
        return "FastSlide";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    @Override
    public void close() throws Exception {
        reader.close();
        super.close();
    }
}
