package com.jms.seafoodai.service;

import com.jms.seafoodai.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 이메일 사용 가능 여부 반환 (true: 사용 가능, false: 중복) */
    public boolean isEmailAvailable(String email) {
        return userMapper.countByEmail(email) == 0;
    }

    /** 회원가입 처리: 중복 체크 → BCrypt 암호화 → DB INSERT */
    @Transactional
    public void signup(java.util.Map<String, Object> user) {
        String email = (String) user.get("email");
        if (!isEmailAvailable(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        // 평문 비밀번호를 BCrypt 해시로 교체 후 저장
        String rawPassword = (String) user.get("password");
        user.put("password", passwordEncoder.encode(rawPassword));
        userMapper.insertUser(user);
    }

    /** 로그인 인증 처리: 이메일 검증 → BCrypt 비밀번호 매칭 검사 → Map 객체 반환 */
    public java.util.Map<String, Object> login(String email, String password) {
        java.util.Map<String, Object> user = userMapper.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("존재하지 않는 이메일입니다.");
        }
        String dbPassword = (String) user.get("password");
        if (!passwordEncoder.matches(password, dbPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        // 보안을 위해 비밀번호 필드를 지운 뒤 반환
        user.put("password", "");
        return user;
    }

    /** 
     * 도/소매업자 거래 승인 및 다음 유통 체인 생성 (트랜잭션 처리)
     */
    @Transactional
    public void approveTrade(String traceId, String userId, Integer sellPrice, Integer adjustedQty) {
        // 1. 원본 거래 상세 정보 조회
        java.util.Map<String, Object> parentTrade = userMapper.findTradeById(traceId);
        if (parentTrade == null) {
            throw new IllegalArgumentException("원본 거래의 상세 정보를 조회할 수 없습니다.");
        }
        
        // 원본 거래 상태 검증 (이미 승인/거절 처리된 중복 방지)
        String currentStatus = (String) parentTrade.get("tradeStatus");
        if (!"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("대기 상태(PENDING)의 거래만 승인할 수 있습니다.");
        }
        
        // [Case A / Case B] 수취 파트너 매칭 검증
        Object parentReceiver = parentTrade.get("receiverId");
        if (parentReceiver != null && !parentReceiver.toString().trim().isEmpty()) {
            // [Case A] 지정 파트너 거래: 로그인 유저 ID와 일치해야 함
            if (!parentReceiver.toString().equals(userId)) {
                throw new IllegalArgumentException("지정된 수취 파트너가 아닙니다. 가로채기가 차단되었습니다.");
            }
        }
        // [Case B] 비지정 공개 매물: 누구나 선착순 승인 가능

        // 원본 수량 확인
        Object qtyObj = parentTrade.get("quantity");
        if (qtyObj == null) {
            throw new IllegalArgumentException("원본 거래의 수량 정보를 확인할 수 없습니다.");
        }
        int parentQty = Integer.parseInt(qtyObj.toString());
        
        int approvedQty = parentQty; // 실제 승인/입고될 수량
        
        // 2. 부분 승인 (adjustedQty) 여부 분기 처리
        if (adjustedQty != null && adjustedQty > 0) {
            if (adjustedQty > parentQty) {
                throw new IllegalArgumentException("원본 수량보다 더 많은 양을 인수할 수 없습니다.");
            }
            
            approvedQty = adjustedQty;
            
            if (adjustedQty < parentQty) {
                // [부분 인수]: 원본 거래의 수량만 차감하고 상태는 PENDING 유지!
                int rows = userMapper.deductTradeQuantity(traceId, adjustedQty);
                if (rows == 0) {
                    throw new IllegalArgumentException("원본 거래의 수량 차감 업데이트에 실패했습니다.");
                }
            } else {
                // [전량 인수]: 원본 거래 상태를 APPROVED로 변경
                int rows = userMapper.updateTradeStatus(traceId, "APPROVED");
                if (rows == 0) {
                    throw new IllegalArgumentException("거래 승인 업데이트에 실패했습니다.");
                }
            }
        } else {
            // [전량 인수]: 원본 거래 상태를 APPROVED로 변경
            int rows = userMapper.updateTradeStatus(traceId, "APPROVED");
            if (rows == 0) {
                throw new IllegalArgumentException("거래 승인 업데이트에 실패했습니다.");
            }
        }
        
        // 3. 다음 체인을 위한 자식 거래 정보 구성 및 인서트 (기본 입고 STOCKED 상태 부여)
        java.util.Map<String, Object> childTrade = new java.util.HashMap<>();
        childTrade.put("parentTraceId", traceId);
        childTrade.put("userId", userId);
        childTrade.put("fishType", parentTrade.get("fishType"));
        childTrade.put("quantity", approvedQty); // 정정된 수량 적용!
        childTrade.put("buyPrice", parentTrade.get("sellPrice")); // 부모의 판매가격이 자식의 매입가격으로 대입!
        childTrade.put("sellPrice", sellPrice);
        childTrade.put("tradeStatus", "STOCKED");
        
        userMapper.insertChildTrade(childTrade);
    }

    /**
     * 지정 거래 거절 처리 (상태를 REJECTED로 업데이트)
     */
    @Transactional
    public void rejectTrade(String traceId, String userId) {
        // 1. 거래 상세 정보 조회
        java.util.Map<String, Object> trade = userMapper.findTradeById(traceId);
        if (trade == null) {
            throw new IllegalArgumentException("거래 내역을 찾을 수 없습니다.");
        }

        // 2. 전체 공개 매물 검증 (receiverId가 없으면 거절 불가)
        Object receiverObj = trade.get("receiverId");
        if (receiverObj == null || receiverObj.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("전체 공개 매물은 거절할 수 없습니다. 인수를 원치 않으시면 무시해 주세요.");
        }

        // 3. 지정 수취 파트너 본인 여부 검증
        if (!receiverObj.toString().equals(userId)) {
            throw new IllegalArgumentException("본인에게 지정된 거래만 거절할 수 있습니다.");
        }

        // 4. 상태가 PENDING인 거래만 거절 가능
        String currentStatus = (String) trade.get("tradeStatus");
        if (!"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("대기 상태(PENDING)의 거래만 거절할 수 있습니다.");
        }

        // 5. 상태를 REJECTED로 업데이트
        int rows = userMapper.updateTradeStatus(traceId, "REJECTED");
        if (rows == 0) {
            throw new IllegalArgumentException("거래 거절 업데이트에 실패했습니다.");
        }
    }
}
