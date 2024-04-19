package com.example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ThreadTest {
    static final int TEST_COUNT = 100_000;
    // 创建虚拟线程的基本手段
    static final Thread.Builder.OfVirtual threadBuilder = Thread.ofVirtual();
    // 线程池接口的创建虚拟线程方法,每次执行任务都会重新创建一个虚拟线程
    static final ExecutorService e2 = Executors.newVirtualThreadPerTaskExecutor();
    // 使用cached线程池缓存虚拟线程
    static final ExecutorService e3 = Executors.newCachedThreadPool(threadBuilder::unstarted);
    // 使用固定线程池缓存虚拟线程(大小配置成并发任务总数,避免在队列中等待而耗时)
    static final ExecutorService e4 = Executors.newFixedThreadPool(TEST_COUNT, threadBuilder::unstarted);

    static final class CountDownTask extends CountDownLatch implements Runnable {
        final long timeBegin = System.nanoTime();
        final String name;

        CountDownTask(String name) {
            super(TEST_COUNT); // 总任务数
            this.name = name;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1); // 会导致虚拟线程的切换
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            countDown(); // 完成一个任务
        }

        @Override
        public void await() throws InterruptedException {
            super.await(); // 等待所有任务完成
            System.out.println(name + ": " + (System.nanoTime() - timeBegin) / 1_000_000 + " ms"); // 输出耗时(毫秒)
        }
    }

    static final class SemaphoreTask extends Semaphore implements Runnable {
        final long timeBegin = System.nanoTime();
        final String name;

        SemaphoreTask(String name) {
            super(TEST_COUNT); // 信号量为1
            this.name = name;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1); // 会导致虚拟线程的切换
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            release(); // 完成一个任务

        }
        @Override
        public void acquire() throws InterruptedException {
            super.acquire(); // 等待所有任务完成
            System.out.println(name + ": " + (System.nanoTime() - timeBegin) / 1_000_000 + " ms"); // 输出耗时(毫秒)
        }
    }

    static void test1() throws Exception {
        var task = new CountDownTask("test1");
        for (int i = 0; i < TEST_COUNT; i++)
            threadBuilder.start(task);
        task.await();
    }

    static void test2() throws Exception {
        var task = new CountDownTask("test2");
        for (int i = 0; i < TEST_COUNT; i++)
            e2.execute(task);
        task.await();
    }

    static void test3() throws Exception {
        var task = new CountDownTask("test3");
        for (int i = 0; i < TEST_COUNT; i++)
            e3.execute(task);
        task.await();
    }

    static void test4() throws Exception {
        var task = new CountDownTask("test4");
        for (int i = 0; i < TEST_COUNT; i++)
            e4.execute(task);
        task.await();
    }

    static void test5() throws Exception {
        SemaphoreTask task = new SemaphoreTask("test5");
        for (int i = 0; i < TEST_COUNT; i++)
            e2.execute(task);
        task.acquire();
    }


    public static void main(String[] args) throws Exception {
        System.out.println(TEST_COUNT);
        for (int i = 0; i < 10; i++) { // 测试10轮,主要看后面几轮,确保预热和线程池复用效果
            System.gc(); // 每次测试前GC确保不受堆上已有太多垃圾导致触发GC影响性能
            test1();
//            System.gc();
//            test2();
            System.gc();
            test3();
            System.gc();
            test4();
            System.gc();
            // 如果固定 线程数 只要部分资源被占用，可以用Semaphore来控制
            test5();
            System.out.println("---");
        }
    }


}
