<div align="center">
  <img src="https://img.shields.io/badge/Ocean-Watch-0e9abf?style=for-the-badge&logo=anchor&logoColor=white" alt="OceanWatch Logo" />
  <h1>🌊 OceanWatch (스마트 수산물 안심 유통 플랫폼)</h1>
  <p>블록체인 스타일 데이터 체인 역추적 및 자바 내장형 AI 기반 수산물 공정가격 유도 시스템</p>
</div>

<br/>

> [!NOTE]  
> **OceanWatch**는 수산물 유통망의 고질적인 불투명성과 일부 도·소매업자의 폭리 문제를 근본적으로 해결하기 위해 탄생했습니다. 최초 생산자부터 최종 소매상까지의 모든 유통 단계를 블록체인 노드처럼 연결하여 역추적(`Traceability`)을 제공하며, 외부 의존성 없이 백엔드 자체 연산력만으로 구동되는 **AI 스마트 가격 추천 엔진**을 통해 시장의 공정 마진을 유도합니다.

---

## 🛠️ 기술 스택 (Tech Stack)

### Backend & Database
- **Language**: Java 1.8
- **Framework**: Spring Boot
- **Build Tool**: Maven
- **ORM**: MyBatis (Map 기반 순수 아키텍처, DTO 무사용)
- **Database**: PostgreSQL (Supabase 원격 연동)

### Frontend & UI/UX
- **Markup / Styling**: HTML5, CSS3
- **Script**: Vanilla JavaScript
- **Design System**: 프리미엄 모바일 반응형 Glassmorphism (유리 질감 테마)

### External Integrations
- **Open API**: 한국농수산식품유통공사(KAMIS) 실시간 도·소매 시세 API 연동

---

## 🧠 핵심 아키텍처 및 핵심 기능 (Key Features)

### 1. 📡 실시간 공공데이터 파이프라인 (`KamisPriceScheduler`)
- 매일 지정된 시간에 KAMIS API를 호출하여 최신 수산물 전국 평균 도/소매 시세를 백엔드 DB로 자동 수집합니다.
- 복잡한 객체 맵핑 없이 순수 문자열 기반 자체 파싱(Self-Healing) 기법을 사용하여, 외부 API 응답 타입 에러(Text/Plain)에도 유연하게 대처하는 강력한 파이프라인입니다.

### 2. 🤖 자바 내장형 AI 가격 예측 추천 엔진
- 무거운 외부 서버(Python, Flask 등) 연결 없이, 오직 Java 기본 내장 모듈(`LocalDate`, `ChronoUnit`)의 연산력만을 극대화하여 **가벼우면서도 정밀한 AI 엔진**을 백엔드에 통합했습니다.
- **신선도 감가 알고리즘:** 입고 후 체류 일수(Aging Days)가 2일을 초과할 경우, 초과 1일당 베이스 시세의 **5%씩 동적 감가**를 적용합니다. (최대 90% 하한선 방어)
- **수요 탄력성 할증 알고리즘:** 유통업자가 방출하는 당일이 주말(금, 토, 일)일 경우, 해산물 수요 증가 예측에 따라 베이스 시세의 **10% 할증(Premium)**을 부여합니다.

### 3. 🚦 유통 폭리 방어 AI 신호등 시스템
도·소매업자가 창고에서 재고를 시장에 방출할 때, **'유저 희망 판매가'**와 **'AI 추천 방출가'**의 비율을 실시간 계산하여 시장 교란 행위를 탐지합니다.
- 🟢 **적정 공정가 (110% 이하):** 시장 친화적이고 투명한 마진율
- 🟡 **시세 높음 주의 (130% 이하):** 평균 시세 대비 약간 높은 마진율
- 🔴 **비정상 폭리 경고 (130% 초과):** 부당 이득 취득이 의심되는 위험 방출가

