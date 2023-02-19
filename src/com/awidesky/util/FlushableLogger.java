package com.awidesky.util;

/**
 * A <code>Logger</code> class that support flush operation.
 * */
public interface FlushableLogger extends Logger {

	public abstract void flush();
	
}
