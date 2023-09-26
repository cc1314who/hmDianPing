package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int value = result.intValue();
        // 2.判断结果是否为0
        if (value != 0) {
            // 2.1 不为0，代表没有购买资格
            return Result.fail(value == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.返回订单id
        return Result.ok(orderId);
    }

    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 在类初始化之后执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从队列中去拿信息
    private class VoucherOrderHandler implements Runnable {

        private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

        @Override
        public void run() {
            while (true) {

                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }
        }

        /**
         * @param voucherOrder
         * @return
         * @title 创建订单
         */
        private void handleVoucherOrder(VoucherOrder voucherOrder) {

        }
    }




/*    @Override
    public Result createVoucherOder(Long voucherId) {
        return null;
    }*/




    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //synchronized (userId.toString().intern()){
        //    // 获取代理对象 (事物)
        //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //   return proxy.createVoucherOder(voucherId);
        //}
        // 创建锁对象
        //SimpleRedisLock lock
        // new SimpleRedisLock("order:" + userId, stringRedisTemplate);

         // 分布式锁

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 加锁失败
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取代理对象 (事物)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOder(voucherId);
        } finally {
            lock.unlock();
        }
    }
    @Transactional
    public Result createVoucherOder(Long voucherId) {
        // 一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买一次了");
        }
        // 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        // 6.创建订单
        // 订单ID
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户ID
        voucherOrder.setUserId(userId);
        // 代金券ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }*/


    /*@Transactional
    public synchronized Result createVoucherOder(Long voucherId) {
        // 一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        // 加悲观锁 userId.toString().intern()加入常量中要不然锁不住
        synchronized (userId.toString().intern()) {
            Integer count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("用户已经购买一次了");
            }
            // 5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0).update();
            if (!success) {
                return Result.fail("库存不足");
            }
            // 6.创建订单
            // 订单ID
            VoucherOrder voucherOrder = new VoucherOrder();
            // 订单ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 用户ID
            voucherOrder.setUserId(userId);
            // 代金券ID
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);
        }

    }*/

}
