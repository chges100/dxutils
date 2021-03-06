/*
 * Copyright (C) 2018 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxutils.hashtable;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores key-value tuples whereas keys are ints and values longs.
 * To be used if memory efficiency is important (and garbage collector should be relieved).
 *
 * @author Kevin Beineke, kevin.beineke@hhu.de, 26.02.2018
 */
public class IntLongHashTable {

    private static final int INITIAL_SIZE = 100;
    private static final float LOAD_FACTOR = 0.9f;

    private static final Logger LOGGER = LogManager.getFormatterLogger(IntLongHashTable.class.getSimpleName());

    private int[] m_table;
    private int m_elementCapacity;
    private int m_count;

    private ArrayList<long[]> m_list;

    /**
     * Creates an instance of LongIntHashTable.
     */
    public IntLongHashTable() {
        m_count = 0;
        m_elementCapacity = INITIAL_SIZE;

        m_table = new int[m_elementCapacity *
                3]; // keys (4 bytes) and values (8 bytes) are stored one after another -> triple size
        m_list = new ArrayList<>();
    }

    /**
     * Creates an instance of LongIntHashTable.
     *
     * @param p_initialSize
     *         the initial size
     */
    public IntLongHashTable(final int p_initialSize) {

        assert p_initialSize > 0;

        m_count = 0;
        m_elementCapacity = p_initialSize;

        m_table = new int[m_elementCapacity *
                3]; // keys (4 bytes) and values (8 bytes) are stored one after another -> triple size
        m_list = new ArrayList<>();
    }

    /**
     * Returns the size.
     *
     * @return the number of entries in the hash table
     */
    public int size() {
        return m_count;
    }

    /**
     * Returns the capacity.
     *
     * @return the capacity
     */
    public int capacity() {
        return m_elementCapacity;
    }

    /**
     * Returns whether this hash table is empty or not.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return m_count == 0;
    }

    /**
     * Returns whether this hash table is full or not.
     *
     * @return true if full, false otherwise
     */
    public boolean isFull() {
        return m_count >= (int) (m_elementCapacity * LOAD_FACTOR);
    }

    /**
     * Returns the underlying array.
     *
     * @return the int array.
     */
    public int[] getTable() {
        return m_table;
    }

    /**
     * Converts the hash table with all entries to an ArrayList with pairs.
     *
     * @return view on ArrayList with entries as pairs of index + value (int array)
     */
    public List<long[]> convert() {

        int count = 0;
        for (int i = 0; i < m_elementCapacity; i++) {
            long key = getKey(i);
            if (key != 0) {
                if (m_list.size() > count) {
                    long[] arr = m_list.get(count);
                    arr[0] = key;
                    arr[1] = getValue(i);
                } else {
                    long[] arr = new long[2];
                    arr[0] = key;
                    arr[1] = getValue(i);
                    m_list.add(arr);
                }
                count++;
            }
        }

        return m_list.subList(0, m_count);
    }

    /**
     * Returns the value to which the specified key is mapped in LongIntHashTable.
     *
     * @param p_key
     *         the searched key (must not be 0)
     * @return the value to which the key is mapped in LongIntHashTable
     */
    public final long get(final int p_key) {
        long ret = -1;
        int iter;
        int index;

        assert p_key != 0;

        index = (HashFunctionCollection.hash(p_key) & 0x7FFFFFFF) % m_elementCapacity;

        iter = getKey(index);
        while (iter != 0) {
            if (iter == p_key) {
                ret = getValue(index);
                break;
            }
            iter = getKey(++index);
        }

        return ret;
    }

    /**
     * Maps the given key to the given value in LongIntHashTable.
     *
     * @param p_key
     *         the key (must not be 0)
     * @param p_value
     *         the value
     */
    public final void put(final int p_key, final long p_value) {
        int iter;
        int index;

        assert p_key != 0;

        index = (HashFunctionCollection.hash(p_key) & 0x7FFFFFFF) % m_elementCapacity;

        iter = getKey(index);
        while (iter != 0) {
            if (iter == p_key) {
                set(index, p_key, p_value);
                return;
            }
            iter = getKey(++index);
        }

        set(index, p_key, p_value);
        if (++m_count >= m_elementCapacity * LOAD_FACTOR) {
            rehash();
        }
    }

