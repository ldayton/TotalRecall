package server.rpc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import server.rpc.dto.SessionStateChanged;

@Component
@Slf4j
public class ClientGateway {

    private volatile ClientApi client;

    public void setClient(ClientApi client) {
        this.client = client;
    }

    public void sessionStateChanged(SessionStateChanged payload) {
        ClientApi c = this.client;
        if (c != null) {
            try {
                c.sessionStateChanged(payload);
            } catch (Throwable t) {
                log.warn("Failed to notify client: sessionStateChanged", t);
            }
        } else {
            log.debug("Client not connected; dropping sessionStateChanged: {}", payload);
        }
    }
}
