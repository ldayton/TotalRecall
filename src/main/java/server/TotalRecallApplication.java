package server;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"server", "audio", "playback"}, proxyBeanMethods = false)
public class TotalRecallApplication {

    public static void main(String[] args) throws Exception {
        var ctx = SpringApplication.run(TotalRecallApplication.class, args);
        var rpc = ctx.getBean(server.rpc.JsonRpcService.class);
        Launcher<server.rpc.ClientApi> launcher = new Launcher.Builder<server.rpc.ClientApi>()
                .setLocalService(rpc)
                .setRemoteInterface(server.rpc.ClientApi.class)
                .setInput(System.in)
                .setOutput(System.out)
                .create();
        var client = launcher.getRemoteProxy();
        ctx.getBean(server.rpc.ClientGateway.class).setClient(client);
        launcher.startListening().get();
    }
}
