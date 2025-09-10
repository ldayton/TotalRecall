package server.rpc.dto;

import playback.AudioPlaybackStateMachine;

public record SessionStateChanged(
        AudioPlaybackStateMachine.State previous,
        AudioPlaybackStateMachine.State current,
        Object context) {}
