package com.stash.platform.susu.service;

import com.stash.platform.susu.repository.SusuGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique 8-character alphanumeric join codes.
 *
 * <p>Characters: A-Z and 2-9 (omits 0, O, 1, I to avoid visual ambiguity).
 * 32^8 = ~1.1 trillion possible codes. Collision probability at 1 million
 * groups is approximately 0.045% — well within acceptable range.
 *
 * <p>On collision, regenerates up to {@link #MAX_ATTEMPTS} times before
 * throwing, which would indicate a serious entropy problem or a very large
 * number of existing groups.
 */
@Component
public class JoinCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(JoinCodeGenerator.class);

    // Excludes 0/O and 1/I to reduce transcription errors
    private static final String ALPHABET    = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int    CODE_LENGTH = 8;
    private static final int    MAX_ATTEMPTS = 10;

    private final SecureRandom        random;
    private final SusuGroupRepository groupRepo;

    public JoinCodeGenerator(SusuGroupRepository groupRepo) {
        this.random    = new SecureRandom();
        this.groupRepo = groupRepo;
    }

    /**
     * Generates a join code that is unique in the database at the time of generation.
     *
     * @return an 8-character uppercase alphanumeric code
     * @throws IllegalStateException if a unique code cannot be found within MAX_ATTEMPTS
     */
    public String generate() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = generateCandidate();
            if (groupRepo.findByJoinCode(candidate).isEmpty()) {
                if (attempt > 1) {
                    log.warn("JoinCodeGenerator: {} collision(s) before finding unique code",
                            attempt - 1);
                }
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique join code after " + MAX_ATTEMPTS +
                " attempts. This should never happen in normal operation.");
    }

    private String generateCandidate() {
        char[] buf = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            buf[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(buf);
    }
}
