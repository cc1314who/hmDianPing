package com.hmdp.service.impl;

//import cn.hutool.core.bean.BeanUtil;
//import cn.hutool.core.collection.CollectionUtil;
//import cn.hutool.core.lang.UUID;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.hmdp.dto.Result;
//import com.hmdp.entity.VoucherOrder;
//import com.hmdp.mapper.VoucherOrderMapper;
//import com.hmdp.service.ISeckillVoucherService;
//import com.hmdp.service.IVoucherOrderService;
//import com.hmdp.utils.RedisIdWorker;
//import com.hmdp.utils.UserHolder;
//import org.redisson.api.RedissonClient;
//import org.springframework.aop.framework.AopContext;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.data.redis.connection.stream.*;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.Resource;
//import java.time.Duration;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * <p>
// * 服务实现类
// * </p>
// *
// * @author 虎哥
// * @since 2021-12-22
// */
//@Service
//public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
//
//    @Autowired
//    private ISeckillVoucherService seckillVoucherService;
//    @Autowired
//    private RedisIdWorker redisIdWorker;
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//    @Resource
//    private RedissonClient redissonClient;
//
//    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
//
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//
//    private IVoucherOrderService proxy = null;
//
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
//
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 获取用户
//        Long userId = UserHolder.getUser().getId();
//        long orderId = redisIdWorker.nextId("order");
//        // 1.执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString(),
//                String.valueOf(orderId)
//        );
//        int value = result.intValue();
//        // 2.判断结果是否为0
//        if (value != 0) {
//            // 2.1 不为0，代表没有购买资格
//            return Result.fail(value == 1 ? "库存不足" : "不能重复下单");
//        }
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 2.2 订单id
//        voucherOrder.setId(orderId);
//        // 2.3 用户id
//        voucherOrder.setUserId(userId);
//        // 2.4 代金券id
//        voucherOrder.setVoucherId(voucherId);
//        // 2.5 放入阻塞队列
//        orderTasks.add(voucherOrder);
//        // 3 获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        // 4.返回订单id
//        return Result.ok(orderId);
//    }
//
//    @Override
//    @Transactional
//    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        // 5.1 查询订单
//        Integer count = query().eq("user_id", userId)
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .count();
//        if (count > 0) {
//            // 用户已经购买过了
//            log.error("用户已经购买过了");
//            return;
//        }
//
//        // 6.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .update();
//        if (!success) {
//            // 扣减失败
//            log.error("库存不足");
//            return;
//        }
//        save(voucherOrder);
//    }
//
//    // 异步处理线程池
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//
//    // 在类初始化之后执行
//    @PostConstruct
//    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//
//    /**
//     * 消息队列，尝试获取stream.orders中消息
//     */
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    /**
//                     * 创建了一个消费者组"g1"，
//                     * 使用"c1"作为消费者名称，
//                     * 并从上次消费的位置开始读取来自"stream.orders" Stream的最后1条消息。
//                     */
//                    // 1.获取消息队列中的订单消息
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
//                    );
//                    // 2.判断订单消息是否为空
//                    if (CollectionUtil.isEmpty(list)) {
//                        // 如果为空，说明灭有消息， 继续下一次循环 等待
//                        continue;
//                    }
//                    // 解析数据
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> value = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    // 3.创建订单
//                    createVoucherOrder(voucherOrder);
//                    // 4.确认信息
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                    // 处理异常信息
//                    handlePendingList();
//                }
//            }
//        }
//
//        private void handlePendingList() {
//            while (true) {
//                try {
//                    /**
//                     * 创建了一个消费者组"g1"，
//                     * 使用"c1"作为消费者名称，
//                     * 并从上次消费的位置开始读取来自"stream.orders" Stream的最后1条消息。
//                     */
//                    // 1.获取消息队列中的订单消息
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
//                    );
//                    // 2.判断订单消息是否为空
//                    if (CollectionUtil.isEmpty(list)) {
//                        // 如果为空，说明没有消息， 继续下一次循环 等待
//                        continue;
//                    }
//                    // 解析数据
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> value = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    // 3.创建订单
//                    createVoucherOrder(voucherOrder);
//                    // 4.确认信息
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理pending订单异常", e);
//                    try {
//                        Thread.sleep(20);
//                    } catch (Exception e1) {
//                        e1.printStackTrace();
//                    }
//                }
//            }
//        }
//    }
//
//
//
//
//    /*// 用于线程池处理的任务
//    // 当初始化完毕后，就会去从队列中去拿信息
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//
//        *//**
//     * @param voucherOrder
//     * @return
//     * @title 创建订单
//     *//*
//        private void handleVoucherOrder(VoucherOrder voucherOrder) {
//            // 1.获取用户
//            Long userId = voucherOrder.getUserId();
//            // 2.创建锁对象
//            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//            // 3.尝试获取锁
//            boolean isLock = redisLock.tryLock();
//            // 4.判断所是否获取成功
//            if (!isLock) {
//                // 获取锁失败
//                log.error("不允许重复下单");
//                return;
//            }
//            try {
//                // 注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
//                proxy.createVoucherOrder(voucherOrder);
//            } finally {
//                // 释放锁
//                redisLock.unlock();
//            }
//        }
//    }*/
//
//
//    /*@Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //synchronized (userId.toString().intern()){
//        //    // 获取代理对象 (事物)
//        //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //   return proxy.createVoucherOder(voucherId);
//        //}
//        // 创建锁对象
//        //SimpleRedisLock lock
//        // new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//         // 分布式锁
//
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        // 加锁失败
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            // 获取代理对象 (事物)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }
//    @Transactional
//    public Result createVoucherOder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//        Integer count = query().eq("user_id", userId)
//                .eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            return Result.fail("用户已经购买一次了");
//        }
//        // 5.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock -1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0).update();
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//        // 6.创建订单
//        // 订单ID
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 订单ID
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 用户ID
//        voucherOrder.setUserId(userId);
//        // 代金券ID
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        return Result.ok(orderId);
//    }*/
//
//
//    /*@Transactional
//    public synchronized Result createVoucherOder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//        // 加悲观锁 userId.toString().intern()加入常量中要不然锁不住
//        synchronized (userId.toString().intern()) {
//            Integer count = query().eq("user_id", userId)
//                    .eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("用户已经购买一次了");
//            }
//            // 5.扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock -1")
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0).update();
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//            // 6.创建订单
//            // 订单ID
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 订单ID
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            // 用户ID
//            voucherOrder.setUserId(userId);
//            // 代金券ID
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//            return Result.ok(orderId);
//        }
//
//    }*/
//
//}


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.返回订单id
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        // 2.5.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入阻塞队列
        orderTasks.add(voucherOrder);

        // 3.返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return createVoucherOrder(voucherId);
    }



    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/
    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock(1200);
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/

    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }*/
}

