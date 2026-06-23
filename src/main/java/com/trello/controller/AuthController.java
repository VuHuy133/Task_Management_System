package com.trello.controller;

import com.trello.dto.ApiResponse;
import com.trello.dto.LoginRequest;
import com.trello.dto.UserResponse;
import com.trello.entity.User;
import com.trello.exception.UserAlreadyExistsException;
import com.trello.config.security.JwtTokenProvider;
import com.trello.service.TokenBlacklistService;
import com.trello.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * User registration endpoint
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody User newUser) {
        try {
            // Check if email already exists
            if (userService.isEmailExist(newUser.getEmail())) {
                ApiResponse<?> response = ApiResponse.builder()
                        .success(false)
                        .message("Email đã tồn tại, vui lòng sử dụng email khác")
                        .statusCode(HttpStatus.BAD_REQUEST.value())
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Register user with USER role
            User registeredUser = userService.handleRegister(newUser);

            UserResponse userResponse = UserResponse.builder()
                    .id(registeredUser.getId())
                    .username(registeredUser.getUsername())
                    .email(registeredUser.getEmail())
                    .role(registeredUser.getRole())
                    .createdAt(registeredUser.getCreatedAt())
                    .build();

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Đăng ký thành công")
                    .statusCode(HttpStatus.CREATED.value())
                    .data(userResponse)
                    .build();

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (UserAlreadyExistsException e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * User login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletResponse response) {
        try {
            // Find user by email
            Optional<User> userOpt = userService.getUserByEmail(request.getEmail());

            if (!userOpt.isPresent()) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Email hoặc mật khẩu không chính xác")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }

            User user = userOpt.get();

            // Validate password
            if (!userService.validatePassword(request.getPassword(), user.getPassword())) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Email hoặc mật khẩu không chính xác")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }


            // Generate Access Token 
            String accessToken = jwtTokenProvider.generateAccessToken(user.getId().toString(), user.getRole());
            long accessTokenTtl = jwtTokenProvider.getTokenExpirationInSeconds(accessToken);

            // Save access token vào Redis IMMEDIATELY without delay
            tokenBlacklistService.saveActiveToken(accessToken, user.getId().toString(), accessTokenTtl);

            // Generate Refresh Token (30 days)
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());
            long refreshTokenTtl = jwtTokenProvider.getTokenExpirationInSeconds(refreshToken);

            // Save refresh token vào Redis IMMEDIATELY without delay
            tokenBlacklistService.saveRefreshToken(refreshToken, user.getId().toString(), refreshTokenTtl);

            // Gửi refresh token qua httpOnly Cookie (Session - không thể bị XSS đọc)
            Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(false); // Đổi thành true khi deploy HTTPS
            refreshCookie.setPath("/api/auth");
            refreshCookie.setMaxAge((int) refreshTokenTtl);
            response.addCookie(refreshCookie);

            UserResponse userResponse = UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .createdAt(user.getCreatedAt())
                    .build();

            // Response: access token trong body, refresh token đã được set vào httpOnly Cookie
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(true)
                    .message("Đăng nhập thành công")
                    .statusCode(HttpStatus.OK.value())
                    .data(java.util.Map.of(
                        "user", userResponse,
                        "accessToken", accessToken,
                        "expiresIn", accessTokenTtl
                    ))
                    .build();

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + accessToken)
                    .body(apiResponse);

        } catch (Exception e) {
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi đăng nhập: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Logout endpoint (REST API)
     * Blacklist cả access token và refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Đọc refresh token từ httpOnly Cookie (Session)
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        // Validate access token
        if (token == null || !token.startsWith("Bearer ")) {
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(false)
                    .message("Access token không hợp lệ hoặc không được cung cấp")
                    .statusCode(HttpStatus.UNAUTHORIZED.value())
                    .build();
            return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
        }

        try {
            String accessToken = token.substring(7);
            
            // Validate access token
            if (!jwtTokenProvider.validateToken(accessToken)) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Access token hết hạn hoặc không hợp lệ")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }

            // Đưa access token vào blacklist Redis
            tokenBlacklistService.blacklistToken(
                    accessToken,
                    jwtTokenProvider.getTokenExpirationInSeconds(accessToken));

            // Đưa refresh token vào blacklist (nếu được cung cấp)
            if (refreshToken != null && !refreshToken.isEmpty()) {
                if (jwtTokenProvider.validateToken(refreshToken)) {
                    tokenBlacklistService.blacklistRefreshToken(
                            refreshToken,
                            jwtTokenProvider.getTokenExpirationInSeconds(refreshToken));
                }
            }

            // Xóa httpOnly Cookie (Session) trên client
            Cookie expiredCookie = new Cookie("refreshToken", "");
            expiredCookie.setHttpOnly(true);
            expiredCookie.setSecure(false);
            expiredCookie.setPath("/api/auth");
            expiredCookie.setMaxAge(0);
            response.addCookie(expiredCookie);

            // Logout successful
            ApiResponse<?> logoutResponse = ApiResponse.builder()
                    .success(true)
                    .message("Đăng xuất thành công")
                    .statusCode(HttpStatus.OK.value())
                    .build();
            
            return new ResponseEntity<>(logoutResponse, HttpStatus.OK);

        } catch (Exception e) {
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi đăng xuất: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Refresh access token endpoint
     * Client gửi refresh token để lấy access token mới
     * Refresh token được giữ lại (reuse pattern)
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refreshAccessToken(
            HttpServletRequest request) {

        // Đọc refresh token từ httpOnly Cookie (Session)
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // Validate refresh token
        if (refreshToken == null || refreshToken.isEmpty()) {
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(false)
                    .message("Refresh token không tồn tại trong session, vui lòng đăng nhập lại")
                    .statusCode(HttpStatus.UNAUTHORIZED.value())
                    .build();
            return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
        }

        try {

            // Check in blacklist
            if (tokenBlacklistService.isRefreshTokenBlacklisted(refreshToken)) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Refresh token đã bị logout, vui lòng login lại")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }

            // Validate refresh token
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Refresh token hết hạn hoặc không hợp lệ")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }

            // Extract user ID from refresh token
            String userId = jwtTokenProvider.getUsernameFromJWT(refreshToken);
            
            // Verify refresh token in Redis
            String storedRefreshToken = tokenBlacklistService.getRefreshToken(userId);
            if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
                ApiResponse<?> apiResponse = ApiResponse.builder()
                        .success(false)
                        .message("Refresh token không khớp với token lưu trong Redis")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build();
                return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
            }


            // Lấy user từ DB để lấy role
            User user = userService.getUserById(Long.valueOf(userId));
            // Generate new access token (keep refresh token) with role
            String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getRole());
            long newAccessTokenTtl = jwtTokenProvider.getTokenExpirationInSeconds(newAccessToken);

            // Save new access token to Redis active
            tokenBlacklistService.saveActiveToken(newAccessToken, userId, newAccessTokenTtl);
            
            // Do NOT generate new refresh token (reuse pattern)
            // Refresh token is in httpOnly cookie, don't return in body

            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(true)
                    .message("Refresh token thành công, access token mới được cấp")
                    .statusCode(HttpStatus.OK.value())
                    .data(java.util.Map.of(
                        "accessToken", newAccessToken,
                        "expiresIn", newAccessTokenTtl
                    ))
                    .build();

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + newAccessToken)
                    .body(apiResponse);

        } catch (Exception e) {
            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi refresh token: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
