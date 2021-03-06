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
package org.redisson.pubsub;

import org.redisson.RedissonCountDownLatch;
import org.redisson.RedissonCountDownLatchEntry;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.util.concurrent.Promise;

public class CountDownLatchPubSub extends PublishSubscribe<RedissonCountDownLatchEntry> {

    @Override
    protected RedissonCountDownLatchEntry createEntry(Promise<RedissonCountDownLatchEntry> newPromise) {
        return new RedissonCountDownLatchEntry(newPromise);
    }

    @Override
    protected RedisPubSubListener<Long> createListener(final String channelName, final RedissonCountDownLatchEntry value) {
        RedisPubSubListener<Long> listener = new BaseRedisPubSubListener<Long>() {

            @Override
            public void onMessage(String channel, Long message) {
                if (!channelName.equals(channel)) {
                    return;
                }

                if (message.equals(RedissonCountDownLatch.zeroCountMessage)) {
                    value.getLatch().open();
                }
                if (message.equals(RedissonCountDownLatch.newCountMessage)) {
                    value.getLatch().close();
                }
            }

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (!channelName.equals(channel)) {
                    return false;
                }

                if (type == PubSubType.SUBSCRIBE) {
                    value.getPromise().trySuccess(value);
                    return true;
                }
                return false;
            }

        };
        return listener;
    }


}
