package com.hmdp;

import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.support.ExecutorServiceAdapter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@SpringBootTest
class HmDianPingApplicationTests {


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private IShopService shopService;

    @Autowired
    private IUserService userService;
    @Test
    void testSaveUser(){
        User user = userService.createUser("17671052093");
        System.err.println(user.toString());
        //userMapper.saveUser("17671052093");
    }

    @Test
    void testSaveShop(){
        shopService.saveShop2Redis(1L,10L);
    }

}
