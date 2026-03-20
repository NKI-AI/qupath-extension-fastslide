package qupath.lib.images.servers.fastslide;

import dev.aifo.fastslide.FastSlide;
import dev.aifo.fastslide.Image;
import dev.aifo.fastslide.SlideReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
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
    private final SlideReader fsr;

    public FastSlideImageServer(URI uri, String...args) throws IOException {
        super();
        this.uri = uri;
        this.args = args;

        Path filePath = GeneralTools.toPath(uri);
        String name;
        if (filePath != null && Files.exists(filePath)) {
            // We need to use the real path to resolve symlinks
            this.fsr = FastSlide.open(filePath.toRealPath().toString());
            name = filePath.getFileName().toString();
        } else {
            this.fsr = FastSlide.open(uri.toString());
            name = null;
        }

    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        Image image = this.fsr.readRegion(
                tileRequest.getImageX(),
                tileRequest.getImageY(),
                tileRequest.getTileWidth(),
                tileRequest.getTileHeight(),
                tileRequest.getLevel());
        BufferedImage img_out = new BufferedImage(
                tileRequest.getTileWidth(),
                tileRequest.getTileHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        byte[] data = ((DataBufferByte)img_out.getRaster().getDataBuffer()).getData();
        byte[] data_img = image.copyData();
        System.arraycopy(data_img, 0, data, 0, data.length);
        return img_out;
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return null;
    }

    @Override
    protected String createID() {
        return "";
    }

    @Override
    public Collection<URI> getURIs() {
        return List.of(this.uri);
    }

    @Override
    public String getServerType() {
        return "FastSlide";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return null;
    }
}
