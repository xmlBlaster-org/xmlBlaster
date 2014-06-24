/*------------------------------------------------------------------------------
Name:      TestSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSub.java 14833 2006-03-06 21:38:58Z laghi $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;

/**
 * This client tests the method subscribe() with a later publish() with XPath
 * query. <br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server, as it
 * cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * 
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSub
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSub
 * </pre>
 */
public class TestSubNewestOnly extends TestCase implements I_Callback {
	private static String ME = "TestSubNewestOnly";
	private final Global glob;
	private static Logger log = Logger.getLogger(TestSubNewestOnly.class
			.getName());
	private boolean messageArrived;
	private String subscribeOid;
	private String publishOid = "topic_TestSubNewestOnly";
	private I_XmlBlasterAccess senderConnection;
	private String senderName = "Publisher";
	private String senderContent = "";
	private final String receiverName = "Subscriber/session/1";
	private I_XmlBlasterAccess receiverConnection;
	private int numReceived = 0;

	/**
	 * Constructs the TestSub object.
	 * <p />
	 * 
	 * @param testName
	 *            The name used in the test suite
	 */
	public TestSubNewestOnly(Global glob, String testName) {
		super(testName);
		this.glob = glob;
	}

	/**
	 * Connect to xmlBlaster and login
	 */
	protected void setUp() {
		String passwd = "secret";
		try {
			Global senderGlob = glob.getClone(null);
			senderConnection = senderGlob.getXmlBlasterAccess();
			senderConnection.connect(new ConnectQos(senderGlob, senderName,
					passwd), this);
			erase(false);
		} catch (Exception e) {
			log.severe("Login failed: " + e.toString());
			e.printStackTrace();
			assertTrue("Login failed: " + e.toString(), false);
		}

		connectSubscriber();
	}

	private void connectSubscriber() {
		String passwd = "secret";
		try {
			Global receiverGlob = glob.getClone(null);
			receiverConnection = receiverGlob.getXmlBlasterAccess();
			ConnectQos connectQos = new ConnectQos(receiverGlob, receiverName,
					passwd);
			CallbackAddress cbProps = new CallbackAddress(new Global());
			cbProps.setRetries(-1);
			connectQos.addCallbackAddress(cbProps);
			receiverConnection.connect(connectQos, this);
		} catch (Exception e) {
			log.severe("Login failed: " + e.toString());
			e.printStackTrace();
			fail("Login failed: " + e.toString());
		}
	}

	private void erase(boolean check) {
		String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
		String qos = "<qos></qos>";
		try {
			EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
			if (check)
				assertEquals("Erase", 1, arr.length);
		} catch (XmlBlasterException e) {
			fail("Erase XmlBlasterException: " + e.getMessage());
		}
	}

	/**
	 * Tears down the fixture.
	 * <p />
	 * cleaning up .... erase() the previous message OID and logout
	 */
	protected void tearDown() {
		erase(true);
		receiverConnection.disconnect(null);
		senderConnection.disconnect(null);
	}

	public void subscribe() {
		SubscribeKey key = new SubscribeKey(receiverConnection.getGlobal(),
				publishOid);
		SubscribeQos qos = new SubscribeQos(receiverConnection.getGlobal());
		qos.setNewestOnly(true);
		numReceived = 0;
		subscribeOid = null;
		try {
			SubscribeReturnQos subscribeReturnQos = receiverConnection
					.subscribe(key, qos);
			subscribeOid = subscribeReturnQos.getSubscriptionId();
			log.info("Success: Subscribe subscription-id=" + subscribeOid
					+ " done: " + subscribeReturnQos.toXml());
		} catch (XmlBlasterException e) {
			log.warning("XmlBlasterException: " + e.getMessage());
			assertTrue("subscribe - XmlBlasterException: " + e.getMessage(),
					false);
		}
		assertTrue("returned null subscribeOid", subscribeOid != null);
		assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
	}

