package audio.fmod;

import audio.exceptions.AudioEngineException;
import java.io.File;
import java.util.Arrays;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-aware native audio library loader with flexible deployment modes.
 *
 * <h3>Loading Modes</h3>
 *
 * <ul>
 *   <li>PACKAGED: Load from system library path (production deployment)
 *   <li>UNPACKAGED: Load from development filesystem paths (development/testing)
 *   <li>Custom paths supported via configuration for both modes
 *   <li>Automatic platform detection (macOS, Linux, Windows)
 * </ul>
 *
 * <h3>Library Types</h3>
 *
 * <ul>
 *   <li>STANDARD: Production library (optimized, minimal logging)
 *   <li>LOGGING: Debug library (verbose diagnostics, performance logging)
 *   <li>Platform-specific filename resolution (dylib/so/dll)
 *   <li>Automatic selection based on configuration
 * </ul>
 *
 * <h3>Configuration System</h3>
 *
 * <ul>
 *   <li>audio.loading.mode: packaged|unpackaged (default: packaged)
 *   <li>audio.library.type: standard|logging (default: standard)
 *   <li>audio.library.path.{platform}: Custom library paths
 * </ul>
 *
 * <h3>Threading & Safety</h3>
 *
 * <ul>
 *   <li>Thread-safe library loading with synchronized access
 *   <li>Singleton lifecycle managed by dependency injection
 *   <li>Modern JNA loading (Native.load + addSearchPath)
 *   <li>Comprehensive error handling and fallback strategies
 * </ul>
 *
 * <h3>Development vs Production</h3>
 *
 * <ul>
 *   <li>Development: Libraries loaded from src/main/resources/
 *   <li>Production: Libraries loaded from system paths or app bundle
 *   <li>CI/Testing: NOSOUND mode for headless environments
 *   <li>Custom paths: Override default locations via configuration
 * </ul>
 */
public class FmodLibraryLoader {

    /**
     * Determines how native libraries should be loaded by the application.
     *
     * <ul>
     *   <li>PACKAGED - Load from the standard system library path (production mode)
     *   <li>UNPACKAGED - Load from development filesystem paths (development mode)
     * </ul>
     */
    public enum LibraryLoadingMode {
        /** Load libraries from standard system library path (default for production). */
        PACKAGED,

        /** Load libraries from development filesystem paths (for development/testing). */
        UNPACKAGED
    }

    /**
     * Determines which variant of the audio library should be loaded.
     *
     * <ul>
     *   <li>STANDARD - Standard production library (default)
     *   <li>LOGGING - Debug/logging library with additional diagnostic output
     * </ul>
     */
    public enum LibraryType {
        /** Standard production library (default for end users). */
        STANDARD,

        /** Debug/logging library with diagnostic output (for development/CI). */
        LOGGING
    }

    private static final Logger logger = LoggerFactory.getLogger(FmodLibraryLoader.class);

    // Thread safety for library loading
    private final Object loadLock = new Object();

    private final FmodProperties properties;

    public FmodLibraryLoader(FmodProperties properties) {
        this.properties = properties;
    }

    /**
     * Loads the native FMOD library into the process using System.load/System.loadLibrary based on
     * configured mode.
     */
    public void loadNativeLibrary() {
        synchronized (loadLock) {
            try {
                LibraryLoadingMode mode = getLoadingMode();
                LibraryType libraryType = getLibraryType();

                if (mode == LibraryLoadingMode.UNPACKAGED) {
                    loadUnpackaged(libraryType);
                } else {
                    loadPackaged(libraryType);
                }
            } catch (Exception e) {
                throw new AudioEngineException("Failed to load audio library", e);
            }
        }
    }

    // Hardware availability concept removed; audio assumed available.

