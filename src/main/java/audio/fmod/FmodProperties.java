package audio.fmod;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Externalized configuration for FMOD/JNA integration. */
@ConfigurationProperties(prefix = "audio")
public record FmodProperties(
        @DefaultValue("packaged") String loadingMode,
        @DefaultValue("standard") String libraryType,
        @DefaultValue("src/main/resources/fmod/macos") String libraryPathMacos) {}

// Defaults
class FmodDefaults {
    static final String MACOS_LIB_PATH = "src/main/resources/fmod/macos";
}
