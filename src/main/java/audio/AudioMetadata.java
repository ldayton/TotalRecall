package audio;

import annotations.ThreadSafe;

@ThreadSafe
public record AudioMetadata(
        int sampleRate,
        int channelCount,
        int bitsPerSample,
        String format,
        long frameCount,
        double durationSeconds) {}
