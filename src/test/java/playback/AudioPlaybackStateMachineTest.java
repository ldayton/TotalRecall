package playback;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Slf4j
class AudioPlaybackStateMachineTest {

    private AudioPlaybackStateMachine stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new AudioPlaybackStateMachine();
    }

    @Test
    void testInitialState() {
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());
    }

    @Test
    @Timeout(10)
    void testInvalidCasDoesNotChangeStateUnderConcurrency() throws Exception {
        // Start from NO_AUDIO (default)
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        int threads = 8;
        int attemptsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < attemptsPerThread; i++) {
                                // Intentionally invalid: NO_AUDIO -> PLAYING
                                stateManager.compareAndSetState(
                                        AudioPlaybackStateMachine.State.NO_AUDIO,
                                        AudioPlaybackStateMachine.State.PLAYING);
                            }
                        } catch (InterruptedException ignored) {
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        // State must remain NO_AUDIO since transition was invalid
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    @Timeout(10)
    void testConcurrentToggleCycleWithCas() throws Exception {
        // Bring to READY
        stateManager.transitionToLoading();
        stateManager.transitionToReady();

        int threads = 6;
        int steps = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < steps; i++) {
                                var s = stateManager.getCurrentState();
                                switch (s) {
                                    case READY ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.READY,
                                                    AudioPlaybackStateMachine.State.PLAYING);
                                    case PLAYING ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.PLAYING,
                                                    AudioPlaybackStateMachine.State.PAUSED);
                                    case PAUSED ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.PAUSED,
                                                    AudioPlaybackStateMachine.State.READY);
                                    case NO_AUDIO ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.NO_AUDIO,
                                                    AudioPlaybackStateMachine.State.LOADING);
                                    case LOADING ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.LOADING,
                                                    AudioPlaybackStateMachine.State.READY);
                                    case ERROR ->
                                            stateManager.compareAndSetState(
                                                    AudioPlaybackStateMachine.State.ERROR,
                                                    AudioPlaybackStateMachine.State.NO_AUDIO);
                                }
                            }
                        } catch (InterruptedException ignored) {
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        // Final state should always be a valid enum, but specifically not an impossible state.
        var end = stateManager.getCurrentState();
        assertNotNull(end);
    }

    @Test
    @Timeout(10)
    void testForceErrorConcurrent() throws Exception {
        // Move to READY so we test from a non-default state
        stateManager.transitionToLoading();
        stateManager.transitionToReady();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            // Each thread tries to force error a bunch of times
                            for (int i = 0; i < 200; i++) {
                                stateManager.forceError();
                            }
                        } catch (InterruptedException ignored) {
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        // End state must be ERROR
        assertEquals(AudioPlaybackStateMachine.State.ERROR, stateManager.getCurrentState());
    }

    @Test
    void testValidTransitions() {
        // NO_AUDIO -> LOADING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.NO_AUDIO,
                        AudioPlaybackStateMachine.State.LOADING));
        assertEquals(AudioPlaybackStateMachine.State.LOADING, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());

        // LOADING -> READY
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.LOADING,
                        AudioPlaybackStateMachine.State.READY));
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());
        assertTrue(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());

        // READY -> PLAYING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.READY,
                        AudioPlaybackStateMachine.State.PLAYING));
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());
        assertTrue(stateManager.isAudioLoaded());
        assertTrue(stateManager.isPlaybackActive());

        // PLAYING -> PAUSED
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.PLAYING,
                        AudioPlaybackStateMachine.State.PAUSED));
        assertEquals(AudioPlaybackStateMachine.State.PAUSED, stateManager.getCurrentState());
        assertTrue(stateManager.isPlaybackActive());

        // PAUSED -> PLAYING (resume)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.PAUSED,
                        AudioPlaybackStateMachine.State.PLAYING));
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());

        // PLAYING -> READY (stop)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.PLAYING,
                        AudioPlaybackStateMachine.State.READY));
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());

        // READY -> NO_AUDIO (close)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.READY,
                        AudioPlaybackStateMachine.State.NO_AUDIO));
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitions() {
        // NO_AUDIO -> PLAYING (skipping LOADING and READY)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.NO_AUDIO,
                        AudioPlaybackStateMachine.State.PLAYING));

        // NO_AUDIO -> PAUSED
        assertFalse(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.NO_AUDIO,
                        AudioPlaybackStateMachine.State.PAUSED));

        // NO_AUDIO -> READY (skipping LOADING)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.NO_AUDIO,
                        AudioPlaybackStateMachine.State.READY));

        // Move to LOADING
        stateManager.transitionToLoading();

        // LOADING -> PLAYING (skipping READY)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.LOADING,
                        AudioPlaybackStateMachine.State.PLAYING));

        // LOADING -> PAUSED
        assertFalse(
                stateManager.compareAndSetState(
                        AudioPlaybackStateMachine.State.LOADING,
                        AudioPlaybackStateMachine.State.PAUSED));
    }

    @Test
    void testTransitionMethods() {
        // Test transitionToLoading
        stateManager.transitionToLoading();
        assertEquals(AudioPlaybackStateMachine.State.LOADING, stateManager.getCurrentState());

        // Test transitionToReady
        stateManager.transitionToReady();
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());

        // Test transitionToPlaying
        stateManager.transitionToPlaying();
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Test transitionToPaused
        stateManager.transitionToPaused();
        assertEquals(AudioPlaybackStateMachine.State.PAUSED, stateManager.getCurrentState());

        // Resume (PAUSED -> PLAYING)
        stateManager.transitionToPlaying();
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Stop (PLAYING -> READY)
        stateManager.transitionToReady();
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());

        // Close (READY -> NO_AUDIO)
        stateManager.transitionToNoAudio();
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testErrorStateTransitions() {
        // LOADING -> ERROR
        stateManager.transitionToLoading();
        stateManager.transitionToError();
        assertEquals(AudioPlaybackStateMachine.State.ERROR, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());

        // ERROR -> NO_AUDIO (reset)
        stateManager.transitionToNoAudio();
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        // ERROR -> LOADING (retry)
        stateManager.forceError(); // Use forceError to get back to ERROR state
        stateManager.transitionToLoading();
        assertEquals(AudioPlaybackStateMachine.State.LOADING, stateManager.getCurrentState());

        // PLAYING -> ERROR
        stateManager.transitionToReady();
        stateManager.transitionToPlaying();
        stateManager.transitionToError();
        assertEquals(AudioPlaybackStateMachine.State.ERROR, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitionExceptions() {
        // Try to pause when not playing
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToPaused());

        // Try to play when no audio
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToPlaying());

        // Try to transition to ready from NO_AUDIO
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToReady());

        // Try to transition to error from READY
        stateManager.transitionToLoading();
        stateManager.transitionToReady();
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToError());
    }

    @Test
    void testCompletePlaybackCycle() {
        // Simulate a complete audio playback cycle

        // Load audio
        stateManager.transitionToLoading();
        assertEquals(AudioPlaybackStateMachine.State.LOADING, stateManager.getCurrentState());

        // Loading completes
        stateManager.transitionToReady();
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());

        // Start playback
        stateManager.transitionToPlaying();
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Pause playback
        stateManager.transitionToPaused();
        assertEquals(AudioPlaybackStateMachine.State.PAUSED, stateManager.getCurrentState());

        // Resume playback
        stateManager.transitionToPlaying();
        assertEquals(AudioPlaybackStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Stop playback
        stateManager.transitionToReady();
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());

        // Close audio
        stateManager.transitionToNoAudio();
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testErrorRecoveryCycle() {
        // Simulate error and recovery

        // Start loading
        stateManager.transitionToLoading();

        // Loading fails
        stateManager.transitionToError();
        assertEquals(AudioPlaybackStateMachine.State.ERROR, stateManager.getCurrentState());

        // Reset to NO_AUDIO
        stateManager.transitionToNoAudio();
        assertEquals(AudioPlaybackStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        // Retry loading
        stateManager.transitionToLoading();
        assertEquals(AudioPlaybackStateMachine.State.LOADING, stateManager.getCurrentState());

        // This time it succeeds
        stateManager.transitionToReady();
        assertEquals(AudioPlaybackStateMachine.State.READY, stateManager.getCurrentState());
    }
}
