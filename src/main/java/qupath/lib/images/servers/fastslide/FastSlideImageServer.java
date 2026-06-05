package qupath.lib.images.servers.fastslide;

import dev.aifo.fastslide.ChannelMetadata;
import dev.aifo.fastslide.DataType;
import dev.aifo.fastslide.Dimensions;
import dev.aifo.fastslide.FastSlide;
import dev.aifo.fastslide.Image;
import dev.aifo.fastslide.ImageFormat;
import dev.aifo.fastslide.ImageInfo;
import dev.aifo.fastslide.PlanarConfig;
import dev.aifo.fastslide.SlideImage;
import dev.aifo.fastslide.SlideProperties;
import dev.aifo.fastslide.SlideReader;
import dev.aifo.fastslide.StackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.*;

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FastSlideImageServer extends AbstractTileableImageServer {

    private static final Logger logger = LoggerFactory.getLogger(FastSlideImageServer.class);

    private final URI uri;
    private final String[] args;
    private final SlideReader reader;
    private final SlideImage image;
    private final int series;
    private final ImageServerMetadata metadata;
    private final boolean isSpectral;
    private final int nChannels;

    public FastSlideImageServer(URI uri, String... args) throws IOException {
        super();
        this.uri = uri;
        this.args = args;

        Path filePath = GeneralTools.toPath(uri);
        String fileName;
        if (filePath != null && Files.exists(filePath)) {
            this.reader = FastSlide.open(filePath.toRealPath().toString());
            fileName = filePath.getFileName().toString();
        } else {
            this.reader = FastSlide.open(uri.toString());
            fileName = uri.toString();
        }

        // Select which navigable image (series) this server represents. Falls
        // back to the primary image when no/invalid "--series N" arg is given.
        int imageCount = reader.getImageCount();
        int requested = parseSeries(args);
        if (requested < 0 || requested >= imageCount) {
            requested = reader.getPrimaryImageIndex();
        }
        this.series = requested;
        this.image = reader.getImage(series);

        String[] imageNames = reader.getImageNames();
        String imageName = (series >= 0 && series < imageNames.length)
                ? imageNames[series] : ("Series " + series);
        // Only decorate the name with the image label when the file is multi-image,
        // so single-image files keep their familiar file-name display.
        String name = imageCount > 1 ? (fileName + " - " + imageName) : fileName;

        ImageFormat format = image.getImageFormat();
        this.isSpectral = (format == ImageFormat.SPECTRAL);

        if (isSpectral) {
            List<ChannelMetadata> channels = image.getChannelMetadata();
            this.nChannels = channels.isEmpty() ? 1 : channels.size();
        } else {
            this.nChannels = 3;
        }

        this.metadata = buildMetadata(name);
    }

    private static int parseSeries(String[] args) {
        if (args == null) {
            return -1;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if ("--series".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private ImageServerMetadata buildMetadata(String name) {
        Dimensions baseDims = image.getBaseDimensions();
        int levelCount = image.getLevelCount();

        double[] downsamples = new double[levelCount];
        for (int i = 0; i < levelCount; i++) {
            downsamples[i] = image.getLevelDownsample(i);
        }

        var builder = new ImageServerMetadata.Builder(
                        FastSlideImageServer.class,
                        uri.toString(),
                        baseDims.width(),
                        baseDims.height())
                .levelsFromDownsamples(downsamples)
                .preferredTileSize(512, 512);

        if (name != null) {
            builder.name(name);
        }

        // Z (focal planes) and T (time points) are orthogonal stack axes that
        // share the X/Y pyramid; channels remain QuPath channels (see below).
        StackInfo stack = image.getStackInfo();
        builder.sizeZ(stack.zCount());
        builder.sizeT(stack.tCount());
        stack.zSpacingMicrons().ifPresent(builder::zSpacingMicrons);
        if (stack.tIntervalSeconds().isPresent()) {
            double interval = stack.tIntervalSeconds().getAsDouble();
            double[] timepoints = new double[stack.tCount()];
            for (int i = 0; i < timepoints.length; i++) {
                timepoints[i] = i * interval;
            }
            builder.timepoints(TimeUnit.SECONDS, timepoints);
        }

        // MPP, magnification and scanner data live on the .vsi container (the
        // reader), not on the individual ETS stack images. Prefer the image's
        // own properties when present, but fall back to the container so the
        // pixel size is reported for every series.
        SlideProperties props = image.getProperties();
        SlideProperties containerProps = reader.getProperties();
        double mppX = props.mppX() > 0 ? props.mppX() : containerProps.mppX();
        double mppY = props.mppY() > 0 ? props.mppY() : containerProps.mppY();
        if (mppX > 0 && mppY > 0) {
            builder.pixelSizeMicrons(mppX, mppY);
        }

        double magnification = props.objectiveMagnification() > 0
                ? props.objectiveMagnification()
                : containerProps.objectiveMagnification();
        if (magnification > 0) {
            builder.magnification(magnification);
        }

        if (isSpectral) {
            builder.rgb(false);
            // Report the true sample type (e.g. 16-bit fluorescence) rather than
            // forcing FLOAT32; the tile raster produced below matches it exactly.
            builder.pixelType(qupathPixelType(image.getDataType()));

            List<ChannelMetadata> channelMeta = image.getChannelMetadata();
            if (!channelMeta.isEmpty()) {
                List<ImageChannel> channels = new ArrayList<>();
                for (ChannelMetadata ch : channelMeta) {
                    Integer color = packColor(ch.colorR(), ch.colorG(), ch.colorB());
                    channels.add(ImageChannel.getInstance(ch.name(), color));
                }
                builder.channels(channels);
            }
        } else {
            builder.rgb(true);
            builder.pixelType(PixelType.UINT8);
        }

        return builder.build();
    }

    private static Integer packColor(int r, int g, int b) {
        return (255 << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();
        int level = tileRequest.getLevel();

        double downsample = image.getLevelDownsample(level);
        int levelX = (int) Math.round(tileRequest.getImageX() / downsample);
        int levelY = (int) Math.round(tileRequest.getImageY() / downsample);
        int z = tileRequest.getZ();
        int t = tileRequest.getT();

        try (Image tile = image.readRegion(levelX, levelY, tileWidth, tileHeight, level, z, t)) {
            ImageInfo info = tile.getInfo();
            byte[] rawData = tile.copyData();

            if (isSpectral) {
                return readSpectralTile(tileWidth, tileHeight, info, rawData);
            } else {
                return readRgbTile(tileWidth, tileHeight, rawData);
            }
        }
    }

    private BufferedImage readRgbTile(int w, int h, byte[] rgb) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        int pixelCount = w * h;
        for (int i = 0; i < pixelCount; i++) {
            int r = rgb[i * 3] & 0xFF;
            int g = rgb[i * 3 + 1] & 0xFF;
            int b = rgb[i * 3 + 2] & 0xFF;
            pixels[i] = (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * Reads a spectral tile, producing a banded BufferedImage whose sample type
     * matches the slide's data type (e.g. 16-bit unsigned for fluorescence)
     * rather than always promoting to float. FastSlide returns SEPARATE planar
     * data (channels-first): [all_ch0_pixels, all_ch1_pixels, ...]; INTERLEAVED
     * is handled as well.
     */
    private BufferedImage readSpectralTile(int w, int h, ImageInfo info, byte[] rawData) {
        int channels = info.channels();
        int n = w * h;
        int bytesPerSample = (int) info.bytesPerSample();
        DataType dataType = info.dataType();
        boolean separate = info.planarConfig() == PlanarConfig.SEPARATE;
        int transferType = awtTransferType(dataType);

        DataBuffer dataBuffer = switch (transferType) {
            case DataBuffer.TYPE_BYTE -> {
                byte[][] bands = new byte[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = (byte) sampleInt(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample), dataType);
                yield new DataBufferByte(bands, n);
            }
            case DataBuffer.TYPE_USHORT -> {
                short[][] bands = new short[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = (short) sampleInt(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample), dataType);
                yield new DataBufferUShort(bands, n);
            }
            case DataBuffer.TYPE_SHORT -> {
                short[][] bands = new short[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = (short) sampleInt(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample), dataType);
                yield new DataBufferShort(bands, n);
            }
            case DataBuffer.TYPE_INT -> {
                int[][] bands = new int[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = sampleInt(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample), dataType);
                yield new DataBufferInt(bands, n);
            }
            case DataBuffer.TYPE_DOUBLE -> {
                double[][] bands = new double[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = sampleDouble(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample));
                yield new DataBufferDouble(bands, n);
            }
            default -> {
                float[][] bands = new float[channels][n];
                for (int c = 0; c < channels; c++)
                    for (int i = 0; i < n; i++)
                        bands[c][i] = sampleFloat(rawData,
                                offset(separate, c, i, channels, n, bytesPerSample));
                yield new DataBufferFloat(bands, n);
            }
        };

        SampleModel sampleModel = new BandedSampleModel(transferType, w, h, channels);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
        return new BufferedImage(createColorModel(channels, transferType), raster, false, null);
    }

    private static int offset(boolean separate, int c, int i, int channels, int n, int bytesPerSample) {
        return separate ? (c * n + i) * bytesPerSample : (i * channels + c) * bytesPerSample;
    }

    private static PixelType qupathPixelType(DataType dataType) {
        return switch (dataType) {
            case UINT8 -> PixelType.UINT8;
            case UINT16 -> PixelType.UINT16;
            case INT16 -> PixelType.INT16;
            case UINT32 -> PixelType.UINT32;
            case INT32 -> PixelType.INT32;
            case FLOAT32 -> PixelType.FLOAT32;
            case FLOAT64 -> PixelType.FLOAT64;
        };
    }

    private static int awtTransferType(DataType dataType) {
        return switch (dataType) {
            case UINT8 -> DataBuffer.TYPE_BYTE;
            case UINT16 -> DataBuffer.TYPE_USHORT;
            case INT16 -> DataBuffer.TYPE_SHORT;
            case UINT32, INT32 -> DataBuffer.TYPE_INT;
            case FLOAT32 -> DataBuffer.TYPE_FLOAT;
            case FLOAT64 -> DataBuffer.TYPE_DOUBLE;
        };
    }

    private static ColorModel createColorModel(int nBands, int transferType) {
        int csType = switch (nBands) {
            case 1 -> ColorSpace.TYPE_GRAY;
            case 3 -> ColorSpace.TYPE_RGB;
            default -> ColorSpace.TYPE_2CLR + Math.min(nBands, 15) - 2;
        };
        ColorSpace cs = new SimpleColorSpace(csType, nBands);
        return new ComponentColorModel(cs, false, false,
                java.awt.Transparency.OPAQUE, transferType);
    }

    private static class SimpleColorSpace extends ColorSpace {
        SimpleColorSpace(int type, int numComponents) {
            super(type, numComponents);
        }

        @Override public float[] toRGB(float[] val) {
            return new float[]{
                    getNumComponents() > 0 ? val[0] : 0,
                    getNumComponents() > 1 ? val[1] : 0,
                    getNumComponents() > 2 ? val[2] : 0
            };
        }
        @Override public float[] fromRGB(float[] rgb) { return new float[getNumComponents()]; }
        @Override public float[] toCIEXYZ(float[] val) { return toRGB(val); }
        @Override public float[] fromCIEXYZ(float[] val) { return fromRGB(val); }
    }

    private static int sampleInt(byte[] data, int offset, DataType dataType) {
        return switch (dataType) {
            case UINT8 -> data[offset] & 0xFF;
            case UINT16 -> (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
            case INT16 -> (short) ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
            case UINT32, INT32 -> (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | ((data[offset + 3] & 0xFF) << 24);
            default -> 0;
        };
    }

    private static float sampleFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(
                (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24));
    }

    private static double sampleDouble(byte[] data, int offset) {
        long bits = 0L;
        for (int b = 0; b < 8; b++) {
            bits |= ((long) (data[offset + b] & 0xFF)) << (8 * b);
        }
        return Double.longBitsToDouble(bits);
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
                FastSlideImageServerBuilder.class, null, uri, args);
    }

    @Override
    protected String createID() {
        // The series MUST be part of the ID: QuPath keys its tile cache by the
        // server path (getPath() -> createID()). Without the series, every image
        // in a multi-image file shares one cache, so label/overview tiles leak
        // on top of the main image.
        return getClass().getName() + ": " + uri.toString() + " [series=" + series + "]";
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
        // The image handle borrows the reader, so free it first.
        image.close();
        reader.close();
        super.close();
    }
}