    /**
     * Maps the given key to the given value in LongIntHashTable.
     * If the key already exists given value is added to old value.
     *
     * @param p_key
     *         the key (must not be 0)
     * @param p_value
     *         the value
     * @return the old value
     */
    public final long add(final int p_key, final long p_value) {
        long ret = -1;
        int iter;
        int index;

        assert p_key != 0;

        index = (HashFunctionCollection.hash(p_key) & 0x7FFFFFFF) % m_elementCapacity;

        iter = getKey(index);
        while (iter != 0) {
            if (iter == p_key) {
                ret = getValue(index);
                set(index, p_key, ret + p_value);
                break;
            }
            iter = getKey(++index);
        }
        if (ret == -1) {
            set(index, p_key, p_value);
            m_count++;
        }

        if (m_count >= m_elementCapacity * LOAD_FACTOR) {
            rehash();
        }

        return ret;
    }

    /**
     * Clears the LongIntHashTable.
     */
    public final void clear() {
        int length = m_table.length;

        /* The array and list is never truncated as the maximum number of concurrently accessed
         backup zones is rather low */

        // This is faster than Arrays.fill
        m_table[0] = 0;
        for (int i = 1; i < length; i += i) {
            System.arraycopy(m_table, 0, m_table, i, length - i < i ? length - i : i);
        }

        m_count = 0;
    }

    /**
     * Replaces the underlying array.
     * Allocates a new table, returns old table for processing.
     *
     * @return the old long array.
     */
    public final int[] replace() {
        int[] newTable = new int[m_table.length];
        int[] oldTable = m_table;
        m_table = newTable;
        m_count = 0;

        return oldTable;
    }

    /**
     * Sets the key-value tuple at given index.
     *
     * @param p_index
     *         the index
     * @param p_key
     *         the key
     * @param p_value
     *         the value
     */
    protected void set(final int p_index, final int p_key, final long p_value) {
        int index;

        index = p_index % m_elementCapacity * 3;
        m_table[index] = p_key;
        m_table[index + 1] = (int) (p_value >>> 32);
        m_table[index + 2] = (int) p_value;
    }

    /**
     * Gets the key at given index.
     *
     * @param p_index
     *         the index
     * @return the key
     */
    protected int getKey(final int p_index) {
        return m_table[p_index % m_elementCapacity * 3];
    }

    /**
     * Gets the value at given index.
     *
     * @param p_index
     *         the index
     * @return the value
     */
    protected long getValue(final int p_index) {
        return (long) m_table[p_index % m_elementCapacity * 3 + 1] << 32 |
                m_table[p_index % m_elementCapacity * 3 + 2] & 0xFFFFFFFFL;
    }

    /**
     * Increases the capacity of and internally reorganizes LongIntHashTable.
     */
    private void rehash() {
        int index = 0;
        int oldCount;
        int oldElementCapacity;
        int[] oldTable;
        int[] newTable;

        LOGGER.trace("Re-hashing (count:  %d)", m_count);

        oldCount = m_count;
        oldElementCapacity = m_elementCapacity;
        oldTable = m_table;

        m_elementCapacity = m_elementCapacity * 2 + 1;
        newTable = new int[m_elementCapacity * 3];
        m_table = newTable;

        m_count = 0;
        while (index < oldElementCapacity) {
            int key = oldTable[index * 3];
            if (key != 0) {
                add(key, (long) oldTable[index * 3 + 1] << 32 | oldTable[index * 3 + 2] & 0xFFFFFFFFL);
            }
            index = (index + 1) % m_elementCapacity;
        }
        m_count = oldCount;
    }

}
