package server.rpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventDispatchThreadConfig {

    @Bean(name = "edt", destroyMethod = "shutdown")
    public ExecutorService edt() {
        return Executors.newSingleThreadExecutor(
                r -> {
                    Thread t = new Thread(r, "EventDispatchThread");
                    t.setDaemon(true);
                    return t;
                });
    }
}
