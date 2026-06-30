package com.example.store_clothes.repository;

import com.example.store_clothes.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository - Repository cho User entity.
 *
 * @SQLRestriction("is_deleted = false") tự động áp dụng cho tất cả query.
 * User đã bị xóa mềm sẽ không xuất hiện trong kết quả — bao gồm cả
 * findByUsername() dùng bởi Spring Security → tài khoản xóa mềm không đăng nhập được.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm user theo username — dùng bởi UserDetailsServiceImpl.
     * Index idx_user_username đảm bảo query O(log n).
     */
    Optional<User> findByUsername(String username);

    /** Validate username unique khi tạo tài khoản mới. */
    boolean existsByUsername(String username);

    /** Validate email unique khi tạo tài khoản mới. */
    boolean existsByEmail(String email);
}
