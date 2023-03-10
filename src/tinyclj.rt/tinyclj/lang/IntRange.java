/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package tinyclj.lang;

import java.io.Serializable;

import java.util.Iterator;
import java.util.NoSuchElementException;

import clojure.lang.*;

/**
 * Implements the special common case of a finite range based on int start, end, and step,
 * with no more than Integer.MAX_VALUE items.
 */
public class IntRange extends ASeq implements Counted, IChunkedSeq, IReduce, IDrop {

private static final long serialVersionUID = -1467242400566893909L;

// Invariants guarantee this is never an empty or infinite seq
//   assert(start != end && step != 0)
final int start;
final int end;
final int step;
final int count;

private IntRange(int start, int end, int step, int count){
    this.start = start;
    this.end = end;
    this.step = step;
    this.count = count;
}

private IntRange(IPersistentMap meta, int start, int end, int step, int count){
    super(meta);
    this.start = start;
    this.end = end;
    this.step = step;
    this.count = count;
}

// returns exact size of remaining items OR throws ArithmeticException for overflow case
static long rangeCount(long start, long end, long step) {
    // (1) count = ceiling ( (end - start) / step )
    // (2) ceiling(a/b) = (a+b+o)/b where o=-1 for positive stepping and +1 for negative stepping
    // thus: count = end - start + step + o / step
    return Numbers.add(Numbers.add(Numbers.minus(end, start), step), step > 0 ? -1 : 1) / step;
}

public static ISeq create(int end) {
    if(end > 0) {
        try {
            return new IntRange(0, end, 1, Math.toIntExact(rangeCount(0L, end, 1L)));
        } catch(ArithmeticException e) {
            throw clojure.lang.Util.sneakyThrow(e); // FIXME... return wrapped Range
            //return Range.create(end);  // count > Integer.MAX_VALUE
        }
    } else {
        return PersistentList.EMPTY;
    }
}

public static ISeq create(int start, int end) {
    if(start >= end) {
        return PersistentList.EMPTY;
    } else {
        try {
            return new IntRange(start, end, 1, Math.toIntExact(rangeCount(start, end, 1L)));
        } catch(ArithmeticException e) {
            throw clojure.lang.Util.sneakyThrow(e); // FIXME... return wrapped Range
            //return Range.create(start, end);
        }
    }
}

public static ISeq create(final int start, int end, int step) {
    if(step > 0) {
        if(end <= start) return PersistentList.EMPTY;
        try {
            return new IntRange(start, end, step, Math.toIntExact(rangeCount(start, end, step)));
        } catch(ArithmeticException e) {
            throw clojure.lang.Util.sneakyThrow(e); // FIXME... return wrapped Range
            //return Range.create(start, end, step);
        }
    } else if(step < 0) {
        if(end >= start) return PersistentList.EMPTY;
        try {
            return new IntRange(start, end, step, Math.toIntExact(rangeCount(start, end, step)));
        } catch(ArithmeticException e) {
            throw clojure.lang.Util.sneakyThrow(e); // FIXME... return wrapped Range
            //return Range.create(start, end, step);
        }
    } else {
        if(end == start) return PersistentList.EMPTY;
        return Repeat.create(start);
    }
}

public Obj withMeta(IPersistentMap meta){
    if(meta == this.meta())
        return this;
    return new IntRange(meta, start, end, step, count);
}

public Object first() {
    return start;
}

public ISeq next() {
    if(count > 1) {
        return new IntRange(start + step, end, step, count-1);
    } else {
        return null;
    }
}

private static final int CHUNK_SIZE = 32;

public IChunk chunkedFirst() {
    return new IntChunk(start, step, Math.min(count, CHUNK_SIZE));
}

public ISeq chunkedNext() {
    return chunkedMore().seq();
}

public ISeq chunkedMore() {
    if(count <= CHUNK_SIZE) {
        return PersistentList.EMPTY;
    } else {
        return IntRange.create(start + (step * CHUNK_SIZE), end, step);
    }

}

public Sequential drop(int n) {
    if(n <= 0) {
        return this;
    } else if(n < count) {
        return new IntRange(start+(step*n), end, step, count - n);
    } else {
        return null;
    }
}

public int count() {
    return count;
}

public Object reduce(IFn f) {
    Object acc = start;
    int i = start + step;
    int n = count;
    var mh = IFn.__arity(f, 2);
    try {
        while(n > 1) {
            acc = mh.invoke(f, acc, i);
            if (acc instanceof Reduced) return ((Reduced)acc).deref();
            i += step;
            n--;
        }
        return acc;
    } catch (Throwable t) {
        throw clojure.lang.Util.sneakyThrow(t);
    }
}

public Object reduce(IFn f, Object val) {
    Object acc = val;
    int n = count;
    int i = start;
    var mh = IFn.__arity(f, 2);
    try {
        do {
            acc = mh.invoke(f, acc, i);
            if (clojure.lang.RT.isReduced(acc)) return ((Reduced)acc).deref();
            i += step;
            n--;
        } while(n > 0);
        return acc;
    } catch (Throwable t) {
        throw clojure.lang.Util.sneakyThrow(t);
    }
}

public Iterator iterator() {
    return new IntRangeIterator();
}

class IntRangeIterator implements Iterator {
    private int next;
    private int remaining;

    public IntRangeIterator() {
        this.next = start;
        this.remaining = count;
    }

    public boolean hasNext() {
        return remaining > 0;
    }

    public Object next() {
        if (remaining > 0) {
            int ret = next;
            next = next + step;
            remaining = remaining - 1;
            return ret;
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
        int x = start;
        Object ret = init;
        var mh = IFn.__arity(f, 2);
        try {
            for(int i=0; i<count; i++) {
                ret = mh.invoke(f, ret, x);
                if(clojure.lang.RT.isReduced(ret))
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
