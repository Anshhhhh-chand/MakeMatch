package com.example.gamematch.player.model;

public enum GameMode {
    RANKED_1V1(2),
    CASUAL_1V1(2),
    RANKED_2V2(4);

    private final int requiredPlayers;

    GameMode(int requiredPlayers) {
        this.requiredPlayers = requiredPlayers;
    }

    public int getRequiredPlayers() {
        return requiredPlayers;
    }
}
