package audio.fmod;

import annotations.ThreadSafe;
import audio.AudioHandle;
import audio.AudioMetadata;
import audio.exceptions.AudioEngineException;
import audio.exceptions.AudioLoadException;
import com.google.inject.Inject;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages audio file loading and lifecycle for the FMOD audio engine. Maintains the single-audio
 * paradigm where only one audio file is "current" at a time.
 */
@ThreadSafe
@Slf4j
class FmodAudioLoadingManager {

    // Immutable record to hold current audio state atomically
    private record CurrentAudio(
            @NonNull FmodAudioHandle handle, @NonNull Pointer sound, @NonNull String path) {}

    private final FmodLibrary fmod;
    private final Pointer system;
    private final FmodSystemStateManager stateManager;
    private final FmodHandleLifecycleManager lifecycleManager;
    private final ReentrantLock loadingLock = new ReentrantLock();

    // Current loaded audio (single-audio paradigm) - guarded by loadingLock
    private volatile Optional<CurrentAudio> current = Optional.empty();

    @Inject
    FmodAudioLoadingManager(
            @NonNull FmodLibrary fmod,
            @NonNull Pointer system,
            @NonNull FmodSystemStateManager stateManager,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {
        this.fmod = fmod;
        this.system = system;
        this.stateManager = stateManager;
        this.lifecycleManager = lifecycleManager;
    }

    /**
     * Load an audio file. Returns the same handle if the file is already loaded. This method
     * acquires the loadingLock for thread-safe operations.
     *
     * @param filePath Path to the audio file
     * @return Handle to the loaded audio
     * @throws AudioLoadException if the file cannot be loaded
     */
    AudioHandle loadAudio(@NonNull String filePath) throws AudioLoadException {
        loadingLock.lock();
        try {
            // Validate and normalize the path
            String canonicalPath = validateAndNormalize(filePath);

            // Check if this file is already loaded
            Optional<CurrentAudio> existing = current;
            if (existing.isPresent() && existing.get().path().equals(canonicalPath)) {
                // Same file already loaded, return existing handle
                return existing.get().handle();
            }

            // Create new sound BEFORE releasing old one (to ensure we always have valid audio)
            Pointer newSound;
            try {
                newSound = createSound(canonicalPath);
            } catch (AudioLoadException e) {
                // Failed to create new sound - keep the old one
                throw e;
            }

            // Only release previous audio after successfully creating new one
            if (existing.isPresent()) {
                int result = fmod.FMOD_Sound_Release(existing.get().sound());
                if (result != FmodConstants.FMOD_OK
                        && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                    log.warn(
                            "Error releasing previous sound '{}': error code {}",
                            existing.get().path(),
                            result);
                }
            }

            // Create handle for the new audio using the lifecycle manager
            FmodAudioHandle newHandle = lifecycleManager.createHandle(newSound, canonicalPath);

            // Update current state atomically
            current = Optional.of(new CurrentAudio(newHandle, newSound, canonicalPath));

            return newHandle;

        } finally {
            loadingLock.unlock();
        }
    }

    /**
     * Get metadata for the currently loaded core.audio. This method acquires the loadingLock for
     * thread-safe operations.
     *
     * @return Metadata if audio is loaded, empty otherwise
     */
    Optional<AudioMetadata> getCurrentMetadata() {
        loadingLock.lock();
        try {
            return current.map(
                    audio -> {
                        try {
                            return extractMetadata(audio.sound());
                        } catch (AudioLoadException e) {
                            log.warn(
                                    "Failed to extract metadata for '{}': {}",
                                    audio.path(),
                                    e.getMessage());
                            // Return basic metadata with what we know
                            return new AudioMetadata(
                                    48000, // Default sample rate
                                    2, // Default stereo
                                    16, // Default bit depth
                                    "Unknown", 0L, // Unknown frame count
                                    0.0); // Unknown duration
                        }
                    });
        } finally {
            loadingLock.unlock();
        }
    }

    /**
     * Check if the given handle represents the current core.audio.
     *
     * @param handle The handle to check
     * @return true if this is the current audio
     */
    boolean isCurrent(@NonNull AudioHandle handle) {
        return lifecycleManager.isCurrent(handle);
    }

    /**
     * Get the current FMOD sound pointer.
     *
     * @return Current sound if loaded, empty otherwise
     */
    Optional<Pointer> getCurrentSound() {
        return current.map(CurrentAudio::sound);
    }

    /**
     * Get the current audio handle.
     *
     * @return Current handle if loaded, empty otherwise
     */
    Optional<FmodAudioHandle> getCurrentHandle() {
        return current.map(CurrentAudio::handle);
    }

    /**
     * Release all loaded audio resources. This method acquires the loadingLock for thread-safe
     * operations.
     */
    void releaseAll() {
        loadingLock.lock();
        try {
            current.ifPresent(
                    audio -> {
                        int result = fmod.FMOD_Sound_Release(audio.sound());
                        if (result != FmodConstants.FMOD_OK
                                && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                            log.warn(
                                    "Error releasing sound for '{}': error code " + result,
                                    audio.path());
                        }
                    });
            // Always clear reference even if release failed to prevent use-after-free
            current = Optional.empty();
            // Clear the lifecycle manager's current handle
            lifecycleManager.clear();
        } finally {
            loadingLock.unlock();
        }
    }

    private String validateAndNormalize(@NonNull String filePath) throws AudioLoadException {
        File file = new File(filePath);

        // Check file exists
        if (!file.exists()) {
            throw new AudioLoadException("Audio file not found: " + filePath);
        }

        // Check file is readable
        if (!file.canRead()) {
            throw new AudioLoadException("Cannot read audio file: " + filePath);
        }

        // Check it's a file, not a directory
        if (file.isDirectory()) {
            throw new AudioLoadException("Path is a directory, not a file: " + filePath);
        }

        // Get canonical path to normalize different representations of the same file
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new AudioLoadException("Failed to resolve file path: " + filePath, e);
        }
    }

