package server;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(
        scanBasePackages = {"server", "audio", "playback"},
        proxyBeanMethods = false)
public class TotalRecallApplication {

    public static void main(String[] args) throws Exception {
        var ctx = SpringApplication.run(TotalRecallApplication.class, args);

        // Set up Unix domain socket
        var socketHandler = ctx.getBean(UnixSocketHandler.class);
        socketHandler.createUnixSocket();

        // Register shutdown hook to clean up socket
        Runtime.getRuntime().addShutdownHook(new Thread(socketHandler::cleanup));

        log.info("Waiting for connection on Unix socket /tmp/totalrecall...");
        
        // Wait for client to connect
        while (!socketHandler.isClientConnected()) {
            Thread.sleep(100);
        }

        var rpc = ctx.getBean(server.rpc.JsonRpcService.class);
        Launcher<server.rpc.ClientApi> launcher =
                new Launcher.Builder<server.rpc.ClientApi>()
                        .setLocalService(rpc)
                        .setRemoteInterface(server.rpc.ClientApi.class)
                        .setInput(socketHandler.getInputStream())
                        .setOutput(socketHandler.getOutputStream())
                        .create();
        var client = launcher.getRemoteProxy();
        ctx.getBean(server.rpc.ClientGateway.class).setClient(client);

        log.info("JSON-RPC server connected via Unix socket /tmp/totalrecall");
        launcher.startListening().get();
    }
}
