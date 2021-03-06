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
package org.redisson.reactive;

import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RDequeReactive;
import org.redisson.api.RHyperLogLogReactive;
import org.redisson.api.RKeysReactive;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.api.RListReactive;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RQueueReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RScriptReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;

public class RedissonBatchReactive implements RBatchReactive {

    private final CommandBatchService executorService;

    public RedissonBatchReactive(ConnectionManager connectionManager) {
        this.executorService = new CommandBatchService(connectionManager);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return new RedissonBucketReactive<V>(executorService, name);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return new RedissonBucketReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLogReactive<V>(executorService, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLogReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return new RedissonListReactive<V>(executorService, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return new RedissonListReactive<V>(codec, executorService, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        return new RedissonMapReactive<K, V>(executorService, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        return new RedissonMapReactive<K, V>(codec, executorService, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        return new RedissonSetReactive<V>(executorService, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        return new RedissonSetReactive<V>(codec, executorService, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name) {
        return new RedissonTopicReactive<M>(executorService, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name, Codec codec) {
        return new RedissonTopicReactive<M>(codec, executorService, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return new RedissonQueueReactive<V>(executorService, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return new RedissonQueueReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueueReactive<V>(executorService, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueueReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RDequeReactive<V> getDequeReactive(String name) {
        return new RedissonDequeReactive<V>(executorService, name);
    }

    @Override
    public <V> RDequeReactive<V> getDequeReactive(String name, Codec codec) {
        return new RedissonDequeReactive<V>(codec, executorService, name);
    }

    @Override
    public RAtomicLongReactive getAtomicLongReactive(String name) {
        return new RedissonAtomicLongReactive(executorService, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSetReactive<V>(executorService, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSetReactive<V>(codec, executorService, name);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        return new RedissonLexSortedSetReactive(executorService, name);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return new RedissonBitSetReactive(executorService, name);
    }

    @Override
    public RScriptReactive getScript() {
        return new RedissonScriptReactive(executorService);
    }

    @Override
    public RKeysReactive getKeys() {
        return new RedissonKeysReactive(executorService);
    }

    @Override
    public Publisher<List<?>> executeReactive() {
        return new NettyFuturePublisher<List<?>>(executorService.executeAsync());
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCacheReactive<K, V>(codec, executorService, name);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        return new RedissonMapCacheReactive<K, V>(executorService, name);
    }

}
