package playback;

import audio.AudioEngine;
import audio.AudioHandle;
import audio.PlaybackHandle;
import audio.PlaybackListener;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import server.rpc.ClientGateway;
import server.rpc.dto.CloseAudio;
import server.rpc.dto.LoadAudio;
import server.rpc.dto.PlayPause;
import server.rpc.dto.ReplayLast;
import server.rpc.dto.ReplayNudge;
import server.rpc.dto.SeekBy;
import server.rpc.dto.SeekTo;
import server.rpc.dto.SessionStateChanged;
import server.rpc.dto.Stop;

/**
 * Manages the current audio session: tracks state transitions, delegates to the audio engine, and
 * publishes state change events. Maintains only essential state like current file and handles.
 */
@Slf4j
@Service
public class AudioPlaybackService implements PlaybackListener {

    private final AudioPlaybackStateMachine stateManager;
    private final AudioEngine audioEngine;
    private final ClientGateway clientGateway;
    private final ExecutorService edt;

    private Optional<File> currentFile = Optional.empty();
    private Optional<AudioHandle> currentAudioHandle = Optional.empty();
    private Optional<PlaybackHandle> currentPlaybackHandle = Optional.empty();
    private String errorMessage = null;

    // Progress tracking
    private volatile long currentPositionFrames = 0;
    private volatile long totalFrames = 0;
    private volatile int sampleRate = 0;

    public AudioPlaybackService(
            @NonNull AudioPlaybackStateMachine stateManager,
            @NonNull AudioEngine audioEngine,
            @NonNull ClientGateway clientGateway,
            @Qualifier("edt") ExecutorService edt) {
        this.stateManager = stateManager;
        this.audioEngine = audioEngine;
        this.clientGateway = clientGateway;
        this.edt = edt;
        this.audioEngine.addPlaybackListener(this);
    }

    // JSON-RPC request handlers (server-side entrypoints)

    public void loadAudio(@NonNull LoadAudio event) {
        var file = new File(event.filePath());
        log.debug("Loading audio file: {}", file.getAbsolutePath());

        // Close current file if any
        closeCurrentSession();

        // Transition to loading state
        var previousState = stateManager.getCurrentState();
        stateManager.transitionToLoading();
        currentFile = Optional.of(file);
        clientGateway.sessionStateChanged(
                new SessionStateChanged(
                        previousState, AudioPlaybackStateMachine.State.LOADING, file));

        // Load the audio file
        try {
            var handle = audioEngine.loadAudio(file.getAbsolutePath());
            currentAudioHandle = Optional.of(handle);

            // Cache metadata for efficient access
            var metadata = audioEngine.getMetadata(handle);
            this.sampleRate = metadata.sampleRate();
            this.totalFrames = metadata.frameCount();
            this.currentPositionFrames = 0;

            var prevState = stateManager.getCurrentState();
            stateManager.transitionToReady();
            clientGateway.sessionStateChanged(
                    new SessionStateChanged(
                            prevState, AudioPlaybackStateMachine.State.READY, file));
            log.info("Audio file loaded successfully: {}", file.getName());
        } catch (Exception e) {
            errorMessage = e.getMessage();
            var prevState = stateManager.getCurrentState();
            stateManager.transitionToError();
            clientGateway.sessionStateChanged(
                    new SessionStateChanged(
                            prevState, AudioPlaybackStateMachine.State.ERROR, e.getMessage()));
            log.error("Failed to load audio file: {}", file.getAbsolutePath(), e);
        }
    }

