// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.lang;

import java.io.Serializable;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements the special common case of a finite range based on int start, end, and step.
 */
public class IntRange extends ASeq implements Counted, IChunkedSeq, IReduce {

private static final int CHUNK_SIZE = 32;

// Invariants guarantee this is never an empty or infinite seq
//   assert(start != end && step != 0)
final int start;
final int end;
final int step;
final BoundsCheck boundsCheck;
private volatile IntChunk _chunk;  // lazy
private volatile ISeq _chunkNext;        // lazy
private volatile ISeq _next;             // cached

private static interface BoundsCheck extends Serializable {
    boolean exceededBounds(long val);
}

private static BoundsCheck positiveStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val >= end);
        }
    };
}

private static BoundsCheck negativeStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val <= end);
        }
    };
}

private IntRange(int start, int end, int step, BoundsCheck boundsCheck){
    this.start = start;
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
}

private IntRange(int start, int end, int step, BoundsCheck boundsCheck, IntChunk chunk, ISeq chunkNext){
    this.start = start;
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
    this._chunk = chunk;
    this._chunkNext = chunkNext;
}

private IntRange(IPersistentMap meta, int start, int end, int step, BoundsCheck boundsCheck, IntChunk chunk, ISeq chunkNext){
    super(meta);
    this.start = start;
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
    this._chunk = chunk;
    this._chunkNext = chunkNext;
}

public static ISeq create(int end) {
    if(end > 0)
        return new IntRange(0, end, 1, positiveStep(end));
    return PersistentList.EMPTY;
}

public static ISeq create(int start, int end) {
    if(start >= end)
        return PersistentList.EMPTY;
    return new IntRange(start, end, 1, positiveStep(end));
}

public static ISeq create(final int start, int end, int step) {
    if(step > 0) {
        if(end <= start) return PersistentList.EMPTY;
        return new IntRange(start, end, step, positiveStep(end));
    } else if(step < 0) {
        if(end >= start) return PersistentList.EMPTY;
        return new IntRange(start, end, step, negativeStep(end));
    } else {
        if(end == start) return PersistentList.EMPTY;
        return Repeat.create(start);
    }
}

public Obj withMeta(IPersistentMap meta){
    if(meta == _meta)
        return this;
    return new IntRange(meta, start, end, step, boundsCheck, _chunk, _chunkNext);
}

public Object first() {
    return (int)start;
}

public void forceChunk() {
    if(_chunk != null) return;

    long count;
    try {
        count = rangeCount(start, end, step);
    } catch(ArithmeticException e) {
        // size of total range is > Long.MAX_VALUE so must step to count
        // this only happens in pathological range cases like:
        // (range -9223372036854775808 9223372036854775807 9223372036854775807)
        count = steppingCount(start, end, step);
    }

    if (count > CHUNK_SIZE) { // not last chunk
        int nextStart = start + (step * CHUNK_SIZE);   // cannot overflow, must be < end
        _chunkNext = new IntRange(nextStart, end, step, boundsCheck);
        _chunk = new IntChunk(start, step, CHUNK_SIZE);
    } else {  // last chunk
        _chunk = new IntChunk(start, step, (int) count);   // count must be <= CHUNK_SIZE
    }
}

public ISeq next() {
    if(_next != null)
        return _next;

    forceChunk();
    if(_chunk.count() > 1) {
        IntChunk smallerChunk = _chunk.dropFirst();
        _next = new IntRange(smallerChunk.first(), end, step, boundsCheck, smallerChunk, _chunkNext);
        return _next;
    }
    return chunkedNext();
}

public IChunk chunkedFirst() {
    forceChunk();
    return _chunk;
}

public ISeq chunkedNext() {
    return chunkedMore().seq();
}

public ISeq chunkedMore() {
    forceChunk();
    if(_chunkNext == null)
        return PersistentList.EMPTY;
    return _chunkNext;
}

