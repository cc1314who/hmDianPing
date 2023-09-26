package com.hmdp.service;

public interface ILock {

    /**
     * @title 尝试获取锁
     * @param timeoutSec 锁持有的时间,过期后自动释放
     * @return true代表获取锁成功,false代表获取锁失败
     */
    boolean tryLock(Long timeoutSec);

    /**
     * @title 释放锁
     * @param
     * @return
     */
    void unlock();

}
