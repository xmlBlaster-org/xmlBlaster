package org.xmlBlaster.protocol.http.ajax;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.IsoDateParser;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.BlockingQueueWrapper;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;

/**
 * One browser session maps on exactly one xmlBlaster login session.
 * <p />
 * Callback messages are send as xml to the browser:
 * <pre>
 * &lt;xmlBlasterResponse>
 *  &lt;update>
 *    &lt;qos>...&lt;/qos>
 *    &lt;key>...&lt;/key>
 *    &lt;content>...&lt;/content>
 *  &lt;/update>
 * &lt;/xmlBlasterResponse>
 * </pre>
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2007
 */
public class BlasterInstance implements I_Callback, BlasterInstanceMBean {
	private static Logger log = Logger.getLogger(BlasterInstance.class.getName());
	
	private AjaxServlet ajaxServlet;

	private I_XmlBlasterAccess xmlBlasterAccess;

	private Global glob;

	private String id; // loginName

	private HttpSession session;

	private String sessionId;

	private StorageId storageId;

	private I_Queue updateQueue;

	private final String RELATED_AJAX = "ajax";

	private BlockingQueueWrapper blockingQueueWrapper;
	
    private ContextNode contextNode;
    /** My JMX registration */
    private JmxMBeanHandle mbeanHandle;
    
    private boolean isShutdownInProgress = false;
    
    private String clientInfo;
    
    private String remoteAddr;

	public BlasterInstance(AjaxServlet ajaxServlet, HttpServletRequest req) {
		this.ajaxServlet = ajaxServlet;
		req.getSession().setAttribute("sessionTimeoutListener", new SessionTimeoutListener(this));
		this.ajaxServlet.add(req.getSession().getId(), this);
	}

	// TODO Business specific code should be moved somewhere else
	public String getGpsTopicId() {
		// one publisher 'joe' -> 'gps.joe', many sessions 'joe/-1' 'joe/-2' may
		// access it
		if (xmlBlasterAccess != null && xmlBlasterAccess.isConnected()) {
			return "device." + this.xmlBlasterAccess.getSessionName().getLoginName() + ".nmea";
		}
		return "";
	}

	public String getCbSessionId() {
		if (xmlBlasterAccess != null && xmlBlasterAccess.isConnected()) {
			return this.xmlBlasterAccess.getConnectQos().getSessionCbQueueProperty()
					.getCallbackAddresses()[0].getSecretSessionId();
		}
		return "";
	}

