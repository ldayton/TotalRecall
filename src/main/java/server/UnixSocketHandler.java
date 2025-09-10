package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UnixSocketHandler {
    private static final String SOCKET_PATH = "/tmp/totalrecall";
    private ServerSocketChannel serverChannel;
    private SocketChannel clientChannel;
    
    public void createUnixSocket() throws IOException {
        Path socketPath = Path.of(SOCKET_PATH);
        
        // Remove existing socket if it exists
        if (Files.exists(socketPath)) {
            Files.delete(socketPath);
            log.info("Removed existing socket at {}", SOCKET_PATH);
        }
        
        // Create Unix domain socket
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.bind(address);
        
        log.info("Created Unix domain socket at {}", SOCKET_PATH);
        
        // Start a thread to accept connections
        new Thread(() -> {
            try {
                log.info("Waiting for client connection on Unix socket...");
                clientChannel = serverChannel.accept();
                log.info("Client connected to Unix socket");
            } catch (IOException e) {
                log.error("Error accepting Unix socket connection", e);
            }
        }).start();
    }
    
    public InputStream getInputStream() throws IOException {
        if (clientChannel == null) {
            throw new IOException("No client connected to Unix socket");
        }
        return Channels.newInputStream(clientChannel);
    }
    
    public OutputStream getOutputStream() throws IOException {
        if (clientChannel == null) {
            throw new IOException("No client connected to Unix socket");
        }
        return Channels.newOutputStream(clientChannel);
    }
    
    public boolean isClientConnected() {
        return clientChannel != null && clientChannel.isOpen();
    }
    
    public void cleanup() {
        try {
            if (clientChannel != null) {
                clientChannel.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            Files.deleteIfExists(Path.of(SOCKET_PATH));
            log.info("Cleaned up Unix socket at {}", SOCKET_PATH);
        } catch (IOException e) {
            log.error("Error cleaning up Unix socket", e);
        }
    }
}