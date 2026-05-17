# 03_db_schema.md - 데이터베이스 설계 명세서 (Supabase / PostgreSQL)

본 문서는 수산물 디지털 이력 기반 AI 공정가격 보증 플랫폼의 데이터베이스 스키마 정의서이다. Supabase(PostgreSQL) 환경을 기준으로 작성되었으며, 역할 기반 권한 제어(RBAC), 계층형 트리 조회, 통계 대시보드 최적화를 위한 인덱스 설계를 포함한다.

---

## 1. 전역 데이터 타입 및 ENUM 정의

```sql
-- 사용자 역할군 정의
CREATE TYPE user_role AS ENUM ('ADMIN', 'PRODUCER', 'WHOLESALER', 'RETAILER', 'CONSUMER');

-- 거래 상태 정의
CREATE TYPE trade_status AS ENUM ('PENDING', 'APPROVED', 'PARTIAL_APPROVED', 'REJECTED');

-- AI 공정가 판정 결과 정의
CREATE TYPE ai_fair_status AS ENUM ('Y', 'W', 'N');
```

---

## 2. 테이블 DDL 및 제약조건

### 2.1 사용자 테이블 (`tb_watch_user`)
Supabase(PostgreSQL)에 저장되는 사용자 마스터 정보 테이블.  
Spring Security 자체 인증(BCrypt)을 사용하므로 `password` 컬럼에 BCrypt 해시값을 저장한다.

```sql
-- 사용자 역할군 ENUM (미존재 시 먼저 생성)
CREATE TYPE user_role AS ENUM ('ADMIN', 'PRODUCER', 'WHOLESALER', 'RETAILER', 'CONSUMER');

CREATE TABLE tb_watch_user (
    user_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(100) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,              -- BCrypt 해시값 저장
    user_name    VARCHAR(50)  NOT NULL,
    role         user_role    NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    business_no  VARCHAR(20),                        -- 선택 입력 (유통업자/생산자용)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```


### 2.2 유통 거래 이력 테이블 (`TB_DISTRIBUTION_TRACE`)
수산물 유통의 핵심 계층 구조(Tree)를 표현하며, 부분 승인 및 거절 숨김 로직의 기준이 된다.

```sql
CREATE TABLE tb_distribution_trace (
    trace_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_trace_id  UUID REFERENCES tb_distribution_trace(trace_id) ON DELETE SET NULL,
    sender_id        UUID NOT NULL REFERENCES tb_user(user_id),
    receiver_id      UUID NOT NULL REFERENCES tb_user(user_id),
    fish_code        VARCHAR(10) NOT NULL, -- 어종 마스터 코드
    buy_price        NUMERIC(12, 2) NOT NULL DEFAULT 0.00, -- 매입 단가
    sell_price       NUMERIC(12, 2) NOT NULL, -- 판매 단가
    qty              NUMERIC(10, 2) NOT NULL, -- 중량/수량 (kg 등)
    status           trade_status NOT NULL DEFAULT 'PENDING',
    ai_fair_yn       ai_fair_status NOT NULL DEFAULT 'Y',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 도/소매업자 입력 무결성 검증 (판매가는 항상 0보다 커야 함)
    CONSTRAINT chk_sell_price CHECK (sell_price > 0)
);
```

### 2.3 공공 가격 시계열 테이블 (`TB_PRICE_HISTORY`)
KAMIS 및 공공데이터 포털 연동 데이터 수집용 테이블이다. 데이터 누적을 고려하여 월별 파티셔닝을 적용한다.

```sql
CREATE TABLE tb_price_history (
    record_date   DATE NOT NULL,
    fish_code     VARCHAR(10) NOT NULL,
    prod_price    NUMERIC(12, 2) NOT NULL, -- 산지 가격
    whole_price   NUMERIC(12, 2) NOT NULL, -- 도매 가격
    retail_price  NUMERIC(12, 2) NOT NULL, -- 소매 가격
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (record_date, fish_code)
) PARTITION BY RANGE (record_date);
```

### 2.4 AI 산출 적정가 밴드 테이블 (`TB_AI_FAIR_PRICE`)
AI 모델이 매일 산출하는 어종별 적정 최소/최대 소매가 밴드 정보이다.

