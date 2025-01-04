package com.crossoverjie.distributed.lock;

import com.crossoverjie.distributed.constant.RedisToolsConstant;
import com.crossoverjie.distributed.util.ScriptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import java.util.Collections;

/**
 * Function: distributed lock
 *
 * @author crossoverJie
 * Date: 26/03/2018 11:09
 * @since JDK 1.8
 */
public class RedisLock {
    private static final Logger logger = LoggerFactory.getLogger(RedisLock.class);

    private static final String LOCK_MSG = "OK";

    private static final Long UNLOCK_MSG = 1L;

    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";


    private final String lockPrefix;

    private final int sleepTime;

    private final JedisConnectionFactory jedisConnectionFactory;
    private final int type;

    /**
     * time millisecond
     */
    private static final int TIME = 1000;

    /**
     * lua script
     */
    private String script;

    private RedisLock(Builder builder) {
        this.jedisConnectionFactory = builder.jedisConnectionFactory;
        this.type = builder.type;
        this.lockPrefix = builder.lockPrefix;
        this.sleepTime = builder.sleepTime;

        buildScript();
    }


    /**
     * get Redis connection
     *
     */
    private Object getConnection() {
        Object connection;
        if (type == RedisToolsConstant.SINGLE) {
            RedisConnection redisConnection = jedisConnectionFactory.getConnection();
            connection = redisConnection.getNativeConnection();
        } else {
            RedisClusterConnection clusterConnection = jedisConnectionFactory.getClusterConnection();
            connection = clusterConnection.getNativeConnection();
        }
        return connection;
    }

    /**
     * Non-blocking lock, default timeout :10s
     *
     * @param key     lock business type
     * @param request value
     * @return true lock success
     * false lock fail
     */
    public boolean tryLock(String key, String request) {
        return tryLock(key, request, 10 * TIME);
    }

    /**
     * blocking lock
     *
     * @param key
     * @param request
     */
    public void lock(String key, String request) throws InterruptedException {
        //get connection
        Object connection = getConnection();
        String result;
        for (; ; ) {
            result = getLockString(key, request, connection);

            if (LOCK_MSG.equals(result)) {
                break;
            }

            Thread.sleep(sleepTime);
        }

    }

    /**
     * blocking lock,custom time
     *
     * @param key
     * @param request
     * @param blockTime custom time
     * @throws InterruptedException
     */
    public boolean lock(String key, String request, int blockTime) throws InterruptedException {

        //get connection
        Object connection = getConnection();
        String result;
        while (blockTime >= 0) {
            result = getLockString(key, request, connection);
            if (LOCK_MSG.equals(result)) {
                return true;
            }
            blockTime -= sleepTime;

            Thread.sleep(sleepTime);
        }
        return false;
    }

    private String getLockString(String key, String request, Object connection) {
        String result;
        if (connection instanceof Jedis) {
            result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
            if (LOCK_MSG.equals(result)) {
                ((Jedis) connection).close();
            }
        } else {
            result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
        }
        return result;
    }


    /**
     * Non-blocking lock
     *
     * @param key        lock business type
     * @param request    value
     * @param expireTime custom expireTime
     * @return true lock success
     * false lock fail
     */
    public boolean tryLock(String key, String request, int expireTime) {
        //get connection
        Object connection = getConnection();
        String result;

        if (connection instanceof Jedis) {
            result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
            ((Jedis) connection).close();
        } else {
            result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
        }

        return LOCK_MSG.equals(result);
    }


    /**
     * unlock
     *
     * @param key
     * @param request request must be the same as lock request
     * @return
     */
    public boolean unlock(String key, String request) {

        //get connection
        Object connection = getConnection();
        //lua script

        Object result = null;
        if (connection instanceof Jedis) {
            result = ((Jedis) connection).eval(script, Collections.singletonList(lockPrefix + key), Collections.singletonList(request));
            ((Jedis) connection).close();
        } else if (connection instanceof JedisCluster) {
            result = ((JedisCluster) connection).eval(script, Collections.singletonList(lockPrefix + key), Collections.singletonList(request));

        } else {
            //throw new RuntimeException("instance is error") ;
            return false;
        }

        return UNLOCK_MSG.equals(result);
    }


    /**
     * read lua script
     */
    private void buildScript() {
        script = ScriptUtil.getScript("lock.lua");
    }


    public static class Builder {
        private static final String DEFAULT_LOCK_PREFIX = "lock_";
        /**
         * default sleep time
         */
        private static final int DEFAULT_SLEEP_TIME = 100;

        private JedisConnectionFactory jedisConnectionFactory = null;

        private final int type;

        private String lockPrefix = DEFAULT_LOCK_PREFIX;
        private int sleepTime = DEFAULT_SLEEP_TIME;

        public Builder(JedisConnectionFactory jedisConnectionFactory, int type) {
            this.jedisConnectionFactory = jedisConnectionFactory;
            this.type = type;
        }

        public Builder lockPrefix(String lockPrefix) {
            this.lockPrefix = lockPrefix;
            return this;
        }

        public Builder sleepTime(int sleepTime) {
            this.sleepTime = sleepTime;
            return this;
        }

        public RedisLock build() {
            return new RedisLock(this);
        }

    }
}
