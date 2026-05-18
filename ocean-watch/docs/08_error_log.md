# 08_error_log.md - 시스템 오류 및 문제 해결 기록서

본 문서는 개발 도중 또는 운영 과정에서 발생한 시스템 오류, 예외(Exception), 데이터 무결성 깨짐 현상과 이에 대한 원인 분석 및 해결 코드를 기록하는 문서이다. 주기적으로 기록하여 동일한 아키텍처적 실수를 예방한다.

---

## 1. 오류 기록 템플릿 (작성 가이드)
안티그래비티 또는 개발자가 오류를 해결했을 때 아래 포맷으로 본 문서의 하단에 로그를 누적한다.

### [ERR-001] 오류 명칭 템플릿 (예: 계층형 쿼리 순환 참조 에러)
* **발생 일시:** YYYY-MM-DD HH:mm:ss
* **발생 컴포넌트:** `TradeService.java` / `vw_consumer_trace` View 등
* **에러 로그 및 증상:**
  > `ERROR: cyclic dependency detected in recursive query` 등 에러 메시지 작성
  > 소비자가 특정 QR 스캔 시 500 인터널 서버 에러 발생하며 무한 루프 도는 현상.
* **원인 분석:** 재판매 등록 시 실수로 `parent_trace_id`에 자기 자신의 `trace_id`를 넣었거나, 유통 경로가 순환 구조로 잘못 입력되어 발생함.
* **해결 방법:** 1. 거래 등록 API 단에서 `parent_trace_id`가 자기 자신과 동일할 수 없도록 Java 밸리데이션 추가.
  2. 데이터베이스 테이블에 `CHECK (trace_id != parent_trace_id)` 제약조건 DDL 추가 적용.
* **조치 상태:** 완료 (Status: SOLVED)

---

## 2. 실시간 오류 및 트러블슈팅 로그 목록

*(여기에 발생한 에러 이력을 ERR-002부터 차례대로 기록해 나가세요.)*

### [ERR-002] UnsupportedClassVersionError — Java 버전 불일치 컴파일 에러
* **발생 일시:** 2026-05-17
* **발생 컴포넌트:** 전체 프로젝트 빌드 (Maven compile)
* **에러 로그 및 증상:**
  > `UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime`
  > 로컬 환경에 Java 11만 설치되어 있으나, Spring Boot 3.3.0 + Java 17로 설정된 pom.xml이 Java 17 바이트코드를 생성해 실행 불가.
* **원인 분석:** `pom.xml`의 `<java.version>17</java.version>` 설정이 로컬 JDK 11과 불일치. Spring Boot 3.x는 Java 17 이상 필수.
* **해결 방법:**
  1. Spring Boot `3.3.0` → `2.7.18` 다운그레이드 (Java 11 호환 최신 안정 버전).
  2. `<java.version>17</java.version>` → `<java.version>11</java.version>` 변경.
  3. MyBatis Starter `3.0.3` → `2.3.2` 변경 (Spring Boot 2.x 호환 버전).
  4. 전체 소스의 `jakarta.validation` 패키지 → `javax.validation` 로 일괄 교체 (Spring Boot 2.x는 javax 패키지 사용).
* **조치 상태:** 완료 (Status: SOLVED)

### [ERR-003] MyBatis BindingException — Invalid bound statement (not found) 에러
* **발생 일시:** 2026-05-18 09:27:46
* **발생 컴포넌트:** `UserMapper.java` / `UserMapper.xml` / `application.yml`
* **에러 로그 및 증상:**
  > `org.apache.ibatis.binding.BindingException: Invalid bound statement (not found): com.jms.seafoodai.mapper.UserMapper.countByEmail`
  > 회원가입 페이지에서 이메일을 입력하면 이메일 중복 검사 API(`/api/v1/auth/check-email`) 실행 중 백엔드에서 MyBatis 바인딩 에러가 발생해 요청이 실패하는 현상.
