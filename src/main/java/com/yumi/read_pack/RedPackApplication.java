package com.yumi.read_pack;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@MapperScan("com.yumi.read_pack.db.mapper")
@EnableCaching
public class RedPackApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedPackApplication.class, args);
	}

}
