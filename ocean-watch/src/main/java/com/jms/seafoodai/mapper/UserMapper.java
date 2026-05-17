package com.jms.seafoodai.mapper;

import com.jms.seafoodai.domain.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    /** 이메일 중복 여부 확인 (존재하면 1, 없으면 0) */
    int countByEmail(String email);

    /** 신규 사용자 INSERT */
    void insertUser(User user);
}
