package audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import audio.fmod.FmodLibraryLoader.LibraryLoadingMode;
import audio.fmod.FmodLibraryLoader.LibraryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for FmodLibraryLoader functionality and dependency injection. */
class FmodLibraryLoaderTest {

    @Test
    @DisplayName("FmodLibraryLoader provides correct FMOD loading mode from configuration")
    void testFmodLoadingModeFromConfiguration() {
        FmodLibraryLoader loader =
                new FmodLibraryLoader(
                        new FmodProperties("unpackaged", "standard", FmodDefaults.MACOS_LIB_PATH));

        // Test that FmodLibraryLoader correctly reads configuration
        LibraryLoadingMode mode = loader.getLoadingMode();

        // In test environment, should be UNPACKAGED (running from source)
        assertEquals(
                LibraryLoadingMode.UNPACKAGED,
                mode,
                "FmodLibraryLoader should return UNPACKAGED in test environment");
    }

    @Test
    @DisplayName("FmodLibraryLoader provides correct FMOD library type from configuration")
    void testLibraryTypeFromConfiguration() {
        FmodLibraryLoader loader =
                new FmodLibraryLoader(
                        new FmodProperties("unpackaged", "standard", FmodDefaults.MACOS_LIB_PATH));

        // Test that FmodLibraryLoader correctly reads configuration
        LibraryType type = loader.getLibraryType();

        // In test environment, should be LOGGING (set via system property)
        assertEquals(
                LibraryType.STANDARD,
                type,
                "FmodLibraryLoader should return STANDARD in test environment");
    }

    // Hardware availability concept removed; no test needed.
}
