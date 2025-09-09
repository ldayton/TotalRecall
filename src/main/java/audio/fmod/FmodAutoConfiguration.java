package audio.fmod;

import audio.AudioEngine;
import audio.SampleReader;
import com.sun.jna.Pointer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FmodProperties.class)
@ConditionalOnClass(FmodLibrary.class)
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
    public FmodLibrary fmodLibrary(FmodSystemManager systemManager) {
        if (!systemManager.isInitialized()) {
            systemManager.initialize();
        }
        return systemManager.getFmodLibrary();
    }

    @Bean
    public Pointer fmodSystemPointer(FmodSystemManager systemManager) {
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
            FmodLibrary fmod,
            Pointer fmodSystemPointer,
            FmodSystemStateManager stateManager,
            FmodHandleLifecycleManager lifecycleManager) {
        return new FmodAudioLoadingManager(fmod, fmodSystemPointer, stateManager, lifecycleManager);
    }

    @Bean
    public FmodPlaybackManager fmodPlaybackManager(FmodLibrary fmod, Pointer fmodSystemPointer) {
        return new FmodPlaybackManager(fmod, fmodSystemPointer);
    }

    @Bean
    public FmodListenerManager fmodListenerManager(FmodLibrary fmod, Pointer fmodSystemPointer) {
        return new FmodListenerManager(fmod, fmodSystemPointer);
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
