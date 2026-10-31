package org.sps.backend.video;

/** Les compteurs à jour après un like / dislike, pour rafraîchir les boutons du lecteur. */
public record ReactionResponse(long likes, long dislikes, ReactionType myReaction) { }
