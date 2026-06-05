package qupath.lib.images.servers.fastslide;

import dev.aifo.fastslide.FastSlide;
import dev.aifo.fastslide.SlideReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FastSlideImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(FastSlideImageServerBuilder.class);

    // Support level claimed for an enabled format. Kept above the built-in
    // readers (Bio-Formats reports 5 for OME-TIFF, OpenSlide 4) so that, when a
    // user enables FastSlide for a format, FastSlide is preferred. QuPath falls
    // back to the next-best reader automatically if our build() throws.
    private static final float SUPPORT_LEVEL = 6f;

    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String... args) {
        float supportLevel = supportLevel(uri, args);
        if (supportLevel <= 0) {
            return UriImageSupport.createInstance(this.getClass(), 0, List.of());
        }

        // Enumerate the navigable images (series). Each one becomes its own
        // ServerBuilder identified by a "--series N" arg, so QuPath presents a
        // chooser when a file holds more than one image (e.g. Olympus VSI).
        List<ServerBuilder<BufferedImage>> builders = new ArrayList<>();
        try {
            String path = resolvePath(uri);
            try (SlideReader reader = FastSlide.open(path)) {
                int count = reader.getImageCount();
                for (int i = 0; i < count; i++) {
                    builders.add(DefaultImageServerBuilder.createInstance(
                            this.getClass(), uri, "--series", Integer.toString(i)));
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to enumerate FastSlide images for {}: {}", uri, e.getLocalizedMessage());
        }

        if (builders.isEmpty()) {
            builders.add(DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
        }
        return UriImageSupport.createInstance(this.getClass(), supportLevel, builders);
    }

    private static String resolvePath(URI uri) throws java.io.IOException {
        Path filePath = GeneralTools.toPath(uri);
        if (filePath != null && Files.exists(filePath)) {
            return filePath.toRealPath().toString();
        }
        return uri.toString();
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
        // Only respond to formats whose checkbox is enabled in the preferences.
        return FastSlideSupportedFormats.isEnabledFor(lower) ? SUPPORT_LEVEL : 0;
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
