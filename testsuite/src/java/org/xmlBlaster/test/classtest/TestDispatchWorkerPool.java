package org.xmlBlaster.test.classtest;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DispatchWorkerPool;

import junit.framework.TestCase;

public class TestDispatchWorkerPool extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	class Worker implements Runnable {
		DispatchWorkerPool dwp;
		String name;

		Worker(DispatchWorkerPool dwp, String name) {
			this.dwp = dwp;
			this.name = name;
		}

		@Override
		public void run() {
			// "-prio=" + Thread.currentThread().getPriority()
			System.out.println(Thread.currentThread().getName() + "-" + name + " START: " + dwp.getStatistic());
			sleepSec(2);
			System.out.println(Thread.currentThread().getName() + "-" + name + " END  : " + dwp.getStatistic());
		}

		public String toString() {
			return this.name;
		}
	}

	enum TEST_CASE {
		SERVER, SERVER_DEFAULT, SERVER_BLOCK_FOREVER, CLIENT, CLIENT_DEFAULT
	}

	public void testExecute() throws XmlBlasterException, InterruptedException {
		Global glob = new Global();

		TEST_CASE testCase = TEST_CASE.SERVER;

		if (testCase == TEST_CASE.SERVER) {
			glob = new ServerScope();
			// On exhaust: java.util.concurrent.RejectedExecutionException
			glob.getProperty().set("maximumPoolSize", "7");

			// corePoolSize the number of threads to keep in the pool, even if they are
			// idle, unless {@code allowCoreThreadTimeOut} is set
			glob.getProperty().set("createThreads", "2");

			// keepAliveTime when the number of threads is greater than the core, this is
			// the maximum time that excess idle threads will wait for new tasks before
			// terminating.
			glob.getProperty().set("threadLifetime", "" + (2 * 1000)); // 180*1000 millis

			glob.getProperty().set("maxWaitTime", "" + (10 * 1000)); // millis
		} else if (testCase == TEST_CASE.SERVER_DEFAULT) {
			glob = new ServerScope();
			
		} else if (testCase == TEST_CASE.SERVER_BLOCK_FOREVER) {
			glob = new ServerScope();
			glob.getProperty().set("maximumPoolSize", "7");
			glob.getProperty().set("createThreads", "2");
			glob.getProperty().set("maxWaitTime", "0"); // millis

		} else if (testCase == TEST_CASE.CLIENT) {
			glob.getProperty().set("maximumPoolSize.client", "2");
			glob.getProperty().set("createThreads.client", "1");
			glob.getProperty().set("threadLifetime", "" + (2 * 1000)); // 180*1000 millis
			glob.getProperty().set("maxWaitTime", "" + (10 * 1000)); // millis 10sec

		} else if (testCase == TEST_CASE.CLIENT_DEFAULT) {
			// we are a Global, nothing to set here
		}

		glob.getProperty().set("threadPriority", "" + Thread.MIN_PRIORITY);
		final DispatchWorkerPool dwp = new DispatchWorkerPool(glob);
		int numWorker = 25;
		for (int i = 0; i < numWorker; i++) {
			dwp.execute(new Worker(dwp, "Worker#" + i));
		}

		sleepSec(2);
		boolean done = false;
		for (int j = 0; j < 20; j++) {
			System.out.println(Thread.currentThread().getName() + " DispatchWorkerPool created: " + dwp.getStatistic());
			if (dwp.getActiveCount() == 0) {
				done = true;
				break;
			}
			sleepSec(2);
		}
		if (!done)
			fail("Worker threads are not done");
		System.out.println("DONE " + testCase);
	}

	private void sleepSec(int sec) {
		try {
			Thread.sleep(sec * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
