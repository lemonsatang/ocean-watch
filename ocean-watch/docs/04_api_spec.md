# 04_api_spec.md - 백엔드 API 설계 명세서 (Spring Boot / RESTful)

본 문서는 수산물 디지털 이력 기반 AI 공정가격 보증 플랫폼의 백엔드 API 명세서이다. Spring Boot 환경에서 구현되며, Supabase JWT 토큰 기반의 인가(Authorization)와 DTO 객체를 통한 강력한 유효성 검사(`@Valid`)를 포함한다.

---

## 1. 공통 응답 규격 (Common Response)

모든 API는 클라이언트가 일관성 있게 에러와 데이터를 처리할 수 있도록 아래의 공통 봉투(Envelope) 패턴을 사용한다.

```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { ... } // 실제 응답 데이터 (없을 경우 null)
}
```

---

## 2. 인증 및 회원 관리 API (Auth)

Supabase Auth와 연동되지만, 백엔드 단에서 추가적인 유효성 검증과 마스터 테이블(`tb_user`) 저장을 수행한다.

### 2.1 이메일 중복 확인
* **Endpoint:** `GET /api/v1/auth/check-email`
* **권한:** `PERMIT_ALL` (비로그인 허용)
* **Request Params:**
  * `email` (String, 필수, 이메일 형식)
* **Response Data:** `{"isAvailable": true}`

### 2.2 회원가입
* **Endpoint:** `POST /api/v1/auth/signup`
* **권한:** `PERMIT_ALL`
* **Request Body:** `@Valid` 적용 필수
  ```json
  {
    "email": "user@example.com", // @Email, @NotBlank
    "password": "Password123!",  // @NotBlank, 프론트에서 확인 후 전송 (이후 Supabase 연동 처리)
    "userName": "홍길동",        // @NotBlank
    "phone": "01012345678",      // @Pattern(regexp="^[0-9]+$")
    "role": "PRODUCER",          // @NotNull, Enum 매핑
    "businessNo": "1234567890"   // 선택적
  }
  ```
* **Response Data:** `{"userId": "uuid-string"}`

---

## 3. 유통 거래 이력 API (Trade)

수산물의 이동 및 상태를 변경하는 핵심 API이다. 발송자(SENDER)와 수취자(RECEIVER) 간의 상태 전이를 처리한다.

### 3.1 신규 거래 등록 (최초 등록 및 재판매)
* **Endpoint:** `POST /api/v1/trades`
* **권한:** `PRODUCER`, `WHOLESALER`, `RETAILER`
* **Request Body:**
  ```json
  {
    "parentTraceId": "uuid-string", // 생산자 최초 등록 시 null
    "receiverId": "uuid-string",    // 인수 받을 대상 사용자 ID
    "fishCode": "F001",             // 어종 마스터 코드
    "buyPrice": 15000.00,           // 매입가 (생산자는 0)
    "sellPrice": 18000.00,          // 판매가 (@Min(1))
    "qty": 50.5                     // 판매 수량 (kg)
  }
  ```
* **동작 로직:** DB `INSERT` 전 `TB_AI_FAIR_PRICE`를 조회하여 현재 판매가에 대한 `aiFairYn` 판정 결과를 함께 저장.
* **Response Data:** `{"traceId": "uuid-string", "aiFairYn": "Y"}`

### 3.2 거래 승인 / 부분 승인 / 거절 처리
* **Endpoint:** `PATCH /api/v1/trades/{traceId}/status`
* **권한:** `WHOLESALER`, `RETAILER`
* **Request Body:**
  ```json
  {
    "status": "PARTIAL_APPROVED", // APPROVED, PARTIAL_APPROVED, REJECTED
    "adjustedQty": 45.0           // 부분 승인(수량 정정) 시 입력한 실제 인수 수량
  }
  ```
* **동작 로직:** * `REJECTED`일 경우 데이터는 삭제하지 않고 상태만 업데이트 (이후 목록 조회 시 숨김 처리됨).
  * `PARTIAL_APPROVED`일 경우 `TB_DISTRIBUTION_TRACE`의 `qty`를 `adjustedQty`로 갱신.
* **Response Data:** `null` (공통 응답 `SUCCESS`만 반환)

---

## 4. 소비자 및 공공 조회 API (Consumer & Public)

### 4.1 소비자 QR 스캔 유통 이력 전체 조회
* **Endpoint:** `GET /api/v1/consumer/traces/{traceId}`
* **권한:** `PERMIT_ALL` (앱 설치 없는 모바일 웹 조회)
* **동작 로직:** `VW_CONSUMER_TRACE` (재귀 뷰)를 조회하여 최상단 생산자부터 현재 소매업자까지의 과정을 Array 형태로 반환.
* **Response Data:**
  ```json
  [
    {
      "stage": "PRODUCER",
      "userName": "김어부",
      "sellPrice": 10000,
      "aiFairYn": "Y",
      "createdAt": "2026-05-17T10:00:00Z"
    },
    {
      "stage": "WHOLESALER",
      "userName": "바다유통",
      "sellPrice": 12000,
      "aiFairYn": "Y",
      "createdAt": "2026-05-18T09:00:00Z"
    }
  ]
  ```

---

## 5. 마이페이지 대시보드 통계 API (Dashboard)

사용자 역할별 맞춤형 시각화 데이터를 제공하기 위한 집계형 API. 성능 최적화를 위해 DB 인덱스를 적극 활용한다.

### 5.1 생산자 대시보드 통계
* **Endpoint:** `GET /api/v1/dashboard/producer`
* **권한:** `PRODUCER`
* **응답 내용:** 월간 누적 판매량(kg), 어종별 평균 판매가 vs KAMIS 공공 평균 시세 비교 객체.

### 5.2 도매업자 재고 및 마진 통계
* **Endpoint:** `GET /api/v1/dashboard/wholesaler`
* **권한:** `WHOLESALER`
* **응답 내용:** 창고 미판매 재고 목록(`status IN ('APPROVED', 'PARTIAL_APPROVED')` 및 자식 노드 없음), 평균 유통 마진율(%), 수량 정정에 의한 누적 감량 통계.

### 5.3 소매업자 조회 통계
* **Endpoint:** `GET /api/v1/dashboard/retailer`
* **권한:** `RETAILER`
* **응답 내용:** 발급한 QR코드의 실 소비자 스캔 카운트, AI 공정가 초록불(Y) 달성 횟수 및 비율.