package audio.fmod;

import audio.AudioHandle;
import audio.exceptions.AudioPlaybackException;
import audio.fmod.panama.FmodCore;
import com.google.errorprone.annotations.ThreadSafe;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around FMOD channel operations for audio playback. Manages FMOD channels and
 * provides simple playback control without state validation. State validation is handled by
 * FmodAudioEngine. Thread-safe via internal locking.
 */
@Slf4j
@ThreadSafe
class FmodPlaybackManager {

    private final MemorySegment system;

    private final ReentrantLock playbackLock = new ReentrantLock();

    // Current playback tracking
    private Optional<FmodPlaybackHandle> currentPlayback = Optional.empty();
    private Optional<MemorySegment> currentChannel = Optional.empty();

    FmodPlaybackManager(@NonNull MemorySegment system) {
        this.system = system;
    }

    /**
     * Starts playback of the provided sound. This method acquires the playbackLock internally.
     *
     * @param sound The FMOD sound pointer to play
     * @param audioHandle The audio handle for metadata
     * @return A playback handle for controlling the playback
     * @throws AudioPlaybackException if playback cannot be started
     */
    FmodPlaybackHandle play(@NonNull MemorySegment sound, @NonNull AudioHandle audioHandle)
            throws AudioPlaybackException {
        playbackLock.lock();
        try {
            // Clean up any existing playback
            if (currentChannel.isPresent()) {
                cleanupCurrentPlayback();
            }

            // Play the sound - start paused so we can get the channel handle first
            try (Arena arena = Arena.ofConfined()) {
                var channelRef = arena.allocate(ValueLayout.ADDRESS);
                int result =
                        FmodCore.FMOD_System_PlaySound(
                                system, sound, MemorySegment.NULL, 1, channelRef);

                if (result != FmodConstants.FMOD_OK) {
                    throw FmodError.toPlaybackException(result, "play sound");
                }

                MemorySegment channel = channelRef.get(ValueLayout.ADDRESS, 0);

                // Now unpause to start playback
                result = FmodCore.FMOD_Channel_SetPaused(channel, 0);
                if (result != FmodConstants.FMOD_OK) {
                    // Clean up the channel if we can't start it
                    FmodCore.FMOD_Channel_Stop(channel);
                    throw FmodError.toPlaybackException(result, "start playback");
                }

                // Create and track playback handle
                FmodPlaybackHandle playbackHandle =
                        new FmodPlaybackHandle(audioHandle, channel, 0, Long.MAX_VALUE);

                currentPlayback = Optional.of(playbackHandle);
                currentChannel = Optional.of(channel);

                return playbackHandle;
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Starts playback of a specific range within the core.audio. This method acquires the
     * playbackLock internally.
     *
     * @param sound The FMOD sound pointer to play (may be preloaded or streaming)
     * @param audioHandle The audio handle for metadata
     * @param startFrame The starting frame (inclusive)
     * @param endFrame The ending frame (exclusive)
     * @param needsPositioning Whether to set position on the channel (false for preloaded segments)
     * @return A playback handle for controlling the playback
     * @throws AudioPlaybackException if playback cannot be started
     */
    FmodPlaybackHandle playRange(
            @NonNull MemorySegment sound,
            @NonNull AudioHandle audioHandle,
            long startFrame,
            long endFrame,
            boolean needsPositioning)
            throws AudioPlaybackException {
        playbackLock.lock();
        try {
            // Clean up any existing playback
            if (currentChannel.isPresent()) {
                cleanupCurrentPlayback();
            }

            // Play the sound - start paused so we can set position first
            try (Arena arena = Arena.ofConfined()) {
                var channelRef = arena.allocate(ValueLayout.ADDRESS);
                int result =
                        FmodCore.FMOD_System_PlaySound(
                                system, sound, MemorySegment.NULL, 1, channelRef);

                if (result != FmodConstants.FMOD_OK) {
                    throw FmodError.toPlaybackException(result, "play sound");
                }

                MemorySegment channel = channelRef.get(ValueLayout.ADDRESS, 0);

                // Set position if needed (for streaming sounds)
                if (needsPositioning && startFrame > 0) {
                    result =
                            FmodCore.FMOD_Channel_SetPosition(
                                    channel, (int) startFrame, FmodConstants.FMOD_TIMEUNIT_PCM);
                    if (result != FmodConstants.FMOD_OK) {
                        FmodCore.FMOD_Channel_Stop(channel);
                        throw FmodError.toPlaybackException(result, "set position");
                    }
                }

                // Now unpause to start playback
                result = FmodCore.FMOD_Channel_SetPaused(channel, 0);
                if (result != FmodConstants.FMOD_OK) {
                    FmodCore.FMOD_Channel_Stop(channel);
                    throw FmodError.toPlaybackException(result, "start playback");
                }

                // Create and track playback handle
                FmodPlaybackHandle playbackHandle =
                        new FmodPlaybackHandle(audioHandle, channel, startFrame, endFrame);

                currentPlayback = Optional.of(playbackHandle);
                currentChannel = Optional.of(channel);

                return playbackHandle;
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Pauses the current playback. This method acquires the playbackLock internally.
     *
     * @throws AudioPlaybackException if the playback cannot be paused
     */
    void pause() throws AudioPlaybackException {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                throw new AudioPlaybackException("No active playback to pause");
            }

            int result = FmodCore.FMOD_Channel_SetPaused(currentChannel.get(), 1);

            // FMOD_ERR_INVALID_HANDLE means channel already stopped
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                cleanupCurrentPlayback();
                return;
            }

            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toPlaybackException(result, "pause");
            }

        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Resumes the current paused playback. This method acquires the playbackLock internally.
     *
     * @throws AudioPlaybackException if the playback cannot be resumed
     */
    void resume() throws AudioPlaybackException {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                throw new AudioPlaybackException("No active playback to resume");
            }

            int result = FmodCore.FMOD_Channel_SetPaused(currentChannel.get(), 0);

            // FMOD_ERR_INVALID_HANDLE means channel already stopped
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                cleanupCurrentPlayback();
                return;
            }

            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toPlaybackException(result, "resume");
            }

        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Stops the current playback if any. This method acquires the playbackLock internally.
     *
     * @throws AudioPlaybackException if the playback cannot be stopped
     */
    void stop() throws AudioPlaybackException {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                return; // Nothing to stop
            }

            cleanupCurrentPlayback();
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Seeks to a specific frame in the current playback. This method acquires the playbackLock
     * internally.
     *
     * @param frame The target frame position
     * @throws AudioPlaybackException if seeking fails
     */
    void seek(long frame) throws AudioPlaybackException {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                throw new AudioPlaybackException("No active playback to seek");
            }

            int result =
                    FmodCore.FMOD_Channel_SetPosition(
                            currentChannel.get(), (int) frame, FmodConstants.FMOD_TIMEUNIT_PCM);

            // FMOD_ERR_INVALID_HANDLE means channel already stopped
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                cleanupCurrentPlayback();
                return;
            }

            // FMOD_ERR_INVALID_POSITION means seek position is out of bounds
            // FMOD will clamp to valid range, so we accept this as success
            if (result == FmodConstants.FMOD_ERR_INVALID_POSITION) {
                // Position was clamped by FMOD - this is expected behavior
                return;
            }

            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toPlaybackException(result, "seek");
            }

        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Gets the current playback position in frames. This method acquires the playbackLock
     * internally.
     *
     * @return The current position in frames, or 0 if nothing is playing
     */
    public long getPosition() {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                return 0;
            }