* **원인 분석:**
  자바 인터페이스 `UserMapper`와 XML의 namespace 경로는 `com.jms.seafoodai.mapper.UserMapper`로 정확히 일치했으나, `application.yml`에 MyBatis의 XML Mapper 위치 설정인 `mybatis.mapper-locations`가 누락되어 발생했습니다. MyBatis Spring Boot Starter는 명시적인 스캔 경로 지정이 없을 경우 인터페이스와 동일한 패키지 폴더 내의 XML 파일만 탐색하므로, `src/main/resources/mapper/UserMapper.xml`에 정의된 쿼리들을 전혀 읽어들이지 못해 발생한 문제였습니다.
* **해결 방법:**
  `application.yml`의 최상위 레벨에 `mybatis.mapper-locations` 설정을 명시적으로 추가하여 리소스 디렉터리의 모든 Mapper XML 파일들이 스캔되도록 조치했습니다.
  ```yaml
  mybatis:
    mapper-locations: classpath:mapper/**/*.xml
  ```
* **조치 상태:** 완료 (Status: SOLVED)

### [ERR-004] Map.of() NullPointerException — 로그인 Response 생성 과정 중 NPE 에러
* **발생 일시:** 2026-05-18 10:02:52
* **발생 컴포넌트:** `AuthController.java` (로그인 API 엔드포인트 `/api/v1/auth/login`)
* **에러 로그 및 증상:**
  ```
  java.lang.NullPointerException: null
      at java.base/java.util.Objects.requireNonNull(Objects.java:209)
      at java.base/java.util.Map.of(Map.java:1419)
      at com.jms.seafoodai.controller.AuthController.login(AuthController.java:64)
  ```
  로그인 진행 시 `Map.of()` 메소드 내부에서 `NullPointerException`이 발생하여 500 에러 혹은 가입 실패/조회 실패 처리가 발생하는 현상.
* **원인 분석:**
  Java 9에 도입된 `Map.of()`는 모든 Key와 Value가 `null`이 아니어야(Non-null) 합니다. 데이터 조회의 결과값이나 로그인 시도 객체의 필드(예: 유통업자의 사업장 주소 등이나 선택 필드가 `null`인 경우)에 `null`이 전달될 경우, `Map.of()` 내부의 `Objects.requireNonNull()` 검증에 걸려 즉시 `NullPointerException`을 발생시킵니다.
* **해결 방법:**
  `null` 값을 안전하게 수용하고 처리할 수 있는 표준 자바 컬렉션인 **`java.util.HashMap`**을 활용하여 응답 구조를 동적으로 매핑하도록 수정하였습니다.
  ```java
  Map<String, Object> response = new java.util.HashMap<>();
  response.put("status", 200);
  response.put("code", "SUCCESS");
  response.put("message", "로그인이 완료되었습니다.");
  
  Map<String, Object> userData = new java.util.HashMap<>();
  userData.put("userId", user.getUserId());
  userData.put("email", user.getEmail());
  userData.put("userName", user.getUserName());
  userData.put("role", user.getRole());
  
  response.put("data", userData);
  ```
* **조치 상태:** 완료 (Status: SOLVED)

### [ERR-005] violates not-null constraint on user_id — 최초 거래 등록 시 생산자 ID 바인딩 누락 및 타입 불일치 에러
* **발생 일시:** 2026-05-18 11:01:42
* **발생 컴포넌트:** `AuthController.java` (최초 거래 등록 API 엔드포인트 `/api/v1/trade/register`) / `UserMapper.xml` (`insertInitialTrade`)
* **에러 로그 및 증상:**
  > `PostgreSQL violates not-null constraint on column "user_id" of relation "tb_watch_trade"`
  > 생산물 등록 화면(`trade-register.html`)에서 최초 거래 등록 요청을 제출할 때, 데이터베이스 인서트 과정에서 `user_id` 칼럼에 `NULL`이 대입되어 예외가 발생하고 거래 등록에 실패하는 현상.
