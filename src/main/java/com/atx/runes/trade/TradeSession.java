package com.atx.runes.trade;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class TradeSession {
    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final Instant createdAt;
    private UUID firstOffer;
    private UUID secondOffer;
    private boolean firstAccepted;
    private boolean secondAccepted;

    public TradeSession(UUID firstPlayer, UUID secondPlayer) {
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        this.createdAt = Instant.now();
    }

    public UUID firstPlayer() {
        return firstPlayer;
    }

    public UUID secondPlayer() {
        return secondPlayer;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<UUID> offerFor(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            return Optional.ofNullable(firstOffer);
        }
        if (secondPlayer.equals(playerId)) {
            return Optional.ofNullable(secondOffer);
        }
        return Optional.empty();
    }

    public Optional<UUID> offerAgainst(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            return Optional.ofNullable(secondOffer);
        }
        if (secondPlayer.equals(playerId)) {
            return Optional.ofNullable(firstOffer);
        }
        return Optional.empty();
    }

    public void setOffer(UUID playerId, UUID runeId) {
        if (firstPlayer.equals(playerId)) {
            firstOffer = runeId;
            firstAccepted = false;
            secondAccepted = false;
        } else if (secondPlayer.equals(playerId)) {
            secondOffer = runeId;
            firstAccepted = false;
            secondAccepted = false;
        }
    }

    public void accept(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            firstAccepted = true;
        } else if (secondPlayer.equals(playerId)) {
            secondAccepted = true;
        }
    }

    public boolean isAccepted(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            return firstAccepted;
        }
        return secondPlayer.equals(playerId) && secondAccepted;
    }

    public boolean ready() {
        return firstOffer != null && secondOffer != null && firstAccepted && secondAccepted;
    }

    public UUID other(UUID playerId) {
        return firstPlayer.equals(playerId) ? secondPlayer : firstPlayer;
    }
}
