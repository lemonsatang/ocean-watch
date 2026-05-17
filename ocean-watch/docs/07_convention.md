# 07_convention.md - 코딩 컨벤션 및 표준 가이드

본 문서는 수산물 AI 공정가격 플랫폼 개발 시 코드의 일관성과 유지보수성을 확보하기 위한 코딩 표준 및 네이밍 규칙이다. 
**[핵심 주의사항]** 현재 본 프로젝트는 이미 개발이 진행 중인 상태이다. 따라서 안티그래비티(AI)는 새로운 코드를 작성하거나 수정할 때, **가장 최우선으로 기존 코드베이스의 스타일과 아키텍처를 존중해야 하며, 명시적인 요청 없이 기존 코드를 대대적으로 리팩토링해서는 안 된다.**

> [!IMPORTANT]
> **[2026-05-17 확정] 런타임 환경 제약**
> - **Java: 11** (로컬 개발 환경 기준 — Java 17 미설치)
> - **Spring Boot: 2.7.18** (Java 11 호환 최신 안정 버전)
> - **MyBatis Starter: 2.3.2** (Spring Boot 2.x 호환)
> - Validation 애노테이션 패키지는 반드시 **`javax.validation`** 을 사용한다. (`jakarta.validation` 사용 금지 — Spring Boot 3.x 전용)

---

## 1. 전역 네이밍 규칙 (Global Naming Convention)

### 1.1 데이터베이스 (PostgreSQL / Supabase)
* **테이블 및 컬럼명:** 소문자와 언더바를 사용하는 `snake_case`를 원칙으로 한다.
  * 예: `user_id`, `tb_watch_distribution_trace`, `created_at`
* **테이블 접두사:** 모든 물리 테이블명은 `tb_watch_`로 시작한다.
* **뷰 접두사:** 뷰(View) 테이블은 `vw_`로 시작한다.

### 1.2 백엔드 (Java / Spring Boot)
* **클래스명:** 대문자로 시작하는 `PascalCase`를 사용한다. (예: `TradeService`, `UserController`)
* **메서드 및 변수명:** 소문자로 시작하는 `camelCase`를 사용한다. (예: `getTraceList()`, `traceId`)
* **상수(Constant):** 모두 대문자를 사용하며 언더바로 구분하는 `UPPER_SNAKE_CASE`를 사용한다. (예: `MAX_UPLOAD_SIZE`)

### 1.3 REST API URI 규칙
* URI는 소문자와 하이픈(-)을 사용하는 `kebab-case`를 사용한다.
* 자원(Resource)의 이름은 복수형 명사를 사용한다.
  * ❌ Bad: `/api/v1/getTrade`, `/api/v1/user-info`
  * 🟢 Good: `/api/v1/trades`, `/api/v1/users`

---

## 2. 레이어별 개발 표준 및 MyBatis 규칙

### 2.1 Spring Boot 레이어 아키텍처 원칙 (단순화 방침)
> **[2026-05-17 확정]** Simplicity First 원칙에 따라 DTO / VO / Entity 클래스를 분리하지 않는다.
> 데이터는 **단일 도메인 객체** (예: `User`)가 Controller 입력 → Service 처리 → Mapper DB 매핑까지 전 레이어를 그대로 관통한다.
> API 응답은 별도 응답 DTO 없이 `Map<String, Object>`를 사용하는 공통 Envelope 패턴으로 처리한다.

* **Controller:** `@Valid`가 붙은 단일 도메인 객체(`@RequestBody`)로 입력을 받고 `Map`으로 응답을 반환한다. 비즈니스 로직은 포함하지 않는다.
* **Service:** BCrypt 암호화, 중복 체크 등 핵심 비즈니스 로직과 `@Transactional`을 담당한다.
* **Mapper:** `@Mapper` 인터페이스 방식을 사용하며, SQL은 `src/main/resources/mapper/*.xml`에 격리한다.

### 2.2 MyBatis 및 SQL 표준
* **SQL 키워드:** `SELECT`, `FROM`, `WHERE`, `JOIN` 등 SQL 예약어는 반드시 **대문자**로 작성하여 가독성을 높인다.
* **Mapper 파일(XML):** 쿼리의 ID는 Java Mapper 인터페이스의 메서드명과 완벽히 일치해야 한다.
* **동적 쿼리:** MyBatis의 `<if>`, `<choose>` 태그를 활용하되, 복잡한 비즈니스 로직을 쿼리 안에 과도하게 넣지 않는다.

---

## 3. 주석 및 커밋 메시지 규칙

### 3.1 코드 주석 (Comments)
* 클래스와 주요 메서드 상단에는 Javadoc 스타일(`/** ... */`)로 해당 컴포넌트의 목적과 동작을 간략히 설명한다.
* 로직이 복잡한 부분이나 분기점(`if-else`)에는 코드 라인 위에 `//` 를 사용하여 왜 이렇게 구현했는지(Why) 위주로 설명한다.

### 3.2 Git 커밋 메시지 (Commit Message)
* 안티그래비티가 작업 내역을 요약할 때 아래의 Prefix 형식을 준수한다.
  * `feat`: 새로운 기능 추가
  * `fix`: 버그 수정
  * `docs`: 문서(MD 파일 등) 수정
  * `style`: 코드 포맷팅, 세미콜론 누락 수정 등 (비즈니스 로직 변경 없음)
  * `refactor`: 코드 리팩토링 (기능 변화 없음)
  * `chore`: 빌드 업무 수정, 패키지 매니저 수정