    /**
     * Gets the audio library loading mode.
     *
     * @return the loading mode, defaults to PACKAGED if not configured
     */
    public LibraryLoadingMode getLoadingMode() {
        String mode = properties.loadingMode();
        try {
            return LibraryLoadingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid audio loading mode '{}', defaulting to PACKAGED. Valid values: {}",
                    mode,
                    Arrays.toString(LibraryLoadingMode.values()));
            return LibraryLoadingMode.PACKAGED;
        }
    }

    /**
     * Gets the audio library type (standard or logging).
     *
     * @return the library type, defaults to STANDARD if not configured
     */
    public LibraryType getLibraryType() {
        String type = properties.libraryType();
        try {
            return LibraryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid audio library type '{}', defaulting to STANDARD. Valid values: {}",
                    type,
                    Arrays.toString(LibraryType.values()));
            return LibraryType.STANDARD;
        }
    }

    /**
     * Gets the custom audio library path for the specified platform.
     *
     * @param platformType the target platform
     * @return the library path, or null if not configured
     */
    public String getLibraryPath() {
        // macOS only
        return properties.libraryPathMacos();
    }

    /**
     * Gets the platform-specific audio library filename based on library type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the filename for the audio library on the current platform
     */
    public String getLibraryFilename(@NonNull LibraryType libraryType) {
        // macOS only
        return libraryType == LibraryType.LOGGING ? "libfmodL.dylib" : "libfmod.dylib";
    }

    /**
     * Gets the full development path to the audio library for the current platform and library
     * type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the relative path to the audio library in development mode
     */
    public String getLibraryDevelopmentPath(@NonNull LibraryType libraryType) {
        var filename = getLibraryFilename(libraryType);
        var base = properties.libraryPathMacos();
        if (base == null || base.isBlank()) {
            base = "src/main/resources/fmod/macos"; // fallback for safety; should not happen
        }
        if (base.endsWith("/") || base.endsWith("\\")) {
            return base + filename;
        }
        return base + "/" + filename;
    }

    /**
     * Loads audio library from development filesystem paths.
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private void loadUnpackaged(@NonNull LibraryType libraryType) {
        if (tryCustomPath(libraryType)) return;
        loadFromDevelopmentPath(libraryType);
    }

    private boolean tryCustomPath(@NonNull LibraryType libraryType) {
        var customPath = getLibraryPath();
        if (customPath == null) return false;

        var customFile = new File(customPath);
        if (customFile.exists()) {
            if (customFile.isDirectory()) {
                var filename = getLibraryFilename(libraryType);
                var resolved = new File(customFile, filename);
                if (!resolved.exists()) {
                    logger.warn(
                            "Custom directory provided but library not found: {}",
                            resolved.getAbsolutePath());
                    return false;
                }
                logger.debug(
                        "Loading audio library from custom directory: {} -> {}",
                        customPath,
                        resolved.getAbsolutePath());
                System.load(resolved.getAbsolutePath());
                return true;
            }
            logger.debug("Loading audio library from custom path: {}", customPath);
            System.load(customFile.getAbsolutePath());
            return true;
        } else {
            logger.warn("Custom audio library path not found: {}", customPath);
            return false;
        }
    }

    private void loadFromDevelopmentPath(@NonNull LibraryType libraryType) {
        var projectDir = System.getProperty("user.dir");
        var relativePath = getLibraryDevelopmentPath(libraryType);
        var fullPath = projectDir + "/" + relativePath;

        var libraryFile = new File(fullPath);
        if (!libraryFile.exists()) {
            throw new RuntimeException(
                    "Audio library not found at: "
                            + fullPath
                            + " (platform="
                            + "macos"
                            + ", type="
                            + libraryType
                            + ")");
        }

        logger.debug("Loading audio library from unpackaged path: {}", fullPath);
        System.load(libraryFile.getAbsolutePath());
    }

    /**
     * Loads audio library from standard system library path (packaged mode).
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private void loadPackaged(@NonNull LibraryType libraryType) {
        // For packaged mode, we use the system library name without path
        String libraryName = getSystemLibraryName(libraryType);
        logger.debug("Loading audio library from system library path: {}", libraryName);
        System.loadLibrary(libraryName);
    }

    /**
     * Gets the system library name for JNA loading (without file extension).
     *
     * @param libraryType The library type (standard or logging)
     * @return The library name for Native.load()
     */
    private String getSystemLibraryName(@NonNull LibraryType libraryType) {
        // All platforms use the same naming convention for FMOD
        return libraryType == LibraryType.LOGGING ? "fmodL" : "fmod";
    }
}
