package com.trello.service;

import com.trello.config.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    private static final String ACTIVE_PREFIX       = "auth:active:";
    private static final String BLACKLIST_PREFIX    = "auth:blacklist:";
    private static final String REFRESH_PREFIX      = "auth:refresh:";
    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh:blacklist:";

    /** Lưu token vào Redis sau khi login thành công.
     *  Key: auth:active:{userId}  →  Value: token */
    public void saveActiveToken(String token, String userId, long ttlSeconds) {
        if (token == null || userId == null) {
            log.error("saveActiveToken: token or userId is null - token={}, userId={}", token, userId);
            return;
        }
        
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            if (ttlSeconds <= 0) {
                ttlSeconds = 86400; // Default 24 hours
                log.warn("TTL invalid, using default 86400s");
            }

            String key = "auth:active:" + userId;
            
            // Ensure Redis template is available
            if (stringRedisTemplate == null) {
                log.error("StringRedisTemplate is NULL - cannot save token to Redis");
                return;
            }
            
            // Save to Redis synchronously
            stringRedisTemplate.opsForValue().set(key, token, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            
            // Verify immediately
            String saved = stringRedisTemplate.opsForValue().get(key);
            if (saved != null && saved.length() > 0) {
                log.info("✓ Token saved successfully to Redis - key: {}, ttl: {}s", key, ttlSeconds);
            } else {
                log.error("✗ Token NOT found in Redis after save - key: {}", key);
            }
        } catch (Exception e) {
            log.error("CRITICAL ERROR in saveActiveToken: {}", e.getMessage(), e);
        }
    }

    /** Đưa token vào blacklist khi logout, xoá active key theo userId. */
    public void blacklistToken(String token, long ttlSeconds) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            log.info("blacklistToken called - ttlSeconds: {}", ttlSeconds);

            if (ttlSeconds <= 0) {
                ttlSeconds = 86400;
                log.warn("TTL is invalid (<=0), set default to 86400s");
            }

            // Lưu vào blacklist theo token
            String blacklistKey = BLACKLIST_PREFIX + token;
            stringRedisTemplate.opsForValue()
                    .set(blacklistKey, "1", ttlSeconds, TimeUnit.SECONDS);
            log.info("Token blacklisted successfully - key: {}, ttl: {}s", blacklistKey, ttlSeconds);

            // Xoá active key theo userId (lấy userId từ JWT)
            try {
                String userId = jwtTokenProvider.getUsernameFromJWT(token);
                String activeKey = ACTIVE_PREFIX + userId;
                Boolean deleted = stringRedisTemplate.delete(activeKey);
                log.info("Deleted active key: {}, existed: {}", activeKey, deleted);
            } catch (Exception ex) {
                log.warn("Could not extract userId from token to delete active key: {}", ex.getMessage());
            }

            Boolean exists = stringRedisTemplate.hasKey(blacklistKey);
            log.info("Blacklist key exists after save: {}", exists);

        } catch (Exception e) {
            log.error("Error blacklisting token: {}", e.getMessage(), e);
        }
    }

    /** Kiểm tra token có trong blacklist không. */
    public boolean isBlacklisted(String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            String blacklistKey = BLACKLIST_PREFIX + token;
            boolean result = Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey));
            log.debug("isBlacklisted check - result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error checking blacklist: {}", e.getMessage(), e);
            return false;
        }
    }

    /** Lấy token active của một user (nếu có). */
    public String getActiveToken(String userId) {
        try {
            return stringRedisTemplate.opsForValue().get(ACTIVE_PREFIX + userId);
        } catch (Exception e) {
            log.error("Error getting active token for userId {}: {}", userId, e.getMessage());
            return null;
        }
    }

    // ========== REFRESH TOKEN MANAGEMENT ==========

    /**
     * Lưu refresh token vào Redis
     * Key: auth:refresh:{userId} → Value: {refresh_token}
     * @param refreshToken JWT refresh token
     * @param userId User ID
     * @param ttlSeconds TTL in seconds (30 days)
     */
    public void saveRefreshToken(String refreshToken, String userId, long ttlSeconds) {
        try {
            if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
                refreshToken = refreshToken.substring(7);
            }
            log.info("saveRefreshToken called - userId: {}, ttlSeconds: {}", userId, ttlSeconds);

            if (ttlSeconds <= 0) {
                ttlSeconds = 30 * 24 * 60 * 60; // 30 days default
                log.warn("TTL invalid, using default 30 days");
            }

            String key = REFRESH_PREFIX + userId;
            log.info("Attempting to save refresh token to Redis with key: {}", key);
            
            if (stringRedisTemplate == null) {
                log.error("StringRedisTemplate is NULL - Redis not properly configured!");
                return;
            }
            
            stringRedisTemplate.opsForValue()
                    .set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);

            log.info("Refresh token saved successfully - key: {}, ttl: {}s", key, ttlSeconds);
            
            // Verify token was saved
            String savedToken = stringRedisTemplate.opsForValue().get(key);
            if (savedToken != null) {
                log.info("✓ Refresh token verified in Redis - key: {}", key);
            } else {
                log.error("✗ Refresh token NOT found in Redis after save - key: {}", key);
            }
        } catch (Exception e) {
            log.error("Error saving refresh token: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * Lấy refresh token của user
     * @param userId User ID
     * @return Refresh token or null
     */
    public String getRefreshToken(String userId) {
        try {
            return stringRedisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        } catch (Exception e) {
            log.error("Error getting refresh token for userId {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Kiểm tra refresh token trong blacklist
     * @param refreshToken JWT refresh token
     * @return true if blacklisted, false otherwise
     */
    public boolean isRefreshTokenBlacklisted(String refreshToken) {
        try {
            if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
                refreshToken = refreshToken.substring(7);
            }
            String blacklistKey = REFRESH_BLACKLIST_PREFIX + refreshToken;
            boolean result = Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey));
            log.debug("isRefreshTokenBlacklisted check - result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error checking refresh token blacklist: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Đưa refresh token vào blacklist (logout)
     * @param refreshToken JWT refresh token
     * @param ttlSeconds TTL in seconds
     */
    public void blacklistRefreshToken(String refreshToken, long ttlSeconds) {
        try {
            if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
                refreshToken = refreshToken.substring(7);
            }
            log.info("blacklistRefreshToken called - ttlSeconds: {}", ttlSeconds);

            if (ttlSeconds <= 0) {
                ttlSeconds = 30 * 24 * 60 * 60; // 30 days default
                log.warn("TTL is invalid, set default to 30 days");
            }

            String blacklistKey = REFRESH_BLACKLIST_PREFIX + refreshToken;
            stringRedisTemplate.opsForValue()
                    .set(blacklistKey, "1", ttlSeconds, TimeUnit.SECONDS);
            log.info("Refresh token blacklisted successfully - key: {}, ttl: {}s", blacklistKey, ttlSeconds);

            // Delete refresh key từ user profile
            try {
                String userId = jwtTokenProvider.getUsernameFromJWT(refreshToken);
                String refreshKey = REFRESH_PREFIX + userId;
                Boolean deleted = stringRedisTemplate.delete(refreshKey);
                log.info("Deleted refresh key: {}, existed: {}", refreshKey, deleted);
            } catch (Exception ex) {
                log.warn("Could not extract userId from refresh token to delete refresh key: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.error("Error blacklisting refresh token: {}", e.getMessage(), e);
        }
    }
}
