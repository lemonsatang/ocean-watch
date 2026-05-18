package com.jms.seafoodai.controller;

import com.jms.seafoodai.domain.User;
import com.jms.seafoodai.service.AuthService;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final com.jms.seafoodai.mapper.UserMapper userMapper;

    public AuthController(AuthService authService, com.jms.seafoodai.mapper.UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    /**
     * 이메일 중복 확인
     * GET /api/v1/auth/check-email?email=xxx
     */
    @GetMapping("/auth/check-email")
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
    @PostMapping("/auth/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody User user) {
        authService.signup(user);
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "code", "SUCCESS",
            "message", "회원가입이 완료되었습니다."
        ));
    }

    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest, javax.servlet.http.HttpSession session) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        try {
            java.util.Map<String, Object> user = authService.login(email, password);
            
            // 로그인 데이터 무결성 검증 (userId 및 role 누락 방어)
            if (user == null || user.get("userId") == null || user.get("userId").toString().trim().isEmpty() || user.get("role") == null) {
                throw new IllegalArgumentException("로그인 사용자 정보의 데이터 무결성이 올바르지 않습니다.");
            }
            
            // 세션에 로그인 사용자 정보 바인딩 및 만료시간 설정 (30분)
            session.setAttribute("loginUser", user);
            session.setMaxInactiveInterval(1800);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("message", "로그인이 완료되었습니다.");

            Map<String, Object> userData = new java.util.HashMap<>();
            userData.put("userId", user.get("userId"));
            userData.put("email", user.get("email"));
            userData.put("userName", user.get("userName"));
            userData.put("role", user.get("role"));
            
            response.put("data", userData);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("status", 400);
            errorResponse.put("code", "FAIL");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 현재 로그인된 사용자 상태 조회
     * GET /api/v1/auth/me
     */
    @GetMapping("/auth/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(javax.servlet.http.HttpSession session) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        
        if (loginUser == null || loginUser.get("userId") == null || loginUser.get("userId").toString().trim().isEmpty()) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", 200);
        response.put("code", "SUCCESS");

        Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("userId", loginUser.get("userId"));
        userData.put("userName", loginUser.get("userName"));
        userData.put("email", loginUser.get("email"));
        userData.put("role", loginUser.get("role"));
        
        response.put("data", userData);
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃
     * GET /api/v1/auth/logout
     */
    @GetMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(javax.servlet.http.HttpSession session) {
        session.removeAttribute("loginUser");
        session.invalidate();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", 200);
        response.put("code", "SUCCESS");
        response.put("message", "로그아웃이 완료되었습니다.");
        return ResponseEntity.ok(response);
    }

    /**
     * 도매업자 목록 조회
     * GET /api/v1/trade/wholesalers
     */
    @GetMapping("/trade/wholesalers")
    public ResponseEntity<Map<String, Object>> getWholesalers() {
        java.util.List<Map<String, Object>> list = userMapper.findUsersByRole("WHOLESALER");
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", 200);
        response.put("code", "SUCCESS");
        
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();
        for (Map<String, Object> u : list) {
            Map<String, Object> uMap = new java.util.HashMap<>();
            uMap.put("userId", u.get("userId"));
            uMap.put("userName", u.get("userName"));
            uMap.put("email", u.get("email"));
            dataList.add(uMap);
        }
        
        response.put("data", dataList);
        return ResponseEntity.ok(response);
    }

    /**
     * 유통 파트너 목록 조회 (도매/소매업자)
     * GET /api/v1/user/partners
     */
    @GetMapping("/user/partners")
    public ResponseEntity<Map<String, Object>> getPartners() {
        java.util.List<Map<String, Object>> list = userMapper.findPartners();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", 200);
        response.put("code", "SUCCESS");
        response.put("data", list);
        return ResponseEntity.ok(response);
    }

    /**
     * 생산자 최초 거래 등록
     * POST /api/v1/trade/register
     */
    @PostMapping("/trade/register")
    public ResponseEntity<Map<String, Object>> registerInitialTrade(
            @RequestBody Map<String, Object> body, 
            javax.servlet.http.HttpSession session) {
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String userId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";

            Map<String, Object> paramMap = new java.util.HashMap<>();
            paramMap.put("userId", userId);
            paramMap.put("receiverId", body.get("receiverId") != null && !body.get("receiverId").toString().trim().isEmpty() ? body.get("receiverId").toString() : null);
            paramMap.put("fishType", body.get("fishCode")); // HTML form still sends fishCode, maps to fishType
            paramMap.put("quantity", body.get("qty"));       // HTML form still sends qty, maps to quantity
            paramMap.put("sellPrice", body.get("sellPrice"));

            int rows = userMapper.insertInitialTrade(paramMap);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("message", "최초 거래가 정상적으로 등록되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "거래 등록 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 도/소매업자 거래 승인 및 다음 체인 위임
     * POST /api/v1/trade/approve
     */
    @PostMapping("/trade/approve")
    public ResponseEntity<Map<String, Object>> approveTrade(
            @RequestBody Map<String, Object> requestMap, 
            javax.servlet.http.HttpSession session) {
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String traceId = (String) requestMap.get("traceId");
            if (traceId == null) {
                traceId = (String) requestMap.get("trace_id");
            }
            
            String userId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";
            
            Object sellPriceObj = requestMap.get("sellPrice");
            if (sellPriceObj == null) {
                sellPriceObj = requestMap.get("sell_price");
            }
            
            Integer sellPrice = null;
            if (sellPriceObj != null && !sellPriceObj.toString().trim().isEmpty()) {
                sellPrice = Integer.parseInt(sellPriceObj.toString());
            }

            Object adjustedQtyObj = requestMap.get("adjustedQty");
            if (adjustedQtyObj == null) {
                adjustedQtyObj = requestMap.get("adjusted_qty");
            }
            
            Integer adjustedQty = null;
            if (adjustedQtyObj != null && !adjustedQtyObj.toString().trim().isEmpty()) {
                adjustedQty = Integer.parseInt(adjustedQtyObj.toString());
            }

            authService.approveTrade(traceId, userId, sellPrice, adjustedQty);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("message", "거래가 성공적으로 승인 및 인수 완료되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "거래 승인 처리 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 지정 거래 거절 처리
     * POST /api/v1/trade/reject
     */
    @PostMapping("/trade/reject")
    public ResponseEntity<Map<String, Object>> rejectTrade(
            @RequestBody Map<String, Object> requestMap, 
            javax.servlet.http.HttpSession session) {
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String traceId = (String) requestMap.get("traceId");
            if (traceId == null) {
                traceId = (String) requestMap.get("trace_id");
            }
            
            String userId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";

            authService.rejectTrade(traceId, userId);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("message", "거래가 성공적으로 거절 처리되었습니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 400);
            errResponse.put("code", "BAD_REQUEST");
            errResponse.put("message", e.getMessage());
            return ResponseEntity.status(400).body(errResponse);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "거래 거절 처리 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 대기 중인 거래 목록 조회 (보안 필터링 적용)
     * GET /api/v1/trade/pending
     */
    @GetMapping("/trade/pending")
    public ResponseEntity<Map<String, Object>> getPendingTrades(javax.servlet.http.HttpSession session) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String userId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";
            java.util.List<Map<String, Object>> list = userMapper.findPendingTrades(userId);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "목록 조회 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 내 창고 재고 목록 조회 및 AI 추천 방출가 산출
     * GET /api/v1/inventory/list
     */
    @GetMapping("/inventory/list")
    public ResponseEntity<Map<String, Object>> getMyInventory(javax.servlet.http.HttpSession session) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String userId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";
            String userRole = loginUser.get("role") != null ? loginUser.get("role").toString() : "";

            java.util.List<Map<String, Object>> list = userMapper.findMyInventory(userId);
            
            // 각 재고 아이템별로 AI 추천가 실시간 연산
            for (Map<String, Object> item : list) {
                String fishType = (String) item.get("fishType");
                java.sql.Timestamp createdAt = (java.sql.Timestamp) item.get("createdAt");
                
                Map<String, Object> fairPriceMap = userMapper.findLatestFairPrice(fishType);
                int basePrice = 0;
                
                if (fairPriceMap != null) {
                    if ("WHOLESALER".equalsIgnoreCase(userRole)) {
                        Object wholesaleObj = fairPriceMap.get("avgWholesalePrice");
                        if (wholesaleObj != null) basePrice = Integer.parseInt(wholesaleObj.toString());
                    } else {
                        Object retailObj = fairPriceMap.get("avgRetailPrice");
                        if (retailObj != null) basePrice = Integer.parseInt(retailObj.toString());
                    }
                }
                
                int recommendedPrice = calculateAiRecommendedPrice(basePrice, createdAt);
                item.put("aiRecommendedPrice", recommendedPrice);
            }
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "창고 목록 조회 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 창고 재고 방출 및 AI 공정가 신호등 판정
     * POST /api/v1/inventory/release
     */
    @PostMapping("/inventory/release")
    public ResponseEntity<Map<String, Object>> releaseInventory(
            @RequestBody Map<String, Object> requestMap,
            javax.servlet.http.HttpSession session) {
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginUser = (java.util.Map<String, Object>) session.getAttribute("loginUser");
        if (loginUser == null) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            errResponse.put("code", "UNAUTHORIZED");
            errResponse.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(errResponse);
        }

        try {
            String traceId = (String) requestMap.get("traceId");
            if (traceId == null) {
                traceId = (String) requestMap.get("trace_id");
            }
            if (traceId == null || traceId.trim().isEmpty()) {
                throw new IllegalArgumentException("방출할 대상 거래 ID(traceId)는 필수입니다.");
            }

            Object sellPriceObj = requestMap.get("sellPrice");
            if (sellPriceObj == null) {
                sellPriceObj = requestMap.get("sell_price");
            }
            if (sellPriceObj == null || sellPriceObj.toString().trim().isEmpty()) {
                throw new IllegalArgumentException("방출 시 판매 가격(sellPrice)은 필수입니다.");
            }
            int sellPrice = Integer.parseInt(sellPriceObj.toString().trim());

            String receiverId = (String) requestMap.get("receiverId");
            if (receiverId == null) {
                receiverId = (String) requestMap.get("receiver_id");
            }

            // 1. 거래 단건 조회
            Map<String, Object> trade = userMapper.findTradeById(traceId);
            if (trade == null) {
                throw new IllegalArgumentException("방출할 거래 내역을 찾을 수 없습니다.");
            }

            // 2. 창고(STOCKED) 상태 및 소유주 검증
            String currentStatus = (String) trade.get("tradeStatus");
            if (!"STOCKED".equals(currentStatus)) {
                throw new IllegalArgumentException("창고 입고(STOCKED) 상태의 재고만 시장에 방출할 수 있습니다.");
            }

            String sessionUserId = loginUser.get("userId") != null ? loginUser.get("userId").toString() : "";
            String userRole = loginUser.get("role") != null ? loginUser.get("role").toString() : "";

            String ownerId = trade.get("userId") != null ? trade.get("userId").toString() : "";
            if (!ownerId.equals(sessionUserId)) {
                throw new IllegalArgumentException("본인 소유의 창고 재고만 시장에 방출할 수 있습니다.");
            }

            String fishType = (String) trade.get("fishType");
            java.sql.Timestamp createdAt = (java.sql.Timestamp) trade.get("createdAt");

            // 3. 오늘 또는 가장 최근의 AI 공정 기준가 조회
            Map<String, Object> fairPriceMap = userMapper.findLatestFairPrice(fishType);
            int basePrice = 0;
            
            if (fairPriceMap != null) {
                if ("WHOLESALER".equalsIgnoreCase(userRole)) {
                    Object wholesaleObj = fairPriceMap.get("avgWholesalePrice");
                    if (wholesaleObj != null) {
                        basePrice = Integer.parseInt(wholesaleObj.toString());
                    }
                } else {
                    Object retailObj = fairPriceMap.get("avgRetailPrice");
                    if (retailObj != null) {
                        basePrice = Integer.parseInt(retailObj.toString());
                    }
                }
            }

            // 4. AI 추천 방출가 산출 (신선도 감가 및 주말 할증 알고리즘 적용)
            int recommendedPrice = calculateAiRecommendedPrice(basePrice, createdAt);

            // 5. 스마트 AI 공정가 신호등 판정 엔진 (추천가 대비율)
            String fairPriceStatus = "Y"; // 기본값
            if (recommendedPrice > 0) {
                double ratio = (double) sellPrice / recommendedPrice;
                if (ratio <= 1.1) {
                    fairPriceStatus = "Y"; // Green (110% 이하)
                } else if (ratio > 1.1 && ratio <= 1.3) {
                    fairPriceStatus = "W"; // Yellow (130% 이하)
                } else {
                    fairPriceStatus = "N"; // Red (130% 초과)
                }
            }

            // 6. DB 업데이트 반영
            Map<String, Object> paramMap = new java.util.HashMap<>();
            paramMap.put("traceId", traceId);
            paramMap.put("userId", sessionUserId);
            paramMap.put("sellPrice", sellPrice);
            paramMap.put("fairPriceStatus", fairPriceStatus);
            paramMap.put("receiverId", receiverId);

            int rows = userMapper.releaseInventory(paramMap);
            if (rows == 0) {
                throw new IllegalArgumentException("재고 방출 처리에 실패했습니다. 재고 소유주 및 상태를 확인하세요.");
            }

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("message", "재고가 실시간 AI 공정가 검증을 통과하여 시장에 성공적으로 방출되었습니다.");
            response.put("fairPriceStatus", fairPriceStatus);
            response.put("recommendedPrice", recommendedPrice);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 400);
            errResponse.put("code", "BAD_REQUEST");
            errResponse.put("message", e.getMessage());
            return ResponseEntity.status(400).body(errResponse);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "재고 방출 처리 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(500).body(errResponse);
        }
    }
    /**
     * 소비자용 유통 이력 재귀 타임라인 조회 (비로그인 오픈 API)
     * GET /api/v1/trace/timeline/{traceId}
     */
    @GetMapping("/trace/timeline/{traceId}")
    public ResponseEntity<Map<String, Object>> getTraceTimeline(@org.springframework.web.bind.annotation.PathVariable("traceId") String traceId) {
        try {
            if (traceId == null || traceId.trim().isEmpty()) {
                throw new IllegalArgumentException("조회할 유통 이력 식별자(traceId)가 제공되지 않았습니다.");
            }

            java.util.List<Map<String, Object>> timeline = userMapper.findTraceTimeline(traceId);

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", 200);
            response.put("code", "SUCCESS");
            response.put("data", timeline);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 400);
            errResponse.put("code", "BAD_REQUEST");
            errResponse.put("message", e.getMessage());
            return ResponseEntity.status(400).body(errResponse);
        } catch (Exception e) {
            Map<String, Object> errResponse = new java.util.HashMap<>();
            errResponse.put("status", 500);
            errResponse.put("code", "FAIL");
            errResponse.put("message", "타임라인 조회 중 서버 오류가 발생했습니다.");
            return ResponseEntity.status(500).body(errResponse);
        }
    }

    /**
     * 내부 알고리즘 기반 AI 스마트 방출가 연산 엔진
     */
    private int calculateAiRecommendedPrice(int basePrice, java.sql.Timestamp createdAt) {
        if (basePrice <= 0 || createdAt == null) return 0;
        
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate stockedDate = createdAt.toLocalDateTime().toLocalDate();
        long agingDays = java.time.temporal.ChronoUnit.DAYS.between(stockedDate, today);
        if (agingDays < 0) agingDays = 0;
        
        double price = basePrice;
        
        // 1. 신선도 감가 (체류일수 2일 초과 시 1일당 5% 감가)
        if (agingDays > 2) {
            long penaltyDays = agingDays - 2;
            double penaltyRatio = penaltyDays * 0.05;
            if (penaltyRatio > 0.9) penaltyRatio = 0.9; // 최대 90% 방어선
            price = price * (1.0 - penaltyRatio);
        }
        
        // 2. 주말 할증 (금, 토, 일 10% 할증)
        java.time.DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.FRIDAY || 
            dayOfWeek == java.time.DayOfWeek.SATURDAY || 
            dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            price = price * 1.10;
        }
        
        return (int) Math.round(price);
    }
}
