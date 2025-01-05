package com.crossoverjie.distributed.lock;

import com.crossoverjie.distributed.constant.RedisToolsConstant;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.*;

public class RealRedisLockTest {

    private static Logger logger = LoggerFactory.getLogger(RealRedisLockTest.class);
    private static ExecutorService executorServicePool;


    private static RedisLock redisLock;

    private static JedisPool jedisPool;


    public static void main(String[] args) throws InterruptedException {
        RealRedisLockTest redisLockTest = new RealRedisLockTest();
        redisLockTest.init();
        initThread();

        for (int i = 0; i < 50; i++) {
            executorServicePool.execute(new Worker(i));
        }

        executorServicePool.shutdown();
        while (!executorServicePool.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.info("worker running");
        }
        logger.info("worker over");

    }

    @Before
    public void setBefore() {
        init();

    }

    private void init() {

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(60);
        config.setMaxTotal(60);
        config.setMaxWaitMillis(10000);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);

        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.addClusterNode(new RedisNode("10.19.13.51", 7000));

        //单机
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(config);

        //集群
        //JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration) ;
        jedisConnectionFactory.setHostName("47.98.194.60");
        jedisConnectionFactory.setPort(6379);
        jedisConnectionFactory.setPassword("");
        jedisConnectionFactory.setTimeout(100000);
        jedisConnectionFactory.afterPropertiesSet();
        //jedisConnectionFactory.setShardInfo(new JedisShardInfo("47.98.194.60", 6379));
        //JedisCluster jedisCluster = new JedisCluster(hostAndPort);

        HostAndPort hostAndPort = new HostAndPort("10.19.13.51", 7000);
        JedisCluster jedisCluster = new JedisCluster(hostAndPort);
        redisLock = new RedisLock.Builder(jedisConnectionFactory, RedisToolsConstant.SINGLE)
                .lockPrefix("lock_")
                .sleepTime(100)
                .build();

    }

    public static void initThread() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("current-thread-%d").build();
        executorServicePool = new ThreadPoolExecutor(350, 350, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(200), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());

    }


    private static class Worker implements Runnable {

        private int index;

        public Worker(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            //测试非阻塞锁
            //boolean limit = redisLock.tryLock("abc", "12345");
            //if (limit) {
            //    logger.info("加锁成功=========");
            //    boolean unlock = redisLock.unlock("abc", "12345");
            //    logger.info("解锁结果===[{}]",unlock);
            //} else {
            //    logger.info("加锁失败");
            //
            //}

            //测试非阻塞锁 + 超时时间
            //boolean limit = redisLock.tryLock("abc", "12345",1000);
            //if (limit) {
            //    logger.info("加锁成功=========");
            //    boolean unlock = redisLock.unlock("abc", "12345");
            //    logger.info("解锁结果===[{}]",unlock);
            //} else {
            //    logger.info("加锁失败");
            //
            //}



            //测试阻塞锁
            try {
                redisLock.lock("abc", "12345");
                logger.info("加锁成功=========");
                boolean unlock = redisLock.unlock("abc","12345") ;
                logger.info("解锁结果===[{}]",unlock);
            } catch (InterruptedException e) {
                logger.error("Exception occurred when lock or unlock", e);
            }



            //测试阻塞锁 + 阻塞时间
            //try {
            //    boolean limit = redisLock.lock("abc", "12345", 100);
            //    if (limit) {
            //        logger.info("加锁成功=========");
            //        boolean unlock = redisLock.unlock("abc", "12345");
            //        logger.info("解锁结果===[{}]",unlock);
            //    } else {
            //        logger.info("加锁失败");
            //
            //    }
            //} catch (InterruptedException e) {
            //    e.printStackTrace();
            //}
        }
    }


}