    /**
     * Create an FMOD sound from a file. This method must be called while holding the loadingLock.
     */
    private Pointer createSound(@NonNull String canonicalPath) throws AudioLoadException {
        // Check we're in the right state
        try {
            stateManager.checkState(FmodSystemStateManager.State.INITIALIZED);
        } catch (AudioEngineException e) {
            throw new AudioLoadException("Audio engine not initialized");
        }

        // Set appropriate flags for playback
        int flags = FmodConstants.FMOD_DEFAULT | FmodConstants.FMOD_ACCURATETIME;

        // Create the sound
        PointerByReference soundRef = new PointerByReference();
        int result =
                fmod.FMOD_System_CreateSound(
                        system,
                        canonicalPath,
                        flags,
                        null, // No extended info
                        soundRef);

        if (result != FmodConstants.FMOD_OK) {
            throw FmodError.toLoadException(result, canonicalPath);
        }

        Pointer sound = soundRef.getValue();
        if (sound == null) {
            throw new AudioLoadException("FMOD returned null sound for: " + canonicalPath);
        }

        return sound;
    }

    // Error mapping centralized in FmodError

    /**
     * Extract metadata from an FMOD sound. This method must be called while holding the
     * loadingLock.
     */
    private AudioMetadata extractMetadata(@NonNull Pointer sound) throws AudioLoadException {
        // Get sound format info
        IntByReference typeRef = new IntByReference(); // File type (WAV, MP3, etc.)
        IntByReference formatRef =
                new IntByReference(); // Sample format (PCM16, FLOAT, etc.) - not used
        IntByReference channelsRef = new IntByReference(); // Number of channels
        IntByReference bitsRef = new IntByReference(); // Bits per sample

        int result = fmod.FMOD_Sound_GetFormat(sound, typeRef, formatRef, channelsRef, bitsRef);

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioLoadException(
                    "Failed to extract audio format metadata (error code: " + result + ")");
        }

        // Get length in milliseconds
        IntByReference lengthMsRef = new IntByReference();
        result = fmod.FMOD_Sound_GetLength(sound, lengthMsRef, FmodConstants.FMOD_TIMEUNIT_MS);

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioLoadException(
                    "Failed to get audio duration (error code: " + result + ")");
        }

        // Get the actual sample rate from the sound
        FloatByReference frequencyRef = new FloatByReference();
        IntByReference priorityRef = new IntByReference(); // Not used
        result = fmod.FMOD_Sound_GetDefaults(sound, frequencyRef, priorityRef);

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioLoadException("Failed to get sample rate (error code: " + result + ")");
        }

        // Get total samples for precise duration
        IntByReference lengthSamplesRef = new IntByReference();
        result =
                fmod.FMOD_Sound_GetLength(sound, lengthSamplesRef, FmodConstants.FMOD_TIMEUNIT_PCM);

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioLoadException(
                    "Failed to get total samples (error code: " + result + ")");
        }

        long totalSamples = Integer.toUnsignedLong(lengthSamplesRef.getValue());
        int sampleRate = Math.round(frequencyRef.getValue());

        // Map sound type to format string
        String format = mapSoundTypeToFormat(typeRef.getValue());

        // Calculate precise duration from samples
        double durationSeconds = totalSamples / (double) sampleRate;

        return new AudioMetadata(
                sampleRate,
                channelsRef.getValue(),
                bitsRef.getValue(),
                format,
                totalSamples,
                durationSeconds);
    }

    /** Map FMOD sound type to human-readable format string. */
    private String mapSoundTypeToFormat(int soundType) {
        return switch (soundType) {
            case FmodConstants.FMOD_SOUND_TYPE_WAV -> "WAV";
            case FmodConstants.FMOD_SOUND_TYPE_AIFF -> "AIFF";
            case FmodConstants.FMOD_SOUND_TYPE_MPEG -> "MP3";
            case FmodConstants.FMOD_SOUND_TYPE_OGGVORBIS -> "OGG";
            case FmodConstants.FMOD_SOUND_TYPE_FLAC -> "FLAC";
            case FmodConstants.FMOD_SOUND_TYPE_OPUS -> "Opus";
            case FmodConstants.FMOD_SOUND_TYPE_RAW -> "RAW";
            default -> "Unknown";
        };
    }
}
