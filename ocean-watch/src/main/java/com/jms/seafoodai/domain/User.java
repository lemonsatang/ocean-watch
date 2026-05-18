package com.jms.seafoodai.domain;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자 통합 객체 - Controller 입력, Service 처리, Mapper DB 매핑에 모두 사용.
 * DTO/Entity 분리 없이 단일 클래스로 전 레이어를 관통한다.
 */
public class User {

    private String userId;

    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;       // 입력 시 평문, Service에서 BCrypt 암호화 후 저장

    @NotBlank
    private String userName;

    @NotNull
    private String role;           // user_role ENUM 값: ADMIN, PRODUCER, WHOLESALER, RETAILER, CONSUMER

    @NotBlank
    @Pattern(regexp = "^[0-9]{10,11}$", message = "전화번호는 10~11자리 숫자여야 합니다.")
    private String phone;

    private String businessNo;     // 선택 입력
    private String representativeName; // 대표자명 (유통업자용)
    private String businessAddress;    // 사업장 주소 (유통업자용)

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ── Getters & Setters ──────────────────────────────────────────

    public String getUserId()                      { return userId; }
    public void setUserId(String userId)            { this.userId = userId; }

    public String getEmail()                     { return email; }
    public void setEmail(String email)           { this.email = email; }

    public String getPassword()                  { return password; }
    public void setPassword(String password)     { this.password = password; }

    public String getUserName()                  { return userName; }
    public void setUserName(String userName)     { this.userName = userName; }

    public String getRole()                      { return role; }
    public void setRole(String role)             { this.role = role; }

    public String getPhone()                     { return phone; }
    public void setPhone(String phone)           { this.phone = phone; }

    public String getBusinessNo()                { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }

    public String getRepresentativeName()        { return representativeName; }
    public void setRepresentativeName(String representativeName) { this.representativeName = representativeName; }

    public String getBusinessAddress()           { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }

    public OffsetDateTime getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt)       { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt()                     { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt)       { this.updatedAt = updatedAt; }
}
