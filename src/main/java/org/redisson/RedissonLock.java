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
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.core.RLock;
import org.redisson.pubsub.LockPubSub;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonLock extends RedissonExpirable implements RLock {

    public static final long LOCK_EXPIRATION_INTERVAL_SECONDS = 30;
    private static final ConcurrentMap<String, Timeout> refreshTaskMap = PlatformDependent.newConcurrentHashMap();
    protected long internalLockLeaseTime = TimeUnit.SECONDS.toMillis(LOCK_EXPIRATION_INTERVAL_SECONDS);

    final UUID id;

    public static final Long unlockMessage = 0L;

    private static final LockPubSub PUBSUB = new LockPubSub();

    final CommandExecutor commandExecutor;

    protected RedissonLock(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.id = id;
    }

    private String getEntryName() {
        return id + ":" + getName();
    }

    String getChannelName() {
        return "redisson_lock__channel__{" + getName() + "}";
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        Long ttl;
        if (leaseTime != -1) {
            ttl = tryLockInner(leaseTime, unit);
        } else {
            ttl = tryLockInner();
        }
        // lock acquired
        if (ttl == null) {
            return;
        }

        Future<RedissonLockEntry> future = subscribe();
        future.syncUninterruptibly();

        try {
            while (true) {
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                // lock acquired
                if (ttl == null) {
                    break;
                }

                // waiting for message
                RedissonLockEntry entry = getEntry();
                if (ttl >= 0) {
                    entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().acquire();
                }
            }
        } finally {
            unsubscribe(future);
        }
    }

    @Override
    public boolean tryLock() {
        return tryLockInner() == null;
    }

    private Long tryLockInner() {
        Long ttlRemaining = tryLockInner(LOCK_EXPIRATION_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // lock acquired
        if (ttlRemaining == null) {
            newRefreshTask();
        }
        return ttlRemaining;
    }

    private void newRefreshTask() {
        if (refreshTaskMap.containsKey(getName())) {
            return;
        }

        Timeout task = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                expire(internalLockLeaseTime, TimeUnit.MILLISECONDS);
                refreshTaskMap.remove(getName());
                newRefreshTask(); // reschedule itself
            }
        }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);

        if (refreshTaskMap.putIfAbsent(getName(), task) != null) {
            task.cancel();
        }
    }

    /**
     * Stop refresh timer
     * @return true if timer was stopped successfully
     */
    void stopRefreshTask() {
        Timeout task = refreshTaskMap.remove(getName());
        if (task != null) {
            task.cancel();
        }
    }


    Long tryLockInner(final long leaseTime, final TimeUnit unit) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        return commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  redis.call('set', KEYS[1], cjson.encode({['o'] = ARGV[1], ['c'] = 1}), 'px', ARGV[2]); " +
                                "  return nil; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    o['c'] = o['c'] + 1; " +
                                "    redis.call('set', KEYS[1], cjson.encode(o), 'px', ARGV[2]); " +
                                "    return nil; " +
                                "  end;" +
                                "  return redis.call('pttl', KEYS[1]); " +
                                "end",
                        Collections.<Object>singletonList(getName()), id.toString() + "-" + Thread.currentThread().getId(), internalLockLeaseTime);
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        Long ttl;
        if (leaseTime != -1) {
            ttl = tryLockInner(leaseTime, unit);
        } else {
            ttl = tryLockInner();
        }
        // lock acquired
        if (ttl == null) {
            return true;
        }

        Future<RedissonLockEntry> future = subscribe();
        if (!future.awaitUninterruptibly(time, TimeUnit.MILLISECONDS)) {
            return false;
        }

        try {
            while (true) {
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                // lock acquired
                if (ttl == null) {
                    break;
                }

                if (time <= 0) {
                    return false;
                }

                // waiting for message
                long current = System.currentTimeMillis();
                RedissonLockEntry entry = getEntry();

                if (ttl >= 0 && ttl < time) {
                    entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);
                }

                long elapsed = System.currentTimeMillis() - current;
                time -= elapsed;
            }
            return true;
        } finally {
            unsubscribe(future);
        }
    }

    private RedissonLockEntry getEntry() {
        return PUBSUB.getEntry(getEntryName());
    }

    private Future<RedissonLockEntry> subscribe() {
        return PUBSUB.subscribe(getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    private void unsubscribe(Future<RedissonLockEntry> future) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(time, -1, unit);
    }

    @Override
    public void unlock() {
        Boolean opStatus = commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  redis.call('publish', KEYS[2], ARGV[2]); " +
                                "  return 1; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    o['c'] = o['c'] - 1; " +
                                "    if (o['c'] > 0) then " +
                                "      redis.call('set', KEYS[1], cjson.encode(o), 'px', ARGV[3]); " +
                                "      return 0;"+
                                "    else " +
                                "      redis.call('del', KEYS[1]);" +
                                "      redis.call('publish', KEYS[2], ARGV[2]); " +
                                "      return 1;"+
                                "    end" +
                                "  end;" +
                                "  return nil; " +
                                "end",
                        Arrays.<Object>asList(getName(), getChannelName()), id.toString() + "-" + Thread.currentThread().getId(), unlockMessage, internalLockLeaseTime);
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
        // TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void forceUnlock() {
        get(forceUnlockAsync());
    }

    Future<Boolean> forceUnlockAsync() {
        stopRefreshTask();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if (redis.call('del', KEYS[1]) == 1) then "
                + "redis.call('publish', KEYS[2], ARGV[1]); "
                + "return 1 "
                + "else "
                + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), unlockMessage);
    }

    @Override
    public boolean isLocked() {
        return isExists();
    }

    @Override
    public boolean isHeldByCurrentThread() {
        Boolean opStatus = commandExecutor.evalRead(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                            "local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  return 0; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    return 1; " +
                                "  else" +
                                "    return 0; " +
                                "  end;" +
                                "end",
                        Collections.<Object>singletonList(getName()), id.toString() + "-" + Thread.currentThread().getId());
        return opStatus;
    }

    @Override
    public int getHoldCount() {
        Long opStatus = commandExecutor.evalRead(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  return 0; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  return o['c']; " +
                                "end",
                        Collections.<Object>singletonList(getName()));
        return opStatus.intValue();
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return forceUnlockAsync();
    }

}
