package qupath.lib.images.servers.fastslide;

import dev.aifo.fastslide.FastSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Set;

public class FastSlideImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(FastSlideImageServerBuilder.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".svs", ".tif", ".tiff", ".ndpi", ".mrxs", ".scn", ".bif",
            ".qptiff", ".isyntax", ".i2syntax");

    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String... args) {
        float supportLevel = supportLevel(uri, args);
        return UriImageSupport.createInstance(this.getClass(), supportLevel,
                DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
    }

    private float supportLevel(URI uri, String... args) {
        if (uri == null) {
            return 0;
        }
        String path = uri.getPath();
        if (path == null) {
            return 0;
        }
        String lower = path.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return 4;
            }
        }
        return 0;
    }

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) throws Exception {
        return new FastSlideImageServer(uri, args);
    }

    @Override
    public String getName() {
        return "FastSlide builder";
    }

    @Override
    public String getDescription() {
        return "Provides access to whole slide image formats supported by FastSlide";
    }

    @Override
    public Class<BufferedImage> getImageType() {
        return BufferedImage.class;
    }
}