```sql
CREATE TABLE tb_ai_fair_price (
    calc_date       DATE NOT NULL,
    fish_code       VARCHAR(10) NOT NULL,
    min_fair_price  NUMERIC(12, 2) NOT NULL,
    max_fair_price  NUMERIC(12, 2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (calc_date, fish_code)
);
```

### 2.5 시스템 감사 로그 테이블 (`TB_AUDIT_LOG`)
관리자 관제 및 시스템 주요 변경 사항에 대한 이력을 보존한다.

```sql
CREATE TABLE tb_audit_log (
    log_id       BIGSERIAL PRIMARY KEY,
    user_id      UUID REFERENCES tb_user(user_id) ON DELETE SET NULL,
    action_type  VARCHAR(50) NOT NULL, -- e.g., 'USER_APPROVAL', 'SYSTEM_ERROR', 'PRICE_ADJUST'
    target_id    UUID,                 -- 영항을 받은 대상 ID (trace_id 또는 user_id)
    details      TEXT,                 -- 상세 변경 내역 (JSON 스트링 등)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 3. 성능 최적화 및 비즈니스 특화 인덱스 설계

### 3.1 거절 거래 숨김 및 조회 최적화 인덱스
도/소매업자가 거절(`REJECTED`)한 거래는 본인 대기 목록에서 즉시 숨김 처리되어야 하므로, 해당 상태값을 필터링하는 조건절 쿼리 속도를 보장하기 위해 복합 인덱스를 구성한다.

```sql
-- 수취인별 대기/승인 거래 고속 조회를 위한 인덱스 (REJECTED 필터링 최적화)
CREATE INDEX idx_trace_receiver_status 
ON tb_distribution_trace(receiver_id, status) 
WHERE status != 'REJECTED';

-- 발송인(판매자)이 거절된 내역을 마이페이지에서 확인하기 위한 인덱스
CREATE INDEX idx_trace_sender_rejected 
ON tb_distribution_trace(sender_id) 
WHERE status = 'REJECTED';
```

### 3.2 역할별 마이페이지 대시보드 통계용 인덱스
사용자 대시보드에서 마진율 계산, 재고 체류 시간(에이징) 조회 시 발생하는 대량 집계(Aggregation) 쿼리의 부하를 최소화한다.

```sql
-- 생산자/유통업자의 어종별 마진 및 거래량 통계 속도 향상
CREATE INDEX idx_trace_stats 
ON tb_distribution_trace(sender_id, fish_code, created_at);

-- 도매업자 창고 미판매 재고(APPROVED 상태이나 아직 자식 노드가 없는 상태) 추적용 인덱스
CREATE INDEX idx_trace_inventory 
ON tb_distribution_trace(receiver_id, status) 
WHERE status IN ('APPROVED', 'PARTIAL_APPROVED');
```

---

## 4. 데이터 조회 및 무결성 구현 규칙 (Database View)

### 4.1 소비자용 유통 이력 추적 뷰 (`VW_CONSUMER_TRACE`)
소비자가 QR 코드를 스캔했을 때, 계층형 트리 구조(`parent_trace_id`)를 역추적하여 한 번에 유통 전 과정을 리턴하기 위한 재귀(Recursive) 쿼리 기본 모델이다.

```sql
CREATE OR REPLACE VIEW vw_consumer_trace AS
WITH RECURSIVE dependency_tree AS (
    -- 최하단 소매 단계(Anchor)
    SELECT 
        trace_id, parent_trace_id, sender_id, receiver_id, 
        fish_code, buy_price, sell_price, qty, status, ai_fair_yn, created_at,
        1 AS current_stage
    FROM tb_distribution_trace
    WHERE status IN ('APPROVED', 'PARTIAL_APPROVED')
    
    UNION ALL
    
    -- 상위 유통 단계로 재귀 추적 (Branch)
    SELECT 
        t.trace_id, t.parent_trace_id, t.sender_id, t.receiver_id, 
        t.fish_code, t.buy_price, t.sell_price, t.qty, t.status, t.ai_fair_yn, t.created_at,
        dt.current_stage + 1
    FROM tb_distribution_trace t
    INNER JOIN dependency_tree dt ON t.trace_id = dt.parent_trace_id
)
SELECT * FROM dependency_tree;
```