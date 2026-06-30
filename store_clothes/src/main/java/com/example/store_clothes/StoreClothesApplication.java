package com.example.store_clothes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point của ứng dụng KiotViet Fashion Store.
 *
 * @EnableJpaAuditing: Kích hoạt tính năng JPA Auditing toàn cục.
 *                     Bắt buộc phải có để @CreatedDate và @LastModifiedDate
 *                     trong BaseEntity hoạt động đúng.
 *                     Nếu thiếu annotation này, các field audit sẽ luôn null.
 */
@SpringBootApplication
@EnableJpaAuditing
public class StoreClothesApplication {

	public static void main(String[] args) {
		SpringApplication.run(StoreClothesApplication.class, args);
	}

}
