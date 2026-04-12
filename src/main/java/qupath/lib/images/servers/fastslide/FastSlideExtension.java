package qupath.lib.images.servers.fastslide;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.ResourceBundle;

import dev.aifo.fastslide.FastSlide;

public class FastSlideExtension implements QuPathExtension {

	private static final ResourceBundle resources = ResourceBundle.getBundle(
			"qupath.lib.images.servers.fastslide.ui.strings");
	private static final Logger logger = LoggerFactory.getLogger(FastSlideExtension.class);

	private static final String EXTENSION_NAME = resources.getString("name");
	private static final String EXTENSION_DESCRIPTION = resources.getString("description");
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.6.0");

	private boolean isInstalled = false;

	private static final BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);

	private static final Property<Integer> integerOption = PathPrefs.createPersistentPreference(
			"demo.num.option", 1).asObject();

	public static Property<Integer> integerOptionProperty() {
		return integerOption;
	}

	private Stage stage;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addPreferenceToPane(qupath);
		addMenuItem(qupath);
		logger.info("FastSlide extension installed (native library auto-loaded via classifier JAR)");
	}

	private void addPreferenceToPane(QuPathGUI qupath) {
		var propertyItem = new PropertyItemBuilder<>(enableExtensionProperty, Boolean.class)
				.name(resources.getString("menu.enable"))
				.category("FastSlide extension")
				.description("Enable the FastSlide extension")
				.build();
		qupath.getPreferencePane()
				.getPropertySheet()
				.getItems()
				.add(propertyItem);
	}

	private void addMenuItem(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItemVersion = new MenuItem("Show Version");
		menuItemVersion.setOnAction(e -> showFastSlideVersion());
		menuItemVersion.disableProperty().bind(enableExtensionProperty.not());
		menu.getItems().add(menuItemVersion);
	}

	private void showFastSlideVersion() {
		Dialogs.showInfoNotification("FastSlide", "Version: " + FastSlide.getVersion());
	}

	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}

	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}
}
