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

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSet extends RExpirable, RBitSetAsync {

    int length();

    void set(int fromIndex, int toIndex, boolean value);

    void clear(int fromIndex, int toIndex);

    void set(BitSet bs);

    void not();

    void set(int fromIndex, int toIndex);

    int size();

    boolean get(int bitIndex);

    void set(int bitIndex);

    void set(int bitIndex, boolean value);

    byte[] toByteArray();

    int cardinality();

    void clear(int bitIndex);

    void clear();

    BitSet asBitSet();

    void or(String... bitSetNames);

    void and(String... bitSetNames);

    void xor(String... bitSetNames);

}
