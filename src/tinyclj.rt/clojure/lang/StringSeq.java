/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Dec 6, 2007 */

package clojure.lang;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class StringSeq extends ASeq implements IndexedSeq,IDrop,IReduceInit{

private static final long serialVersionUID = 7975525539139301753L;

public final CharSequence s;
public final int i;

static public StringSeq create(CharSequence s){
	if(s.length() == 0)
		return null;
	return new StringSeq(null, s, 0);
}

StringSeq(IPersistentMap meta, CharSequence s, int i){
	super(meta);
	this.s = s;
	this.i = i;
}

public Obj withMeta(IPersistentMap meta){
	if(meta == meta())
		return this;
	return new StringSeq(meta, s, i);
}

public Object first(){
	return Character.valueOf(s.charAt(i));
}

public ISeq next(){
	if(i + 1 < s.length())
		return new StringSeq(_meta, s, i + 1);
	return null;
}

public int index(){
	return i;
}

public int count(){
	return s.length() - i;
}

public Sequential drop(int n) {
	int ii = i + n;
	if (ii < s.length()) {
		return new StringSeq(null, s, ii);
	} else {
		return null;
	}
}

public Object reduce(IFn f, Object start) {
    var mh = IFn.__arity(f, 2);
    try {
        Object acc = start;
        for(int ii=i; ii < s.length(); ii++) {
            acc = mh.invoke(f, acc, s.charAt(ii));
            if(RT.isReduced(acc))
                return ((IDeref)acc).deref();
        }
        return acc;
    } catch (Throwable t) {
        throw clojure.lang.Util.sneakyThrow(t);
    }
}

}
