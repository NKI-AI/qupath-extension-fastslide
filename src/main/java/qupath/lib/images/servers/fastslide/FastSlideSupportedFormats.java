package qupath.lib.images.servers.fastslide;

import javafx.beans.property.BooleanProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.List;

/**
 * Registry of slide formats the FastSlide extension can open.
 *
 * <p>Each format is gated by a persistent preference so users can toggle, via checkboxes in the
 * preference pane, which file extensions the extension responds to.
 */
public final class FastSlideSupportedFormats {

    private FastSlideSupportedFormats() {
    }

    /**
     * A slide format and the lower-case file-name suffixes that identify it.
     */
    public record Format(String key, String label, List<String> suffixes, BooleanProperty enabled) {

        /**
         * Length of the longest suffix matching {@code lowerPath}, or -1 if none match. Used so the
         * most specific format wins (e.g. {@code .ome.tif} is governed by OME-TIFF, not TIFF).
         */
        int longestMatch(String lowerPath) {
            int best = -1;
            for (String suffix : suffixes) {
                if (lowerPath.endsWith(suffix)) {
                    best = Math.max(best, suffix.length());
                }
            }
            return best;
        }
    }

    private static Format format(String key, String label, String... suffixes) {
        BooleanProperty enabled =
                PathPrefs.createPersistentPreference("fastslide.format." + key, true);
        return new Format(key, label, List.of(suffixes), enabled);
    }

    private static final List<Format> FORMATS = List.of(
            format("svs", "Aperio (.svs)", ".svs"),
            format("ome-tiff", "OME-TIFF (.ome.tif, .ome.tiff)", ".ome.tiff", ".ome.tif"),
            format("tiff", "Generic TIFF (.tif, .tiff)", ".tiff", ".tif"),
            format("czi", "Zeiss CZI (.czi)", ".czi"),
            format("mrxs", "MIRAX (.mrxs)", ".mrxs"),
            format("qptiff", "QPTIFF (.qptiff)", ".qptiff"),
            format("ndpi", "Hamamatsu NDPI (.ndpi)", ".ndpi"),
            format("isyntax", "Philips iSyntax (.isyntax)", ".isyntax"),
            format("vsi", "Olympus VSI (.vsi)", ".vsi"),
            format("bif", "Ventana BIF (.bif)", ".bif"));

    /** All known formats, in display order. */
    public static List<Format> all() {
        return FORMATS;
    }

    /**
     * Returns {@code true} if {@code lowerPath} matches a known format whose checkbox is enabled.
     * The format with the longest matching suffix governs the decision.
     *
     * @param lowerPath a lower-cased file path or name
     */
    public static boolean isEnabledFor(String lowerPath) {
        Format best = null;
        int bestLen = -1;
        for (Format f : FORMATS) {
            int len = f.longestMatch(lowerPath);
            if (len > bestLen) {
                bestLen = len;
                best = f;
            }
        }
        return best != null && best.enabled().get();
    }
}