    public void playPause(@NonNull PlayPause event) {
        var state = stateManager.getCurrentState();

        switch (state) {
            case READY -> {
                // Start playback from beginning
                currentAudioHandle.ifPresent(
                        handle -> {
                            var playback = audioEngine.play(handle);
                            currentPlaybackHandle = Optional.of(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var position = audioEngine.getPosition(playback);
                            clientGateway.sessionStateChanged(
                                    new SessionStateChanged(
                                            prevState,
                                            AudioPlaybackStateMachine.State.PLAYING,
                                            position));
                            log.debug("Started playback");
                        });
            }
            case PLAYING -> {
                // Pause playback
                currentPlaybackHandle.ifPresent(
                        playback -> {
                            audioEngine.pause(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPaused();
                            var position = audioEngine.getPosition(playback);
                            clientGateway.sessionStateChanged(
                                    new SessionStateChanged(
                                            prevState,
                                            AudioPlaybackStateMachine.State.PAUSED,
                                            position));
                            log.debug("Paused playback");
                        });
            }
            case PAUSED -> {
                // Resume playback
                currentPlaybackHandle.ifPresent(
                        playback -> {
                            audioEngine.resume(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var position = audioEngine.getPosition(playback);
                            clientGateway.sessionStateChanged(
                                    new SessionStateChanged(
                                            prevState,
                                            AudioPlaybackStateMachine.State.PLAYING,
                                            position));
                            log.debug("Resumed playback");
                        });
            }
            default -> log.warn("Play/pause requested in invalid state: {}", state);
        }
    }

    public void closeAudio(@NonNull CloseAudio event) {
        log.debug("Closing audio file");
        var fileBeingClosed = currentFile.orElse(null);
        var prevState = stateManager.getCurrentState();
        closeCurrentSession();
        stateManager.transitionToNoAudio();
        clientGateway.sessionStateChanged(
                new SessionStateChanged(
                        prevState, AudioPlaybackStateMachine.State.NO_AUDIO, fileBeingClosed));
    }

    public void seekBy(@NonNull SeekBy event) {
        // Calculate target frame based on current position and requested amount
        long shiftFrames = (long) ((event.milliseconds() / 1000.0) * sampleRate);
        long targetFrame =
                (event.forward())
                        ? currentPositionFrames + shiftFrames
                        : currentPositionFrames - shiftFrames;

        // Ensure within bounds
        targetFrame = Math.max(0, Math.min(targetFrame, totalFrames - 1));

        // Delegate to existing seek logic
        seekTo(new SeekTo(targetFrame));
    }

    public void seekTo(@NonNull SeekTo event) {
        var state = stateManager.getCurrentState();

        if (currentPlaybackHandle.isPresent()) {
            // If we have a playback handle (playing or paused), just seek it
            audioEngine.seek(currentPlaybackHandle.get(), event.frame());
            currentPositionFrames = event.frame(); // Update cached position
            log.debug("Seeked to frame: {}", event.frame());
        } else if (state == AudioPlaybackStateMachine.State.READY
                && currentAudioHandle.isPresent()) {
            // TODO: Fix audio glitch when seeking in READY state
            // Problem: We need a playback handle to seek, but creating one via play()
            // causes a brief moment of audio playback before pause() takes effect.
            // This creates an audible glitch when seeking from READY state.
            // Possible solutions:
            // 1. Track desired position without playback handle, apply on play
            // 2. Add engine support for creating paused playback handles
            // 3. Use play(startFrame, startFrame+1) with immediate stop

            // If in READY state with no playback handle, create one and immediately pause it
            var playback = audioEngine.play(currentAudioHandle.get());
            currentPlaybackHandle = Optional.of(playback);
            audioEngine.pause(playback);
            audioEngine.seek(playback, event.frame());
            currentPositionFrames = event.frame();

            // Transition to PAUSED since we now have a paused playback handle
            var prevState = stateManager.getCurrentState();
            stateManager.transitionToPaused();
            clientGateway.sessionStateChanged(
                    new SessionStateChanged(
                            prevState, AudioPlaybackStateMachine.State.PAUSED, event.frame()));

            log.debug("Created paused playback and seeked to frame: {}", event.frame());
        }
    }

    public void stop(@NonNull Stop event) {
        var state = stateManager.getCurrentState();

        if (state == AudioPlaybackStateMachine.State.PLAYING) {
            // Stop playback and reset position to beginning
            stopPlayback();
            log.debug("Stopped playback and reset to beginning");
        }
    }

    public void replayLast(@NonNull ReplayLast event) {
        var state = stateManager.getCurrentState();

        if ((state == AudioPlaybackStateMachine.State.READY
                        || state == AudioPlaybackStateMachine.State.PAUSED)
                && currentAudioHandle.isPresent()) {

            // Get current position
            long currentFrame = 0;
            if (currentPlaybackHandle.isPresent()) {
                currentFrame = audioEngine.getPosition(currentPlaybackHandle.get());
            }

            // Calculate 200ms in frames
            int currentSampleRate = this.sampleRate != 0 ? this.sampleRate : 44100; // default
            long framesToReplay = (long) (currentSampleRate * (event.windowMillis() / 1000.0));
            long startFrame = Math.max(0, currentFrame - framesToReplay);
            long endFrame = currentFrame;

            // Play the interval from 200ms ago to current position
            audioEngine.play(currentAudioHandle.get(), startFrame, endFrame);

            log.debug("Replaying last 200ms from frame {} to {}", startFrame, endFrame);
        }
    }

    public void replayNudge(@NonNull ReplayNudge event) {
        var state = stateManager.getCurrentState();

        if ((state == AudioPlaybackStateMachine.State.READY
                        || state == AudioPlaybackStateMachine.State.PAUSED)
                && currentPlaybackHandle.isPresent()
                && currentAudioHandle.isPresent()) {

            // Get current position
            long currentFrame = audioEngine.getPosition(currentPlaybackHandle.get());

            int currentSampleRate = this.sampleRate != 0 ? this.sampleRate : 44100;
            long shiftFrames = (long) (currentSampleRate * (event.windowMillis() / 1000.0));

            // Calculate new position based on direction
            long newFrame =
                    event.forward() ? currentFrame + shiftFrames : currentFrame - shiftFrames;

            // Ensure we don't go out of bounds
            newFrame = Math.max(0, Math.min(newFrame, totalFrames - 1));

            // Seek to the new position
            audioEngine.seek(currentPlaybackHandle.get(), newFrame);

            // Now replay the last 200ms from this new position
            long replayStartFrame = Math.max(0, newFrame - shiftFrames);
            long replayEndFrame = newFrame;

            // Play the interval
            audioEngine.play(currentAudioHandle.get(), replayStartFrame, replayEndFrame);

            log.debug(
                    "Moved {} to frame {} and replaying from {} to {}",
                    event.forward() ? "forward" : "backward",
                    newFrame,
                    replayStartFrame,
                    replayEndFrame);
        }
    }

    // PlaybackListener
    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Store progress for efficient access
        this.currentPositionFrames = positionFrames;
        this.totalFrames = totalFrames;
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle handle,
            @NonNull audio.PlaybackState newState,
            @NonNull audio.PlaybackState oldState) {
        edt.execute(
                () -> {
                    // Handle state changes from audio engine on EDT
                    log.debug("Playback state changed: {} -> {}", oldState, newState);
                    if (newState == audio.PlaybackState.ERROR
                            && stateManager.getCurrentState()
                                    == AudioPlaybackStateMachine.State.PLAYING) {
                        var prevState = stateManager.getCurrentState();
                        stateManager.transitionToError();
                        errorMessage = "Playback error occurred";
                        clientGateway.sessionStateChanged(
                                new SessionStateChanged(
                                        prevState,
                                        AudioPlaybackStateMachine.State.ERROR,
                                        "Playback error"));
                    }
                });
    }

    @Override
    public void onPlaybackComplete(@NonNull PlaybackHandle playback) {
        // Ensure completion is processed on EDT
        edt.execute(
                () -> {
                    var prevState = stateManager.getCurrentState();
                    stateManager.transitionToReady();
                    currentPlaybackHandle = Optional.empty();
                    currentPositionFrames = 0; // Reset position
                    clientGateway.sessionStateChanged(
                            new SessionStateChanged(
                                    prevState, AudioPlaybackStateMachine.State.READY, "completed"));
                    log.debug("Playback completed");
                });
    }

    // WaveformSessionSource implementation

    public Optional<Double> getPlaybackPosition() {
        if (currentPlaybackHandle.isEmpty() || sampleRate == 0) {
            return Optional.empty();
        }
        // Use cached position for efficiency
        return Optional.of((double) currentPositionFrames / sampleRate);
    }

    public Optional<Double> getTotalDuration() {
        if (currentAudioHandle.isEmpty() || sampleRate == 0) {
            return Optional.empty();
        }
        // Use cached values for efficiency
        return Optional.of((double) totalFrames / sampleRate);
    }

    public boolean isAudioLoaded() {
        return stateManager.isAudioLoaded();
    }

    public boolean isPlaying() {
        return stateManager.getCurrentState() == AudioPlaybackStateMachine.State.PLAYING;
    }

    public boolean isLoading() {
        return stateManager.getCurrentState() == AudioPlaybackStateMachine.State.LOADING;
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public Optional<AudioHandle> getCurrentAudioHandle() {
        return currentAudioHandle;
    }

    public Optional<String> getCurrentAudioFilePath() {
        return currentFile.map(f -> f.getAbsolutePath());
    }

    public Optional<Integer> getSampleRate() {
        return sampleRate > 0 ? Optional.of(sampleRate) : Optional.empty();
    }

    public Optional<Long> getPlaybackPositionFrames() {
        if (currentPlaybackHandle.isEmpty()) {
            return Optional.empty();
        }
        // Return the exact frame position without conversion
        return Optional.of(currentPositionFrames);
    }

    public Optional<Long> getTotalFrames() {
        if (currentAudioHandle.isEmpty() || totalFrames == 0) {
            return Optional.empty();
        }
        return Optional.of(totalFrames);
    }

    /**
     * Stop playback and transition to READY state. Used when user explicitly stops playback (not
     * pause).
     */
    public void stopPlayback() {
        if (currentPlaybackHandle.isPresent()) {
            var prevState = stateManager.getCurrentState();
            audioEngine.stop(currentPlaybackHandle.get());
            currentPlaybackHandle.get().close();
            currentPlaybackHandle = Optional.empty();
            currentPositionFrames = 0; // Reset position on stop

            if (prevState == AudioPlaybackStateMachine.State.PLAYING
                    || prevState == AudioPlaybackStateMachine.State.PAUSED) {
                stateManager.transitionToReady();
                clientGateway.sessionStateChanged(
                        new SessionStateChanged(
                                prevState, AudioPlaybackStateMachine.State.READY, "stopped"));
                log.debug("Playback stopped");
            }
        }
    }

    private void closeCurrentSession() {
        // Stop any active playback
        if (currentPlaybackHandle.isPresent()) {
            audioEngine.stop(currentPlaybackHandle.get());
            currentPlaybackHandle.get().close();
        }
        currentPlaybackHandle = Optional.empty();

        // Clear handles (audio handle doesn't need explicit closing)
        currentAudioHandle = Optional.empty();

        // Clear state and cached values
        currentFile = Optional.empty();
        errorMessage = null;
        currentPositionFrames = 0;
        totalFrames = 0;
        sampleRate = 0;
    }
}
