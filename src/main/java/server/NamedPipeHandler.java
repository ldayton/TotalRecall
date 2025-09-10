package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NamedPipeHandler {
    private static final String PIPE_PATH = "/tmp/totalrecall";
    private RandomAccessFile pipe;

    public void createNamedPipe() throws IOException, InterruptedException {
        Path pipePath = Paths.get(PIPE_PATH);

        // Remove existing pipe if it exists
        if (Files.exists(pipePath)) {
            Files.delete(pipePath);
            log.info("Removed existing named pipe at {}", PIPE_PATH);
        }

        // Create the named pipe using mkfifo
        ProcessBuilder pb = new ProcessBuilder("mkfifo", PIPE_PATH);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Failed to create named pipe at " + PIPE_PATH);
        }

        log.info("Created named pipe at {}", PIPE_PATH);
    }

    public InputStream getInputStream() throws IOException {
        if (pipe == null) {
            pipe = new RandomAccessFile(PIPE_PATH, "rw");
        }
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return pipe.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return pipe.read(b, off, len);
            }
        };
    }

    public OutputStream getOutputStream() throws IOException {
        if (pipe == null) {
            pipe = new RandomAccessFile(PIPE_PATH, "rw");
        }
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                pipe.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                pipe.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                // RandomAccessFile doesn't need explicit flush
            }
        };
    }

    public void cleanup() {
        try {
            if (pipe != null) {
                pipe.close();
            }
            Files.deleteIfExists(Paths.get(PIPE_PATH));
            log.info("Cleaned up named pipe at {}", PIPE_PATH);
        } catch (IOException e) {
            log.error("Error cleaning up named pipe", e);
        }
    }
}
