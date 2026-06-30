package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository cho Product Entity.
 *
 * Kế thừa JpaRepository<Product, Long> để có sẵn các phương thức CRUD chuẩn:
 * save(), findById(), findAll(), delete()...
 *
 * QUAN TRỌNG - @SQLRestriction tự động áp dụng:
 * Mọi query tạo ra từ repository này (kể cả findById, findAll) đều tự động
 * được thêm điều kiện "AND is_deleted = false" do @SQLRestriction("is_deleted = false")
 * được khai báo trên Product entity.
 * → Sản phẩm đã bị xóa mềm sẽ KHÔNG bao giờ xuất hiện trong kết quả.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Kiểm tra mã sản phẩm đã tồn tại chưa (bao gồm cả chưa bị xóa mềm).
     * Dùng để validate trước khi tạo mới, tránh duplicate code.
     */
    boolean existsByCode(String code);

    /**
     * Tìm sản phẩm theo mã code với JOIN FETCH variants.
     *
     * WHY JOIN FETCH?
     * Nếu chỉ dùng findById(), khi code truy cập product.getVariants(),
     * Hibernate sẽ phải phát sinh thêm 1 query riêng (N+1 problem).
     * JOIN FETCH giải quyết bằng cách load product + variants trong 1 query duy nhất.
     *
     * JPQL thay vì Native SQL:
     * - Database-agnostic: hoạt động với MySQL, PostgreSQL, H2...
     * - Hibernate tự lo việc áp dụng @SQLRestriction vào đây.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id")
    Optional<Product> findByIdWithVariants(@Param("id") Long id);

    /**
     * Tìm sản phẩm theo mã code (dùng để kiểm tra trùng lặp khi update).
     */
    Optional<Product> findByCode(String code);
}
