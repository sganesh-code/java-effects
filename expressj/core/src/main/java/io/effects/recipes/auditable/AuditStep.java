package io.effects.recipes.auditable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable audit step register in a cryptographically secured chain.
 */
public record AuditStep<E>(
    String stepId,
    String actorId,
    E detail,
    String hash,
    Instant timestamp
) {
    public AuditStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(hash);
        Objects.requireNonNull(timestamp);
    }

    /**
     * Cryptographically links this step to a previous hash, securing the integrity of the audit chain.
     */
    public static <E> String computeHash(
        String stepId, 
        String actorId, 
        E detail, 
        String previousHash, 
        Instant timestamp
    ) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String detailStr = Objects.toString(detail);
            String input = stepId + actorId + detailStr + (previousHash != null ? previousHash : "") + timestamp.toString();
            byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}