package server.rpc;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import playback.AudioPlaybackService;
import server.rpc.dto.CloseAudio;
import server.rpc.dto.LoadAudio;
import server.rpc.dto.PlayPause;
import server.rpc.dto.Pong;
import server.rpc.dto.ReplayLast;
import server.rpc.dto.ReplayNudge;
import server.rpc.dto.SeekBy;
import server.rpc.dto.SeekTo;
import server.rpc.dto.Stop;

@Service
@Import({
    AudioPlaybackService.class,
    playback.AudioPlaybackStateMachine.class,
    EventDispatchThreadConfig.class
})
public class JsonRpcService {
    private final AudioPlaybackService session;
    private final ExecutorService edt;

    public JsonRpcService(AudioPlaybackService session, @Qualifier("edt") ExecutorService edt) {
        this.session = session;
        this.edt = edt;
    }

    private <T> CompletableFuture<T> onEdt(Callable<T> task) {
        var cf = new CompletableFuture<T>();
        edt.execute(
                () -> {
                    try {
                        cf.complete(task.call());
                    } catch (Throwable t) {
                        cf.completeExceptionally(t);
                    }
                });
        return cf;
    }

    @JsonRequest("ping")
    public CompletableFuture<Pong> ping() {
        return onEdt(Pong::new);
    }

    @JsonRequest("audio/load")
    public CompletableFuture<Void> load(LoadAudio req) {
        return onEdt(
                () -> {
                    session.loadAudio(req);
                    return null;
                });
    }

    @JsonRequest("audio/playPause")
    public CompletableFuture<Void> playPause(PlayPause req) {
        return onEdt(
                () -> {
                    session.playPause(req);
                    return null;
                });
    }

    @JsonRequest("audio/close")
    public CompletableFuture<Void> close(CloseAudio req) {
        return onEdt(
                () -> {
                    session.closeAudio(req);
                    return null;
                });
    }

    @JsonRequest("audio/seekBy")
    public CompletableFuture<Void> seekBy(SeekBy req) {
        return onEdt(
                () -> {
                    session.seekBy(req);
                    return null;
                });
    }

    @JsonRequest("audio/seekTo")
    public CompletableFuture<Void> seekTo(SeekTo req) {
        return onEdt(
                () -> {
                    session.seekTo(req);
                    return null;
                });
    }

    @JsonRequest("audio/stop")
    public CompletableFuture<Void> stop(Stop req) {
        return onEdt(
                () -> {
                    session.stop(req);
                    return null;
                });
    }

    @JsonRequest("audio/replayLast")
    public CompletableFuture<Void> replayLast(ReplayLast req) {
        return onEdt(
                () -> {
                    session.replayLast(req);
                    return null;
                });
    }

    @JsonRequest("audio/replayNudge")
    public CompletableFuture<Void> replayNudge(ReplayNudge req) {
        return onEdt(
                () -> {
                    session.replayNudge(req);
                    return null;
                });
    }
}
