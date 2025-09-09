package audio.fmod;

import audio.AudioEngine;
import audio.SampleReader;
import java.lang.foreign.MemorySegment;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FmodProperties.class)
public class FmodAutoConfiguration {

    @Bean
    public FmodLibraryLoader fmodLibraryLoader(FmodProperties properties) {
        return new FmodLibraryLoader(properties);
    }

    @Bean
    public FmodSystemStateManager fmodSystemStateManager() {
        return new FmodSystemStateManager();
    }

    @Bean(destroyMethod = "shutdown")
    public FmodSystemManager fmodSystemManager(FmodLibraryLoader loader) {
        return new FmodSystemManager(loader);
    }

    @Bean
    public MemorySegment fmodSystemPointer(FmodSystemManager systemManager) {
        if (!systemManager.isInitialized()) {
            systemManager.initialize();
        }
        return systemManager.getSystem();
    }

    @Bean
    public FmodHandleLifecycleManager fmodHandleLifecycleManager() {
        return new FmodHandleLifecycleManager();
    }

    @Bean
    public FmodAudioLoadingManager fmodAudioLoadingManager(
            MemorySegment fmodSystemPointer,
            FmodSystemStateManager stateManager,
            FmodHandleLifecycleManager lifecycleManager) {
        return new FmodAudioLoadingManager(fmodSystemPointer, stateManager, lifecycleManager);
    }

    @Bean
    public FmodPlaybackManager fmodPlaybackManager(MemorySegment fmodSystemPointer) {
        return new FmodPlaybackManager(fmodSystemPointer);
    }

    @Bean
    public FmodListenerManager fmodListenerManager(MemorySegment fmodSystemPointer) {
        return new FmodListenerManager(fmodSystemPointer);
    }

    @Bean
    public AudioEngine audioEngine(
            FmodSystemManager systemManager,
            FmodAudioLoadingManager loadingManager,
            FmodPlaybackManager playbackManager,
            FmodListenerManager listenerManager,
            FmodSystemStateManager stateManager,
            FmodHandleLifecycleManager lifecycleManager) {
        return new FmodAudioEngine(
                systemManager,
                loadingManager,
                playbackManager,
                listenerManager,
                stateManager,
                lifecycleManager);
    }

    @Bean
    public SampleReader sampleReader(FmodLibraryLoader loader) {
        return new FmodSampleReader(loader);
    }
}
