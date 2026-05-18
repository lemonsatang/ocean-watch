package com.jms.seafoodai.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    /** 이메일 중복 여부 확인 (존재하면 1, 없으면 0) */
    int countByEmail(String email);

    /** 신규 사용자 INSERT */
    void insertUser(java.util.Map<String, Object> paramMap);

    /** 이메일로 사용자 정보 조회 */
    java.util.Map<String, Object> findByEmail(String email);

    /** 특정 역할군 사용자 목록 조회 */
    java.util.List<java.util.Map<String, Object>> findUsersByRole(String role);

    /** 생산자 최초 거래 등록 */
    int insertInitialTrade(java.util.Map<String, Object> paramMap);

    /** 유통 파트너 목록 조회 (도매/소매업자) */
    java.util.List<java.util.Map<String, Object>> findPartners();

    /** 거래 상태 업데이트 */
    int updateTradeStatus(@org.apache.ibatis.annotations.Param("traceId") String traceId, @org.apache.ibatis.annotations.Param("status") String status);

    /** 거래 수량 차감 업데이트 */
    int deductTradeQuantity(@org.apache.ibatis.annotations.Param("traceId") String traceId, @org.apache.ibatis.annotations.Param("deductedQty") int deductedQty);

    /** 특정 거래 내역 단건 조회 */
    java.util.Map<String, Object> findTradeById(String traceId);

    /** 자식 거래(새로운 유통 단계) 등록 */
    int insertChildTrade(java.util.Map<String, Object> childTrade);

    /** 대기 중인 거래 목록 조회 (보안 필터링 적용) */
    java.util.List<java.util.Map<String, Object>> findPendingTrades(@org.apache.ibatis.annotations.Param("currentUserId") String currentUserId);

    /** 기준 시세 Upsert */
    int upsertFairPrice(java.util.Map<String, Object> paramMap);

    /** 특정 어종의 가장 최근 기준 시세 조회 (오늘 또는 과거 최근) */
    java.util.Map<String, Object> findLatestFairPrice(@org.apache.ibatis.annotations.Param("fishType") String fishType);

    /** 재고 방출 업데이트 */
    int releaseInventory(java.util.Map<String, Object> paramMap);

    /** fair_price_status 컬럼 추가 자동 DDL 방어 */
    void addFairPriceStatusColumnIfNotExists();

    /** 내 창고 재고 목록 조회 */
    java.util.List<java.util.Map<String, Object>> findMyInventory(@org.apache.ibatis.annotations.Param("userId") String userId);

    /** 유통 이력 역추적 재귀 조회 */
    java.util.List<java.util.Map<String, Object>> findTraceTimeline(@org.apache.ibatis.annotations.Param("traceId") String traceId);
}
