package com.jms.seafoodai.controller;

import com.jms.seafoodai.domain.User;
import com.jms.seafoodai.service.AuthService;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 이메일 중복 확인
     * GET /api/v1/auth/check-email?email=xxx
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "code", "SUCCESS",
            "data", Map.of("isAvailable", available)
        ));
    }

    /**
     * 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody User user) {
        authService.signup(user);
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "code", "SUCCESS",
            "message", "회원가입이 완료되었습니다."
        ));
    }
}
