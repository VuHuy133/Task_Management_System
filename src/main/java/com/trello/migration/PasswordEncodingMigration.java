package com.trello.migration;

import com.trello.entity.User;
import com.trello.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordEncodingMigration implements CommandLineRunner, Ordered {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // BCrypt pattern: $2a$|$2b$|$2y$ followed by cost and hash
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Override
    public void run(String... args) throws Exception {
        log.info("====== PASSWORD ENCODING MIGRATION STARTED ======");
        
        try {
            List<User> allUsers = userRepository.findAll();
            log.info("Total users in database: {}", allUsers.size());
            
            if (allUsers.isEmpty()) {
                log.info("No users found in database. Skipping migration.");
                return;
            }

            int encodedCount = 0;
            int skippedCount = 0;

            for (User user : allUsers) {
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    log.warn("User {} has null/empty password, skipping", user.getUsername());
                    skippedCount++;
                    continue;
                }

                // Check if password is already BCrypt encoded
                if (isBCryptEncoded(user.getPassword())) {
                    log.debug("User {} password already encoded, skipping", user.getUsername());
                    skippedCount++;
                    continue;
                }

                // Encode plain text password
                try {
                    String plainPassword = user.getPassword();
                    String encodedPassword = passwordEncoder.encode(plainPassword);
                    user.setPassword(encodedPassword);
                    userRepository.save(user);
                    log.info("✓ Encoded password for user: {} (from: {} to: {}...)", 
                        user.getUsername(), 
                        plainPassword.substring(0, Math.min(3, plainPassword.length())) + "***",
                        encodedPassword.substring(0, 20) + "...");
                    encodedCount++;
                } catch (Exception e) {
                    log.error("✗ Failed to encode password for user: {}", user.getUsername(), e);
                }
            }

            log.info("====== PASSWORD ENCODING MIGRATION COMPLETE ======");
            log.info("Total users: {}", allUsers.size());
            log.info("Encoded: {}", encodedCount);
            log.info("Skipped (already encoded or null): {}", skippedCount);
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR during password migration!", e);
        }
    }

    /**
     * Returns execution order. Lower values run first.
     * Set to Ordered.HIGHEST_PRECEDENCE to ensure this runs before other CommandLineRunner implementations.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Check if password is already BCrypt encoded
     * @param password Password to check
     * @return true if password is BCrypt hash, false if plain text
     */
    private boolean isBCryptEncoded(String password) {
        if (password == null) {
            return false;
        }
        return BCRYPT_PATTERN.matcher(password).find();
    }
}
