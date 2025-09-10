package server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import playback.AudioPlaybackService;
import playback.AudioPlaybackStateMachine;
import server.rpc.ClientGateway;
import audio.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import server.rpc.JsonRpcService;
import server.rpc.dto.Pong;

@SpringBootTest(classes = {TotalRecallApplication.class, server.rpc.JsonRpcService.class, server.JsonRpcServiceTest.StubsConfig.class})
class JsonRpcServiceTest {

    @Autowired private JsonRpcService service;

    @Test
    void pingReturnsPongRecord() throws Exception {
        Pong pong = service.ping().get(2, TimeUnit.SECONDS);
        assertEquals(new Pong(), pong);
    }

    @TestConfiguration
    static class StubsConfig {
        @Bean
        @Primary
        ClientGateway clientGateway() {
            return new ClientGateway();
        }

        @Bean
        @Primary
        AudioPlaybackStateMachine audioPlaybackStateMachine() {
            return new AudioPlaybackStateMachine();
        }

        @Bean
        @Primary
        AudioPlaybackService audioPlaybackService(
                AudioPlaybackStateMachine sm,
                AudioEngine engine,
                ClientGateway gw,
                @org.springframework.beans.factory.annotation.Qualifier("edt") java.util.concurrent.ExecutorService edt) {
            return new AudioPlaybackService(sm, engine, gw, edt);
        }

        @Bean
        AudioEngine audioEngine() {
            return new AudioEngine() {
                @Override
                public AudioHandle loadAudio(String filePath) { return null; }
                @Override
                public PlaybackHandle play(AudioHandle audio) { return null; }
                @Override
                public PlaybackHandle play(AudioHandle audio, long startFrame, long endFrame) { return null; }
                @Override
                public void pause(PlaybackHandle playback) {}
                @Override
                public void resume(PlaybackHandle playback) {}
                @Override
                public void stop(PlaybackHandle playback) {}
                @Override
                public void seek(PlaybackHandle playback, long frame) {}
                @Override
                public PlaybackState getState(PlaybackHandle playback) { return PlaybackState.STOPPED; }
                @Override
                public long getPosition(PlaybackHandle playback) { return 0; }
                @Override
                public boolean isPlaying(PlaybackHandle playback) { return false; }
                @Override
                public boolean isPaused(PlaybackHandle playback) { return false; }
                @Override
                public boolean isStopped(PlaybackHandle playback) { return true; }
                @Override
                public AudioMetadata getMetadata(AudioHandle audio) {
                    return new AudioMetadata(44100, 2, 16, "unknown", 0L, 0.0);
                }
                @Override
                public void addPlaybackListener(PlaybackListener listener) {}
                @Override
                public void removePlaybackListener(PlaybackListener listener) {}
                @Override
                public void close() {}
            };
        }
    }
}
