package com.trello.config;

import com.trello.service.DataSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CommandLineRunner để tự động chạy logic khi application startup
 * Hỗ trợ reset password từ environment variables
 * 
 * Enable bằng cách set: ENABLE_STARTUP_RUNNER=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.startup.enabled",
    havingValue = "true",
    matchIfMissing = false  // Mặc định KHÔNG chạy (disabled)
)
public class ApplicationStartupRunner implements CommandLineRunner {

    private final DataSeederService dataSeederService;
    private final Environment environment;

    @Override
    public void run(String... args) throws Exception {
        // Kiểm tra environment variable RESET_PASSWORD
        String resetPasswordEnv = environment.getProperty("RESET_PASSWORD");
        if ("true".equalsIgnoreCase(resetPasswordEnv)) {
            String newPassword = environment.getProperty("RESET_PASSWORD_VALUE", "123456");
            
            log.info("⚙️  RESET_PASSWORD is enabled. Resetting all user passwords to: {}", 
                    "*".repeat(newPassword.length()));
            
            Map<String, Object> result = dataSeederService.resetAllUserPasswords(newPassword);
            
            if ("SUCCESS".equals(result.get("status"))) {
                log.info("✅ Password reset successful: {}", result.get("message"));
            } else {
                log.error("❌ Password reset failed: {}", result.get("message"));
            }
        }
    }
}
