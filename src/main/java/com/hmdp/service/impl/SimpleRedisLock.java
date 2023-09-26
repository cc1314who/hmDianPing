package com.hmdp.service.impl;


import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;


import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.KEY_PREFIX;

/**
 * @ Author     ：maple.
 * @ Date       ：Created in 10:00 2023/9/25
 * @ Modified By：
 */
@Slf4j
public class SimpleRedisLock implements ILock {

    public SimpleRedisLock(String name ,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId,
                        timeoutSec, TimeUnit.SECONDS);
        log.info(threadId+"开始获取锁,获取"+success);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
/*        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断标示是否一致
        if(threadId.equals(id)) {
            // 释放锁
            log.info(threadId+"开始释放锁");
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }*/
        /**
         * 判断 Collections.singletonList(KEY_PREFIX + name) 和 ID_PREFIX + Thread.currentThread().getId()是否相等
         * 相等就删除 key为 KEY_PREFIX + name的锁
         */
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
