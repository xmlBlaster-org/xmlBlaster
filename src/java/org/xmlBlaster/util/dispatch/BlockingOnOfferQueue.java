package org.xmlBlaster.util.dispatch;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BlockingOnOfferQueue<T> extends ArrayBlockingQueue<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private long msTimeout;

	public BlockingOnOfferQueue(int capacity, long msTimeout) {
		super(capacity, true);
		this.msTimeout = msTimeout;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.ArrayBlockingQueue#offer(java.lang.Object)
	 */
	@Override
	public boolean offer(T e) {
		try {
			if (msTimeout > 0L)
				return super.offer(e, msTimeout, TimeUnit.MILLISECONDS);
			return super.offer(e);
		}
		catch (InterruptedException ex) {
			return false;
		}
	}


}