* **원인 분석:**
  1. 컨트롤러(`AuthController.java`)에서 세션(`loginUser`)으로부터 꺼내온 고유 식별자 객체(`java.util.UUID`)를 MyBatis 파라미터 맵에 넘겨줄 때, 드라이버 레벨에서 `UUID` 인스턴스가 `null`로 잘못 평가되거나 타입 불일치로 누락되는 현상이 발생했습니다.
  2. 또한 파트너 비지정 매물(공개 매물) 도입 과정에서 `receiverId` 빈 문자열 `""` 처리에 대한 타입 방어 코드가 미흡해 파라미터 맵 구성이 완전치 않았습니다.
* **해결 방법:**
  1. 컨트롤러 단에서 세션의 `loginUser` 고유 식별자를 매퍼로 전송하기 전에 **`.toString()`** 함수를 사용하여 명시적으로 표준 문자열(`String`) 형태로 형변환하여 바인딩했습니다.
  2. `receiverId` 필드 역시 `null` 및 빈 공백 체크 방어 로직을 추가하여 데이터 타입을 엄격하게 동기화했습니다.
  ```java
  Map<String, Object> paramMap = new java.util.HashMap<>();
  paramMap.put("userId", loginUser.getUserId().toString()); // 명시적 String 형변환
  paramMap.put("receiverId", body.get("receiverId") != null && !body.get("receiverId").toString().trim().isEmpty() ? body.get("receiverId").toString() : null);
  ```
* **조치 상태:** 완료 (Status: SOLVED)

### [ERR-006] Map UUID Mapping NullPointerException — MyBatis 자동 매핑 실패로 인한 userId 누락 에러
* **발생 일시:** 2026-05-18 11:03:55
* **발생 컴포넌트:** `UserMapper.xml` (`findByEmail`) / `AuthController.java` (세션 바인딩)
* **에러 로그 및 증상:**
  ```
  java.lang.NullPointerException: Cannot invoke "java.util.UUID.toString()" because the return value of "com.jms.seafoodai.domain.User.getUserId()" is null
  ```
  최초 거래 등록 시 세션에서 꺼낸 `loginUser.getUserId()` 값이 `null`이어서 `.toString()` 호출 중 NullPointerException 예외가 발생하는 현상.
* **원인 분석:**
  MyBatis에서 `resultType="com.jms.seafoodai.domain.User"`를 사용해 자동 매핑을 시도할 때, PostgreSQL의 `UUID` 데이터 타입인 `user_id` 칼럼을 Java의 `java.util.UUID` 타입인 `userId` 필드로 명시적 형변환 처리하지 못해 auto-mapping 과정에서 해당 필드가 그냥 `null`로 누락되어 저장되었습니다. 이로 인해 회원 정보 로드 시 이메일/이름 등은 다 채워졌지만 정작 기본키인 `userId`만 유실된 상태로 세션에 캐싱되어 오류를 일으켰습니다.
* **해결 방법:**
  1. `UserMapper.xml` 내부에 전용 `<resultMap>`인 `UserResultMap`을 정의하고, 별도의 복잡한 타입핸들러나 JDBC 타입 지정 없이 순정 스타일로 `<id property="userId" column="user_id"/>` 형태로 수동 매핑을 지정했습니다.
  2. `findByEmail` 및 `findUsersByRole` select 쿼리의 결과 형식을 `resultMap="UserResultMap"`으로 교정하여 DB 조회 데이터가 자바 필드에 정밀 바인딩되도록 보장했습니다.
  3. `User.java` 객체의 `userId` 필드 타입을 `UUID`에서 자바 표준 `String`으로 과감하게 변환하여, MyBatis와 PostgreSQL 드라이버 간의 UUID 타입 바인딩 매핑 예외를 원천 차단했습니다.
  ```xml
  <resultMap id="UserResultMap" type="com.jms.seafoodai.domain.User">
      <id property="userId" column="user_id"/>
      ...
  </resultMap>
  ```
* **조치 상태:** 완료 (Status: SOLVED)