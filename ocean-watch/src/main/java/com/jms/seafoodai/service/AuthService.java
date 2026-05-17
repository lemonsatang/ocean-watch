package com.jms.seafoodai.service;

import com.jms.seafoodai.domain.User;
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
    public void signup(User user) {
        if (!isEmailAvailable(user.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        // 평문 비밀번호를 BCrypt 해시로 교체 후 저장
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insertUser(user);
    }
}
