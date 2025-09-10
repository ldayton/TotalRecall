package server.rpc;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import server.rpc.dto.SessionStateChanged;

public interface ClientApi {

    @JsonNotification("session/stateChanged")
    void sessionStateChanged(SessionStateChanged payload);
}

