/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Mar 29, 2006 10:39:05 AM */

package clojure.lang;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;


public class Keyword extends tinyclj.lang.FnBridge implements IFn, Comparable, Named, Serializable, IHashEq {

private static final long serialVersionUID = -2105088845257724163L;

private static ConcurrentHashMap<Symbol, Reference<Keyword>> table = new ConcurrentHashMap();
static final ReferenceQueue rq = new ReferenceQueue();
public final Symbol sym;
final int hasheq;
transient String _str;

public static Keyword intern(Symbol sym){
	Keyword k = null;
	Reference<Keyword> existingRef = table.get(sym);
	if(existingRef == null)
		{
		Util.clearCache(rq, table);
		if(sym.meta() != null)
			sym = (Symbol) sym.withMeta(null);
		k = new Keyword(sym);
		existingRef = table.putIfAbsent(sym, new WeakReference<Keyword>(k, rq));
		}
	if(existingRef == null)
		return k;
	Keyword existingk = existingRef.get();
	if(existingk != null)
		return existingk;
	//entry died in the interim, do over
	table.remove(sym, existingRef);
	return intern(sym);
}

public static Keyword intern(String ns, String name){
	return intern(Symbol.intern(ns, name));
}

public static Keyword intern(String nsname){
	return intern(Symbol.intern(nsname));
}

private Keyword(Symbol sym){
	this.sym = sym;
	hasheq = sym.hasheq() + 0x9e3779b9;
}

public static Keyword find(Symbol sym){
    Reference<Keyword> ref = table.get(sym);
    if (ref != null)
        return ref.get();
    else
        return null;
}

public static Keyword find(String ns, String name){
    return find(Symbol.intern(ns, name));
}

public static Keyword find(String nsname){
    return find(Symbol.intern(nsname));
}

public final int hashCode(){
	return sym.hashCode() + 0x9e3779b9;
}

public int hasheq() {
	return hasheq;
}

public String toString(){
	if(_str == null)
		_str = (":" + sym);
	return _str;
}

/**
 * @Deprecated CLJ-2350: This function is no longer called, but has not been
 * removed to maintain the public interface.
 */
public Object throwArity(){
	throw new IllegalArgumentException("Wrong number of args passed to keyword: "
	                                   + toString());
}

Object throwArity(int n) {
	throw new ArityException(n, toString());
}

public Object call() {
	return throwArity(0);
}

public void run(){
	throw new UnsupportedOperationException();
}

public Object invoke() {
	return throwArity(0);
}

public int compareTo(Object o){
	return sym.compareTo(((Keyword) o).sym);
}


public String getNamespace(){
	return sym.getNamespace();
}

public String getName(){
	return sym.getName();
}

private Object readResolve() throws ObjectStreamException{
	return intern(sym);
}

/**
 * Indexer implements IFn for attr access
 *
 * @param obj - must be IPersistentMap
 * @return the value at the key or nil if not found
 */
final public Object invoke(Object obj) {
	if(obj instanceof ILookup)
		return ((ILookup)obj).valAt(this);
	return RT.get(obj, this);
}

final public Object invoke(Object obj, Object notFound) {
	if(obj instanceof ILookup)
		return ((ILookup)obj).valAt(this,notFound);
	return RT.get(obj, this, notFound);
}
}
