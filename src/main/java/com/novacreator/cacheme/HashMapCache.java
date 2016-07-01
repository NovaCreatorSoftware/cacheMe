package com.novacreator.cacheme;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashMapCache<KEY, VALUE> {
	private static final Logger LOGGER = LoggerFactory.getLogger(HashMapCache.class);

	private final ConcurrentMap<KEY, Future<VALUE>> CACHE = new ConcurrentHashMap<>();

	private Future<VALUE> create(final KEY key, final Callable<VALUE> callable) {
		long startTime = System.currentTimeMillis();
		try {
			Future<VALUE> future = CACHE.get(key);
			if(future != null || callable == null) {
				return future;
			}
			final FutureTask<VALUE> futureTask = new FutureTask<VALUE>(callable);
			future = CACHE.putIfAbsent(key, futureTask);
			if(future != null) {
				return future;
			}
			futureTask.run();
			return futureTask;
		} finally {
			LOGGER.debug("HashMapCache.create for key=" + key + " took " + (System.currentTimeMillis() - startTime) + "ms");
		}
	}

	public void put(final KEY key, final VALUE value) {
		long startTime = System.currentTimeMillis();
		create(key, new Callable<VALUE>() {
			@Override
			public VALUE call() throws Exception {
				return value;
			}
		});
		LOGGER.debug("HashMapCache.put(" + key + ", " + value + ") took " + (System.currentTimeMillis() - startTime) + "ms");
	}

	public VALUE get(final KEY key) throws InterruptedException, ExecutionException {
		return get(key, null);
	}
	//if you need to do some work on the data, do it inside the callable (like do computations that are based on other elements in the cache)
	public VALUE get(final KEY key, final Callable<VALUE> callable) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		VALUE result = null;
		try {
			final Future<VALUE> future = create(key, callable);
			if(future != null) {
				result = future.get();
			}
			return result;
		} catch (final InterruptedException | ExecutionException | RuntimeException e) {
			LOGGER.error(e.getMessage(), e);
			CACHE.remove(key);
			throw e;
		} finally {
			LOGGER.debug("HashMapCache.get(" + key + ")=" + result + " took " + (System.currentTimeMillis() - startTime) + "ms");			
		}
	}

	public VALUE remove(final KEY key) {
		long startTime = System.currentTimeMillis();
		Future<VALUE> future = CACHE.remove(key);
		VALUE result = null;
		if(future != null) {
			try {
				result = future.get();
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		LOGGER.debug("HashMapCache.remove(" + key + ")=" + result + " took " + (System.currentTimeMillis() - startTime) + "ms");
		return result;
	}
	public boolean remove(final KEY key, final VALUE value) {
		long startTime = System.currentTimeMillis();
		boolean result = CACHE.remove(key, value);
		LOGGER.debug("HashMapCache.remove(" + key + ", " + value + ")=" + result + " took " + (System.currentTimeMillis() - startTime) + "ms");		
		return result;
	}
}
