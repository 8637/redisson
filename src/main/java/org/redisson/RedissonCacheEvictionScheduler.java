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
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * Eviction scheduler for RMapCache object.
 * Deletes expired entries in time interval between 5 seconds to 2 hours.
 * It analyzes deleted amount of expired keys
 * and 'tune' next execution delay depending on it.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheEvictionScheduler {

    public static class RedissonCacheTask implements Runnable {

        final String name;
        final String timeoutSetName;
        final CommandAsyncExecutor executor;
        final Deque<Integer> sizeHistory = new LinkedList<Integer>();
        int delay = 10;

        int minDelay = 5;
        int maxDelay = 2*60*60;
        int keysLimit = 500;

        public RedissonCacheTask(String name, String timeoutSetName, CommandAsyncExecutor executor) {
            this.name = name;
            this.timeoutSetName = timeoutSetName;
            this.executor = executor;
        }

        public void schedule() {
            executor.getConnectionManager().getGroup().schedule(this, delay, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            Future<Integer> future = executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                    "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
                  + "if #expiredKeys > 0 then "
                      + "redis.call('zrem', KEYS[2], unpack(expiredKeys)); "
                      + "redis.call('hdel', KEYS[1], unpack(expiredKeys)); "
                  + "end; "
                  + "return #expiredKeys;",
                  Arrays.<Object>asList(name, timeoutSetName), System.currentTimeMillis(), keysLimit);

            future.addListener(new FutureListener<Integer>() {
                @Override
                public void operationComplete(Future<Integer> future) throws Exception {
                    if (!future.isSuccess()) {
                        schedule();
                        return;
                    }

                    Integer size = future.getNow();

                    if (sizeHistory.size() == 2) {
                        if (sizeHistory.peekFirst() > sizeHistory.peekLast()
                                && sizeHistory.peekLast() > size) {
                            delay = Math.min(maxDelay, delay*2);
                        }

//                        if (sizeHistory.peekFirst() < sizeHistory.peekLast()
//                                && sizeHistory.peekLast() < size) {
//                            prevDelay = Math.max(minDelay, prevDelay/2);
//                        }

                        if (sizeHistory.peekFirst() == sizeHistory.peekLast()
                                && sizeHistory.peekLast() == size) {
                            if (size == keysLimit) {
                                delay = Math.max(minDelay, delay/2);
                            }
                            if (size == 0) {
                                delay = Math.min(maxDelay, delay*2);
                            }
                        }

                        sizeHistory.pollFirst();
                    }

                    sizeHistory.add(size);
                    schedule();
                }
            });
        }

    }

    private final ConcurrentMap<String, RedissonCacheTask> tasks = PlatformDependent.newConcurrentHashMap();

    public void schedule(String name, String timeoutSetName, CommandAsyncExecutor executor) {
        RedissonCacheTask task = new RedissonCacheTask(name, timeoutSetName, executor);
        RedissonCacheTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }

}
