package server.rpc.dto;

public record ReplayNudge(long windowMillis, boolean forward) {}
