package qupath.lib.images.servers.fastslide;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;

public class FastSlideImageServerBuilder implements ImageServerBuilder<BufferedImage> {


    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String... args) throws IOException {
        float supportLevel = supportLevel(uri, args);
        return UriImageSupport.createInstance(this.getClass(), supportLevel,
                DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
    }

    private float supportLevel(URI uri, String...args) {
        return 1;
    }

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) throws Exception {
        return null;
    }

    @Override
    public String getName() {
        return "FastSlide builder";
    }

    @Override
    public String getDescription() {
        return "Provides basic access to whole slide image formats supported by FastSlide";
    }

    @Override
    public Class<BufferedImage> getImageType() {
        return BufferedImage.class;
    }
}