	public void init(HttpServletRequest req, Properties props) throws XmlBlasterException {
		this.glob = new Global(props);
		this.xmlBlasterAccess = this.glob.getXmlBlasterAccess();
		this.id = this.xmlBlasterAccess.getId();
		this.session = req.getSession();
		this.sessionId = this.session.getId();
		this.clientInfo = this.ajaxServlet.getInfo(req, true, false);
		this.remoteAddr = req.getRemoteAddr();

        // JMX
	    try {
           String instanceName = glob.validateJmxValue(this.sessionId);
           this.contextNode = new ContextNode("instance", instanceName, this.ajaxServlet.getContextNode());
           this.mbeanHandle = glob.registerMBean(this.contextNode, this);
        }
	    catch (XmlBlasterException e) {
	       log.warning("Ignoring problem during JMX session registration: " + e.toString());
	    }
		this.updateQueue = new RamQueuePlugin();
		this.storageId = new StorageId(glob, RELATED_AJAX + ":" + this.sessionId);
		// glob.getNodeId().getId() is not yet available:
		QueuePropertyBase queueProps = new CbQueueProperty(glob, RELATED_AJAX, "/node/dummy");
		queueProps.setMaxEntries(100L);
		queueProps.setMaxBytes(200000L);
		this.updateQueue.initialize(storageId, queueProps);
		this.blockingQueueWrapper = new BlockingQueueWrapper(500L);
		this.blockingQueueWrapper.init(this.updateQueue);
		log.info(id + " Created new sessionId=" + this.sessionId);
		this.xmlBlasterAccess.registerConnectionListener(new I_ConnectionStateListener() {
			public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
				org.xmlBlaster.client.qos.ConnectReturnQos qos = connection.getConnectReturnQos();
				if (qos != null) {
					log
							.info("I_ConnectionStateListener.reachedAlive(): We were lucky, connected to "
									+ connection.getConnectReturnQos().getSessionName());
					id = connection.getConnectReturnQos().getSessionName().getLoginName();
				} else {
					log
							.severe("I_ConnectionStateListener.reachedAlive(): Missing ConnectReturnQos: "
									+ connection.toXml());
				}
			}

			public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
				log.warning("I_ConnectionStateListener.reachedPolling(): No connection to "
						+ glob.getId() + ", we are polling ...");
				// We shut down as the xbSession was transient and on xbRestart all subscriptions would be lost anyhow
				shutdown();
			}

			public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
				log.warning("I_ConnectionStateListener.reachedDead(): Connection to "
						+ glob.getId() + " is dead, good bye");
				shutdown();
			}
		});
	}

	public synchronized void execute(byte[] xmlScriptRaw, String xmlScript, Writer out) throws XmlBlasterException,
			UnsupportedEncodingException, IOException {
		if (xmlScript == null)
		   xmlScript = new String(xmlScriptRaw, "UTF-8");
		log.info(id + " Processing script: " + xmlScript);
		java.io.Reader reader = new java.io.StringReader(xmlScript);
		java.io.ByteArrayOutputStream outStream = new java.io.ByteArrayOutputStream();
		XmlScriptClient interpreter = new XmlScriptClient(this.glob, this.xmlBlasterAccess, this,
				null, outStream);
		interpreter.setThrowAllExceptions(true);
		interpreter.parse(reader);
		byte[] bytes = outStream.toByteArray();
		out.write(new String(bytes, "UTF-8"));

		// Processing script: <xmlBlaster><disconnect><qos></qos></disconnect></xmlBlaster>
		// Unfortunately on disconnect() the toDead() is not triggered, so detect it here (Hack:)
		if (xmlScript.indexOf("<disconnect>") != -1 && xmlScript.indexOf("</disconnect>") != -1
				|| xmlScript.indexOf("<disconnect/>") != -1) {
			//if (xmlBlasterAccess).isDead()) does not report a disconnect (buggy???)
			//if (((XmlBlasterAccess)xmlBlasterAccess).isShutdown())
			shutdown();
		}
	}

	public HttpSession getSession() {
		return session;
	}

	public void setSession(HttpSession session) {
		this.session = session;
	}

	public I_XmlBlasterAccess getXmlBlasterAccess() {
		return xmlBlasterAccess;
	}

	public void setXmlBlasterAccess(I_XmlBlasterAccess xmlBlasterAccess) {
		this.xmlBlasterAccess = xmlBlasterAccess;
	}

	public void put(MsgUnit msgUnit) throws XmlBlasterException {
		MsgQueuePublishEntry queueEntry = new MsgQueuePublishEntry(glob, msgUnit, this.storageId);
		if (this.updateQueue.getNumOfEntries() >= this.updateQueue.getMaxNumOfEntries())
			this.updateQueue.remove(1, -1);
		this.updateQueue.put(queueEntry, I_Queue.IGNORE_PUT_INTERCEPTOR);
	}

	/*
	public int sendUpdates(Writer out, boolean onlyContent) throws XmlBlasterException, IOException {
		if (this.updateQueue.getNumOfEntries() == 0)
			return 0;
		ArrayList entries = this.updateQueue.takeWithPriority(-1,-1,PriorityEnum.MIN_PRIORITY.getInt(),PriorityEnum.MAX_PRIORITY.getInt());
		int count = entries.size();
		if (count < 1)
			return 0;
		out.write("<xmlBlasterResponse>");
		for (int i = 0; i < count; i++) {
			MsgQueuePublishEntry entry = (MsgQueuePublishEntry) entries.get(i);
			out.write("<update>");
			if (onlyContent)
				out.write(entry.getMsgUnit().getContentStr());
			else
				out.write(entry.getMsgUnit().toXml());
			out.write("</update>");
		}
		out.write("</xmlBlasterResponse>");
		return count;
	}
	*/

	/**
	 * This method gets the entries in the correct form, i.e. the first stored comes first.
	 * @param out The out stream
	 * @param onlyContent if false the complete MsgUnit XML is send
	 * @param numEntries if -1 unlimited
	 * @param timeout if 0 not blocking, if timeout > 0: blocking, if timeout < 0: infinite blocking
	 * @return Number of send updates
	 * @throws XmlBlasterException
	 * @throws IOException
	 */
	public int sendUpdates(Writer out, boolean onlyContent, int numEntries, long timeout)
			throws XmlBlasterException, IOException {
		ArrayList entries = null;
		if (timeout == 0) { // None Blocking
			entries = this.updateQueue.takeWithPriority(numEntries, -1, PriorityEnum.MIN_PRIORITY
					.getInt(), PriorityEnum.MAX_PRIORITY.getInt());
		} else {
			entries = this.blockingQueueWrapper.blockingTakeWithPriority(numEntries, timeout,
					PriorityEnum.MIN_PRIORITY.getInt(), PriorityEnum.MAX_PRIORITY.getInt());
		}
		int count = entries.size();
		if (count < 1)
			return 0;
		out.write("<xmlBlasterResponse>");
		for (int i = 0; i < count; i++) {
			MsgQueuePublishEntry entry = (MsgQueuePublishEntry) entries.get(i);
			///// TODO: escapeXml !!!! or use base64 or use CDATA
			out.write("<update>");
			if (onlyContent)
				out.write(entry.getMsgUnit().getContentStr());
			else
				out.write(entry.getMsgUnit().toXml());
			out.write("</update>");
		}
		out.write("</xmlBlasterResponse>");
		this.updateQueue.clear();
		return count;

	}

	public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
			UpdateQos updateQos) throws XmlBlasterException {
		// Allow e.g. __sys__Login
		if (/*updateKey.isInternal() ||*/ !updateQos.isOk()) {
			log.warning(id + " Ignoring received message " + updateKey.toXml() + " "
					+ updateQos.toXml());
			return "";
		}
		MsgUnit msgUnit = new MsgUnit(updateKey.getData(), content, updateQos.getData());
		// if (!positionHasChanged(req, pos)) {
		// return "";String url = getUrl(pos);
		// }
		log.info(id + " receiving '" + msgUnit.toXml() + "'");
		put(msgUnit);
		return "";
	}

	// TODO Business specific code should be moved somewhere else
	public String getStartupPos() {
		if (getGpsTopicId().length() < 1)
			return "";
		return ""
				+ "<xmlBlasterResponse>"
				+ "<update>"
				+ "<sessionId>"
				+ getCbSessionId()
				+ "</sessionId>"
				+ "<key oid='"
				+ getGpsTopicId()
				+ "'/>"
				+ "<content>$GPRMC,095637.01,A,4749.860636,N,00903.623845,E,071.7,302.0,080107,,*35</content>"
				+ "<qos/>" + "</update>" + "</xmlBlasterResponse>";
	}

	public void shutdown() {
		if (isShutdownInProgress)
			return;
		this.isShutdownInProgress = true;
		try {
			this.glob.unregisterMBean(this.mbeanHandle);
		}
		catch (Throwable e) {
			log.warning(e.toString());
		}
		this.ajaxServlet.removeBlasterInstance(this.sessionId);
		if (!xmlBlasterAccess.isDead()) {
			try {
				xmlBlasterAccess.disconnect(null);
				log.info(id + " XmlBlaster is disconnected");
			} catch (Throwable e) {
				log.warning(id + " Ignoring disconnect problem " + e.toString());
			}
		}
		try {
			// Aware: triggers again a recursive shutdown via HttpSessionBindingListener
			this.session.invalidate();
			log.info(id + " Servlet session is invalidated, created=" + getCreationTimestamp() + " lastAccess=" + getLastAccessedTimestamp());
		} catch (Throwable e) {
			log.info(id + " Servlet session already invalidated: " + e.toString());
		}
	}

	void plainGet(HttpServletRequest req, HttpServletResponse res, StringWriter out)
			throws IOException, XmlBlasterException {
		String topic = (String) req.getParameter("topic");
		if (topic == null) {
			log.severe("The topic must be specified when using 'plainGet'");
			return;
		}
		String mimeType = (String) req.getParameter("mimeType");
		String charset = (String) req.getParameter("charset");
		log.info(id + " Making the request for 'plainGet' with topic='" + topic + "' mimeType='"
				+ mimeType + "' charset='" + charset + "'");
		res.setContentType("text/xml; charset=UTF-8");
		GetKey key = new GetKey(this.glob, topic);
		GetQos qos = new GetQos(this.glob);
		MsgUnit[] ret = this.xmlBlasterAccess.get(key, qos);
		if (ret != null && ret.length > 0) {
			if (ret.length > 1)
				log.warning(id + " " + ret.length
						+ " entries are found but only the first will be sent back to the client");
			if (mimeType == null)
				mimeType = ret[0].getContentMime();
			if (mimeType == null)
				mimeType = "text/xml";
			if (charset == null)
				charset = "UTF-8";
			res.setContentType(mimeType + "; charset=" + charset);
			out.write(ret[0].getContentStr());
		} else
			log.info(id + " No entry found for topic '" + topic + "'");
	}
	
	// JMX
	public int getUpdateQueueSize() {
		return (updateQueue == null) ? -1 : (int)updateQueue.getNumOfEntries();
	}
	
	public String getRelativeName() {
		if (this.xmlBlasterAccess == null) return "";
		SessionName sessionName = this.xmlBlasterAccess.getSessionName();
		if (sessionName == null) return "";
		return sessionName.getRelativeName();
	}
	
	public String getCreationTimestamp() {
		if (session == null) return "";
		try {
			return IsoDateParser.getUTCTimestamp(session.getCreationTime());
		}
		catch (Throwable e) {
			return e.toString();
		}
	}

	public String getLastAccessedTimestamp() {
		if (session == null) return "";
		try {
			return IsoDateParser.getUTCTimestamp(session.getLastAccessedTime());
		}
		catch (Throwable e) {
			return e.toString();
		}
	}

	public boolean isShutdown() {
		if (this.xmlBlasterAccess == null) return true;
		return !this.xmlBlasterAccess.isAlive();
	}
	
	public String getClientInfo() {
		return this.clientInfo;
	}
	
	public String shutdownAndBlockIP() {
		shutdown();
		if (this.remoteAddr != null)
			this.ajaxServlet.addBlockedIP(this.remoteAddr);
		return "Shutdown and blocked " + this.remoteAddr;
	}

	public String getRemoteAddr() {
		return remoteAddr;
	}
}