### 4. 🔗 PostgreSQL 재귀 쿼리(`WITH RECURSIVE`) 유통 이력 역추적
- 소비자가 매장에서 제공받은 고유 `trace_id`(QR코드)를 조회하면, **단 한 번의 쿼리**로 `tb_watch_trade` 테이블을 재귀 역추적합니다.
- 복잡한 백엔드 반복 루프 없이 DB 레벨에서 어부(생산자)부터 현재 노드까지의 연결 고리를 고속으로 파악해 냅니다.
- 프론트엔드에서는 이를 세련된 세로형 Glassmorphic 타임라인 UI로 렌더링하고, 단계별 'AI 공정가 신호등 배지'를 시각화합니다.

---

## ⚙️ 데이터베이스 스키마 요약 (Database Schema)

모든 데이터는 DTO 없이 `Map<String, Object>` 형태의 유연한 규격으로 백엔드를 관통하여 프론트엔드까지 전달됩니다.

### 👤 1. 유저 마스터 (`tb_watch_user`)
| Column Name | Type | Description |
|---|---|---|
| **user_id** (PK) | UUID | 사용자 고유 식별자 |
| email | VARCHAR | 로그인 아이디 및 알림 이메일 |
| password | VARCHAR | BCrypt 암호화 비밀번호 |
| role | ENUM | PRODUCER(어부), WHOLESALER(도매), RETAILER(소매) |
| business_no | VARCHAR | 사업자 등록번호 |
| created_at | TIMESTAMP | 계정 생성 일시 |

### 📦 2. 유통 거래 내역 (`tb_watch_trade`)
| Column Name | Type | Description |
|---|---|---|
| **trace_id** (PK) | UUID | 거래/재고 고유 식별자 (QR 코드의 근간) |
| parent_trace_id | UUID | 상위(이전) 거래의 식별자 (재귀 쿼리용) |
| user_id (FK) | UUID | 현재 재고의 소유주 (유저 ID) |
| receiver_id (FK) | UUID | 다음 유통 체인의 인수 예정자 |
| fish_type | VARCHAR | 어종명 (예: 고등어, 오징어 등) |
| trade_status | VARCHAR | 거래 상태 (PENDING, STOCKED, RELEASED 등) |
| **fair_price_status** | VARCHAR | AI 신호등 판정 결과 (Y: 🟢 / W: 🟡 / N: 🔴) |
| created_at | TIMESTAMP | 재고 입고 및 거래 발생 일시 |

> [!TIP]
> **Map 기반 아키텍처의 철학**  
> OceanWatch는 별도의 Entity, VO, DTO 클래스를 일절 생성하지 않습니다. 데이터베이스의 결과셋은 곧바로 MyBatis를 거쳐 `java.util.Map`으로 변환되며, 비즈니스 로직과 UI까지 하나의 유연한 맵(Map) 구조로 일관성 있게 흐르도록 극도의 미니멀리즘 아키텍처를 채택하고 있습니다.
>
## 화면ui
[메인]
<img width="1844" height="851" alt="image" src="https://github.com/user-attachments/assets/7f2ce0c2-c156-44e5-ac0e-85cbaa44717e" />

[로그인]
<img width="1848" height="887" alt="image" src="https://github.com/user-attachments/assets/184defc1-bd34-4eae-9b27-4b750c45c989" />

[회원가입]
<img width="1863" height="882" alt="image" src="https://github.com/user-attachments/assets/922b7cde-e3ef-49f9-bb9c-873f7fab027c" />

[수산물 유통 이력 역추적]
<img width="1842" height="884" alt="image" src="https://github.com/user-attachments/assets/13588f59-6683-4e4f-b5a4-0242a92c19c5" />

[내 창고]
<img width="1840" height="881" alt="image" src="https://github.com/user-attachments/assets/5ae2264c-e1bb-4b44-a16e-e5dbc0d7b1a7" />

[거래 승인 현황]
<img width="1859" height="885" alt="image" src="https://github.com/user-attachments/assets/7ddacf2f-ff1e-4166-a28a-102cc450d947" />


