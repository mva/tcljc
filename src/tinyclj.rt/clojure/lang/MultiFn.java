/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Sep 13, 2007 */

package clojure.lang;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MultiFn implements IFn{
final public IFn dispatchFn;
final public Object defaultDispatchVal;
final public IRef hierarchy;
final String name;
final ReentrantReadWriteLock rw;
volatile IPersistentMap methodTable;
volatile IPersistentMap preferTable;
volatile IPersistentMap methodCache;
volatile Object cachedHierarchy;

// static final Var assoc = RT.var("clojure.core", "assoc");
// static final Var dissoc = RT.var("clojure.core", "dissoc");
private static IFn isa;
private static IFn isa() {
  if (isa == null) {
    isa = tinyclj.lang.RT.resolveRuntimeFn("tinyclj.core", "isa?");
  }
  return isa;
}                         
private static IFn parents;
private static IFn parents() {
  if (parents == null) {
    parents = tinyclj.lang.RT.resolveRuntimeFn("tinyclj.core", "parents");
  }
  return parents;
}                         

public MultiFn(String name, IFn dispatchFn, Object defaultDispatchVal, IRef hierarchy) {
	this.rw = new ReentrantReadWriteLock();
	this.name = name;
	this.dispatchFn = dispatchFn;
	this.defaultDispatchVal = defaultDispatchVal;
	this.methodTable = PersistentHashMap.EMPTY;
	this.methodCache = getMethodTable();
	this.preferTable = PersistentHashMap.EMPTY;
    this.hierarchy = hierarchy;
	cachedHierarchy = null;
}

public MultiFn reset(){
	rw.writeLock().lock();
	try{
		methodTable = methodCache = preferTable = PersistentHashMap.EMPTY;
		cachedHierarchy = null;
		return this;
	}
	finally {
		rw.writeLock().unlock();
	}
}

public MultiFn addMethod(Object dispatchVal, IFn method) {
	rw.writeLock().lock();
	try{
		methodTable = getMethodTable().assoc(dispatchVal, method);
		resetCache();
		return this;
	}
	finally {
		rw.writeLock().unlock();
	}
}

public MultiFn removeMethod(Object dispatchVal) {
	rw.writeLock().lock();
	try
		{
		methodTable = getMethodTable().without(dispatchVal);
		resetCache();
		return this;
		}
	finally
		{
		rw.writeLock().unlock();
		}
}

public MultiFn preferMethod(Object dispatchValX, Object dispatchValY) {
	rw.writeLock().lock();
	try
		{
		if(prefers(hierarchy.deref(), dispatchValY, dispatchValX))
			throw new IllegalStateException(
					String.format("Preference conflict in multimethod '%s': %s is already preferred to %s",
					              name, dispatchValY, dispatchValX));
		preferTable = getPreferTable().assoc(dispatchValX, RT.conj((IPersistentCollection) RT.get(getPreferTable(),
		                                                                                     dispatchValX,
		                                                                                     PersistentHashSet.EMPTY),
		                                                      dispatchValY));
		resetCache();
		return this;
		}
	finally
		{
		rw.writeLock().unlock();
		}
}

private boolean prefers(Object hierarchy, Object x, Object y) {
	IPersistentSet xprefs = (IPersistentSet) getPreferTable().valAt(x);
	if(xprefs != null && xprefs.contains(y))
		return true;
	for(ISeq ps = RT.seq(parents().invoke(hierarchy, y)); ps != null; ps = ps.next())
		{
		if(prefers(hierarchy, x, ps.first()))
			return true;
		}
	for(ISeq ps = RT.seq(parents().invoke(hierarchy, x)); ps != null; ps = ps.next())
		{
		if(prefers(hierarchy, ps.first(), y))
			return true;
		}
	return false;
}

private boolean isA(Object hierarchy, Object x, Object y) {
    return RT.booleanCast(isa().invoke(hierarchy, x, y));
}

private boolean dominates(Object hierarchy, Object x, Object y) {
	return prefers(hierarchy, x, y) || isA(hierarchy, x, y);
}

private IPersistentMap resetCache() {
	rw.writeLock().lock();
	try
		{
		methodCache = getMethodTable();
		cachedHierarchy = hierarchy.deref();
		return methodCache;
		}
	finally
		{
		rw.writeLock().unlock();
		}
}

 public IFn getMethod(Object dispatchVal) {
	if(cachedHierarchy != hierarchy.deref())
		resetCache();
	IFn targetFn = (IFn) methodCache.valAt(dispatchVal);
	if(targetFn != null)
		return targetFn;
	return findAndCacheBestMethod(dispatchVal);
}

private IFn getFn(Object dispatchVal) {
	IFn targetFn = getMethod(dispatchVal);
	if(targetFn == null)
		throw new IllegalArgumentException(String.format("No method in multimethod '%s' for dispatch value: %s",
		                                                 name, dispatchVal));
	return targetFn;
}

private IFn findAndCacheBestMethod(Object dispatchVal) {
	rw.readLock().lock();
	Object bestValue;
	IPersistentMap mt = methodTable;
	IPersistentMap pt = preferTable;
	Object ch = cachedHierarchy;
	try
		{
		Map.Entry bestEntry = null;
		for(Object o : getMethodTable())
			{
			Map.Entry e = (Map.Entry) o;
			if(isA(ch, dispatchVal, e.getKey()))
				{
				if(bestEntry == null || dominates(ch, e.getKey(), bestEntry.getKey()))
					bestEntry = e;
				if(!dominates(ch, bestEntry.getKey(), e.getKey()))
					throw new IllegalArgumentException(
							String.format(
									"Multiple methods in multimethod '%s' match dispatch value: %s -> %s and %s, and neither is preferred",
									name, dispatchVal, e.getKey(), bestEntry.getKey()));
				}
			}
		if(bestEntry == null)
			{
			bestValue = methodTable.valAt(defaultDispatchVal);
		        if(bestValue == null)
				return null;
			}
		else
			bestValue = bestEntry.getValue();
		}
	finally
		{
		rw.readLock().unlock();
		}


	//ensure basis has stayed stable throughout, else redo
	rw.writeLock().lock();
	try
		{
		if( mt == methodTable &&
		    pt == preferTable &&
		    ch == cachedHierarchy &&
			cachedHierarchy == hierarchy.deref())
			{
			//place in cache
			methodCache = methodCache.assoc(dispatchVal, bestValue);
			return (IFn) bestValue;
			}
		else
			{
			resetCache();
			return findAndCacheBestMethod(dispatchVal);
			}
		}
	finally
		{
		rw.writeLock().unlock();
		}
}
  
    public IPersistentMap getMethodTable() {
        return methodTable;
    }

    public IPersistentMap getPreferTable() {
        return preferTable;
    }

    
    public Object invoke() {
        return getFn(dispatchFn.invoke()).invoke();
    }

    public Object invoke(Object arg1) {
        return getFn(dispatchFn.invoke(arg1)).invoke(arg1);
    }

    public Object invoke(Object arg1, Object arg2) {
        return getFn(dispatchFn.invoke(arg1, arg2)).invoke(arg1, arg2);
    }

    public Object invoke(Object arg1, Object arg2, Object arg3) {
        return getFn(dispatchFn.invoke(arg1, arg2, arg3)).invoke(arg1, arg2, arg3);
    }

    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
        return getFn(dispatchFn.invoke(arg1, arg2, arg3, arg4)).
            invoke(arg1, arg2, arg3, arg4);
    }

    public Object invokeN(Object... args) {
        try {
            var mh_disp = IFn.__arity(dispatchFn, args.length).bindTo(dispatchFn);
            var f = getFn(mh_disp.invokeWithArguments(args));
            var mh_f = IFn.__arity(f, args.length).bindTo(f);
            return mh_f.invokeWithArguments(args);
        } catch (Throwable t) {
            throw Util.sneakyThrow(t);
        }
    }

    // unrolled version of FnBridge that covers arbitrary arities:
    private static final MethodHandle __a0;
    private static final MethodHandle __a1;
    private static final MethodHandle __a2;
    private static final MethodHandle __a3;
    private static final MethodHandle __a4;
    private static final MethodHandle __aN;

    @Override
    public MethodHandle __arityOrNull(int n) {
        switch (n) {
        case 0: return __a0;
        case 1: return __a1;
        case 2: return __a2;
        case 3: return __a3;
        case 4: return __a4;
        default: return __aN;
        }
    }
    
    private static final MethodHandle lookupInvoke(MethodHandles.Lookup l, int arity) {
        try {
            if (arity >= 0) {
                return l.findVirtual(MultiFn.class, "invoke",
                                     MethodType.genericMethodType(arity));
            } else {
                return l.findVirtual(MultiFn.class, "invokeN",
                                     MethodType.genericMethodType(0, true));
            }
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static {
        MethodHandles.Lookup l = MethodHandles.publicLookup();
        __a0 = lookupInvoke(l, 0);
        __a1 = lookupInvoke(l, 1);
        __a2 = lookupInvoke(l, 2);
        __a3 = lookupInvoke(l, 3);
        __a4 = lookupInvoke(l, 4);
        __aN = lookupInvoke(l, -1);
    }
}
