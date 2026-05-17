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