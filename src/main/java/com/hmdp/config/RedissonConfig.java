package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ Author     ：maple.
 * @ Date       ：Created in 14:13 2023/9/25
 * @ Modified By：
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonConfig1() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://172.16.233.20:6379")
                .setPassword("123321");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }


}
