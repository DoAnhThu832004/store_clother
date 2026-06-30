package com.example.store_clothes.security;

import com.example.store_clothes.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl - Nạp thông tin người dùng cho Spring Security.
 *
 * Spring Security gọi loadUserByUsername() trong quá trình:
 * 1. Đăng nhập: AuthenticationManager.authenticate() → loadUserByUsername().
 * 2. Mỗi request có JWT: JwtAuthFilter → loadUserByUsername() để validate.
 *
 * @Transactional(readOnly = true): Tối ưu — chỉ đọc DB, không cần Dirty Checking.
 * User.roles = EAGER → load cùng 1 query, không gây thêm query extra.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Không tìm thấy người dùng: " + username));
    }
}