	public void publishThree() {
		int count = 3;
		for (int i = 0; i < count; i++) {
			PublishKey publishKey = new PublishKey(
					senderConnection.getGlobal(), publishOid);
			PublishQos publishQos = new PublishQos(
					senderConnection.getGlobal(), publishOid);
			senderContent = "" + (i + 1);
			try {
				MsgUnit msgUnit = new MsgUnit(publishKey,
						senderContent.getBytes(), publishQos);
				PublishReturnQos tmp = senderConnection.publish(msgUnit);
				assertEquals("Wrong publishOid", publishOid, tmp.getKeyOid());
				log.info("Success: Publishing " + senderContent + " done, returned oid=" + publishOid);
			} catch (XmlBlasterException e) {
				log.warning("XmlBlasterException: " + e.getMessage());
				assertTrue("publish - XmlBlasterException: " + e.getMessage(),
						false);
			}
		}
	}

	/**
	 * TEST: Construct a message and publish it,<br />
	 * the previous XPath subscription should match and send an update.
	 */
	public void testNewestOnly() throws InterruptedException {
		subscribe();
		receiverConnection.leaveServer(null);
		Thread.sleep(1000L);
		assertEquals("numReceived after subscribe", 0, numReceived);

		publishThree();
		Thread.sleep(1000L);
		assertEquals("numReceived after subscribe", 0, numReceived);

		connectSubscriber();
		waitOnUpdate(1000L);
		assertEquals("numReceived after subscribe", 1, numReceived);
	}

	/**
	 * This is the callback method invoked from xmlBlaster delivering us a new
	 * asynchronous message.
	 * 
	 * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[],
	 *      UpdateQos)
	 */
	public String update(String cbSessionId_, UpdateKey updateKey,
			byte[] content, UpdateQos updateQos) {
		String contentStr = new String(content);

		if (updateQos.isErased()) {
			return "";
		}

		log.info("Receiving update of message " + updateKey.getOid() + " with content="
				+ contentStr);
		// log.info("subscribeOid=" + subscribeOid + ":" + updateQos.toXml());

		numReceived += 1;

		// wait that the subscribe() has returned as well
		for (int ii = 0; ii < 5; ii++) {
			if (subscribeOid != null)
				break;
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException i) {
			}
			log.info("waiting ...");
		}

		assertEquals("Wrong sender", senderName, updateQos.getSender()
				.getLoginName());
		assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId",
				subscribeOid, updateQos.getSubscriptionId());
		assertEquals("Wrong oid of message returned", publishOid,
				updateKey.getOid());

		assertEquals("Message content is corrupted", senderContent,
				contentStr);

		messageArrived = true;
		return "";
	}

	/**
	 * Little helper, waits until the variable 'messageArrive' is set to true,
	 * or returns when the given timeout occurs.
	 * 
	 * @param timeout
	 *            in milliseconds
	 */
	private void waitOnUpdate(final long timeout) {
		long pollingInterval = 50L; // check every 0.05 seconds
		if (timeout < 50)
			pollingInterval = timeout / 10L;
		long sum = 0L;
		while (!messageArrived) {
			try {
				Thread.sleep(pollingInterval);
			} catch (InterruptedException i) {
			}
			sum += pollingInterval;
			if (sum > timeout) {
				log.warning("Timeout of " + timeout + " occurred");
				break;
			}
		}
		messageArrived = false;
	}

	/**
	 * Method is used by TestRunner to load these tests
	 * 
	 * <pre>
	 * java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNewestOnly
	 * </pre>
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTest(new TestSubNewestOnly(new Global(), "testNewestOnly"));
		return suite;
	}

	/**
	 * Invoke: java org.xmlBlaster.test.qos.TestSubNewestOnly
	 * 
	 * @throws InterruptedException
	 */
	public static void main(String args[]) throws InterruptedException {
		Global glob = new Global();
		if (glob.init(args) != 0) {
			System.err.println(ME + ": Init failed");
			System.exit(1);
		}
		TestSubNewestOnly testSub = new TestSubNewestOnly(glob,
				"testNewestOnly");
		testSub.setUp();
		testSub.testNewestOnly();
		testSub.tearDown();
	}
}