            try (Arena arena = Arena.ofConfined()) {
                var positionRef = arena.allocate(ValueLayout.JAVA_INT);
                int result =
                        FmodCore.FMOD_Channel_GetPosition(
                                currentChannel.get(), positionRef, FmodConstants.FMOD_TIMEUNIT_PCM);

                // If channel is invalid, clean up and return 0
                if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                    cleanupCurrentPlayback();
                    return 0;
                }

                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Failed to get position: {}", FmodError.describe(result));
                    return 0;
                }

                return positionRef.get(ValueLayout.JAVA_INT, 0);
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Checks if current playback has finished. This method acquires the playbackLock internally.
     *
     * @return true if playback just finished and was cleaned up, false otherwise
     */
    boolean checkPlaybackFinished() {
        playbackLock.lock();
        try {
            if (!currentChannel.isPresent()) {
                return false;
            }

            // Check if channel is still playing
            try (Arena arena = Arena.ofConfined()) {
                var isPlayingRef = arena.allocate(ValueLayout.JAVA_INT);
                int result = FmodCore.FMOD_Channel_IsPlaying(currentChannel.get(), isPlayingRef);

                // FMOD_ERR_INVALID_HANDLE means channel already stopped
                if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE
                        || (result == FmodConstants.FMOD_OK
                                && isPlayingRef.get(ValueLayout.JAVA_INT, 0) == 0)) {

                    // Channel has finished
                    cleanupCurrentPlayback();
                    return true;
                }
            }
            return false;
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Gets the current playback handle if active. This method acquires the playbackLock internally.
     *
     * @return Current playback handle or empty if none active
     */
    Optional<FmodPlaybackHandle> getCurrentPlayback() {
        playbackLock.lock();
        try {
            return currentPlayback;
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * Checks if there is an active playback. This method acquires the playbackLock internally.
     *
     * @return true if audio is currently playing or paused
     */
    boolean hasActivePlayback() {
        playbackLock.lock();
        try {
            return currentChannel.isPresent();
        } finally {
            playbackLock.unlock();
        }
    }

    /** Cleans up the current playback resources. REQUIRES: Caller must hold the playbackLock. */
    private void cleanupCurrentPlayback() {
        // Stop the FMOD channel if it exists
        currentChannel.ifPresent(
                channel -> {
                    int result = FmodCore.FMOD_Channel_Stop(channel);
                    if (result != FmodConstants.FMOD_OK) {
                        log.warn("Failed to stop channel during cleanup: error code {}", result);
                    }
                });

        // Mark playback as inactive
        currentPlayback.ifPresent(FmodPlaybackHandle::markInactive);

        // Clear references
        currentPlayback = Optional.empty();
        currentChannel = Optional.empty();
    }
}
