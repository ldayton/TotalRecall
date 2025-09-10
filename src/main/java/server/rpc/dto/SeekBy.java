package server.rpc.dto;

public record SeekBy(long milliseconds, boolean forward) {}
