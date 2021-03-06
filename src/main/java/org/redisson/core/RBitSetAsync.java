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
package org.redisson.core;

import java.util.BitSet;

import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetAsync extends RExpirableAsync {

    Future<byte[]> toByteArrayAsync();

    Future<Integer> lengthAsync();

    Future<Void> setAsync(int fromIndex, int toIndex, boolean value);

    Future<Void> clearAsync(int fromIndex, int toIndex);

    Future<Void> setAsync(BitSet bs);

    Future<Void> notAsync();

    Future<Void> setAsync(int fromIndex, int toIndex);

    Future<Integer> sizeAsync();

    Future<Boolean> getAsync(int bitIndex);

    Future<Void> setAsync(int bitIndex);

    Future<Void> setAsync(int bitIndex, boolean value);

    Future<Integer> cardinalityAsync();

    Future<Void> clearAsync(int bitIndex);

    Future<Void> clearAsync();

    Future<Void> orAsync(String... bitSetNames);

    Future<Void> andAsync(String... bitSetNames);

    Future<Void> xorAsync(String... bitSetNames);

}
