/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.core.RLock;

import io.netty.util.concurrent.Future;

/**
 * Lock will be removed automatically if client disconnects.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonWriteLock extends RedissonLock implements RLock {

    private final CommandExecutor commandExecutor;

    protected RedissonWriteLock(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name, id);
        this.commandExecutor = commandExecutor;
    }

    private String getLockName() {
        return id + ":" + Thread.currentThread().getId();
    }

    String getChannelName() {
        return "redisson_rwlock__{" + getName() + "}";
    }

    Long tryLockInner(final long leaseTime, final TimeUnit unit) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        return commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                            "local mode = redis.call('hget', KEYS[1], 'mode'); " +
                            "if (mode == false) then " +
                                  "redis.call('hset', KEYS[1], 'mode', 'write'); " +
                                  "redis.call('hset', KEYS[1], KEYS[2], 1); " +
                                  "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                                  "return nil; " +
                              "end; " +
                              "if (mode == 'write') then " +
                                  "if (redis.call('hexists', KEYS[1], KEYS[2]) == 1) then " +
                                      "redis.call('hincrby', KEYS[1], KEYS[2], 1); " +
                                      "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                                      "return nil; " +
                                  "end; " +
                                "end;" +
                                "return redis.call('pttl', KEYS[1]);",
                        Arrays.<Object>asList(getName(), getLockName()), internalLockLeaseTime);
    }

    @Override
    public void unlock() {
        Boolean opStatus = commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                                "local mode = redis.call('hget', KEYS[1], 'mode'); " +
                                "if (mode == false) then " +
                                    "redis.call('publish', KEYS[3], ARGV[1]); " +
                                    "return 1; " +
                                "end;" +
                                "if (mode == 'write') then " +
                                    "local lockExists = redis.call('hexists', KEYS[1], KEYS[2]); " +
                                    "if (lockExists == 0) then " +
                                        "return nil;" +
                                    "else " +
                                        "local counter = redis.call('hincrby', KEYS[1], KEYS[2], -1); " +
                                        "if (counter > 0) then " +
                                            "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                                            "return 0; " +
                                        "else " +
                                            "redis.call('hdel', KEYS[1], KEYS[2]); " +
                                            "if (redis.call('hlen', KEYS[1]) == 1) then " +
                                                "redis.call('del', KEYS[1]); " +
                                                "redis.call('publish', KEYS[3], ARGV[1]); " +
                                            "end; " +
                                            "return 1; "+
                                        "end; " +
                                    "end; " +
                                "end; "
                                + "return nil;",
                        Arrays.<Object>asList(getName(), getLockName(), getChannelName()), RedissonReadWriteLock.unlockMessage, internalLockLeaseTime);
        if (opStatus == null) {
            throw new IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread by node id: "
                    + id + " thread-id: " + Thread.currentThread().getId());
        }
        if (opStatus) {
            stopRefreshTask();
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    Future<Boolean> forceUnlockAsync() {
        stopRefreshTask();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
              "if (redis.call('hdel', KEYS[1], KEYS[2]) == 1) then " +
                  "if (redis.call('hlen', KEYS[1]) == 1) then " +
                      "redis.call('del', KEYS[1]); " +
                      "redis.call('publish', KEYS[3], ARGV[1]); " +
                  "end; " +
                  "return 1; " +
              "else " +
                  "return 0; " +
              "end;",
              Arrays.<Object>asList(getName(), getLockName(), getChannelName()), RedissonReadWriteLock.unlockMessage);
    }

    @Override
    public boolean isLocked() {
        String res = commandExecutor.read(getName(), StringCodec.INSTANCE, RedisCommands.HGET, getName(), "mode");
        return "write".equals(res);
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return commandExecutor.read(getName(), LongCodec.INSTANCE, RedisCommands.HEXISTS, getName(), getLockName());
    }

    @Override
    public int getHoldCount() {
        Long res = commandExecutor.read(getName(), LongCodec.INSTANCE, RedisCommands.HGET, getName(), getLockName());
        if (res == null) {
            return 0;
        }
        return res.intValue();
    }

}