// fallback count mechanism for pathological cases
// returns either exact count or CHUNK_SIZE+1
long steppingCount(long start, long end, long step) {
    long count = 1;
    long s = start;
    while(count <= CHUNK_SIZE) {
        try {
            s = Numbers.add(s, step);
            if(boundsCheck.exceededBounds(s))
                break;
            else
                count++;
        } catch(ArithmeticException e) {
            break;
        }
    }
    return count;
}

// returns exact size of remaining items OR throws ArithmeticException for overflow case
long rangeCount(long start, long end, long step) {
    // (1) count = ceiling ( (end - start) / step )
    // (2) ceiling(a/b) = (a+b+o)/b where o=-1 for positive stepping and +1 for negative stepping
    // thus: count = end - start + step + o / step
    return Numbers.add(Numbers.add(Numbers.minus(end, start), step), this.step > 0 ? -1 : 1) / step;
}

public int count() {
    try {
        long c = rangeCount(start, end, step);
        if(c > Integer.MAX_VALUE) {
            return Numbers.throwIntOverflow();
        } else {
            return (int) c;
        }
    } catch(ArithmeticException e) {
        // rare case from large range or step, fall back to iterating and counting
        Iterator iter = this.iterator();
        long count = 0;
        while(iter.hasNext()) {
            iter.next();
            count++;
        }

        if(count > Integer.MAX_VALUE)
            return Numbers.throwIntOverflow();
        else
            return (int)count;
    }
}

public Object reduce(IFn f) {
    var mh = IFn.__arity(f, 2);
    try {
        Object acc = start;
        int i = start + step;
        while(! boundsCheck.exceededBounds(i)) {
            acc = mh.invoke(f, acc, i);
            if (acc instanceof Reduced) return ((Reduced)acc).deref();
            i += step;
        }
        return acc;
    } catch (Throwable t) {
        throw clojure.lang.Util.sneakyThrow(t);
    }
}

public Object reduce(IFn f, Object val) {
    var mh = IFn.__arity(f, 2);
    try {
        Object acc = val;
        int i = start;
        do {
            acc = mh.invoke(f, acc, i);
            if (RT.isReduced(acc)) return ((Reduced)acc).deref();
            i += step;
        } while(! boundsCheck.exceededBounds(i));
        return acc;
    } catch (Throwable t) {
        throw clojure.lang.Util.sneakyThrow(t);
    }
}

public Iterator iterator() {
    return new IntRangeIterator();
}

class IntRangeIterator implements Iterator {
    private long next;
    private boolean hasNext;

    public IntRangeIterator() {
        this.next = start;
        this.hasNext = true;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public Object next() {
        if (hasNext) {
            long ret = next;
            try {
                next = Numbers.add(next, step);
                hasNext = ! boundsCheck.exceededBounds(next);
            } catch(ArithmeticException e) {
                hasNext = false;
            }
            return (int)ret;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}

private static class IntChunk implements IChunk, Serializable {
    final int start;
    final int step;
    final int count;

    public IntChunk(int start, int step, int count) {
        this.start = start;
        this.step = step;
        this.count = count;
    }

    public int first(){
        return start;
    }

    public Object nth(int i){
        return start + (i * step);
    }

    public Object nth(int i, Object notFound){
        if(i >= 0 && i < count)
            return start + (i * step);
        return notFound;
    }

    public int count(){
        return count;
    }

    public IntChunk dropFirst(){
        if(count <= 1)
            throw new IllegalStateException("dropFirst of empty chunk");
        return new IntChunk(start+step, step, count-1);
    }

    public Object reduce(IFn f, Object init) {
        var mh = IFn.__arity(f, 2);
        try {
            int x = start;
            Object ret = init;
            for(int i=0; i<count; i++) {
                ret = mh.invoke(f, ret, x);
                if(RT.isReduced(ret))
                    return ret;
                x += step;
            }
            return ret;
        } catch (Throwable t) {
            throw clojure.lang.Util.sneakyThrow(t);
        }
    }

}
}
