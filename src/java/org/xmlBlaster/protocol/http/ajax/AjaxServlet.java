/*------------------------------------------------------------------------------
Name:      AjaxServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Registered in xmlBlaster/demo/http/WEB-INF/web.xml
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.util.Base64;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.BlockingQueueWrapper;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;

//Debug: ~/apache-tomcat-5.5.15/bin/catalina.sh jpda start

/**
 * One browser session maps on exactly one xmlBlaster login session.
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2007
 */
class BlasterInstance implements I_Callback {
	private static Logger log = Logger.getLogger(BlasterInstance.class
			.getName());

	private Map/*<String, BlasterInstance>*/ blasterInstanceMap;

	private I_XmlBlasterAccess xmlBlasterAccess;

	private Global glob;

	private HttpSession session;

	private String sessionId;

	private StorageId storageId;

	private I_Queue updateQueue;

	private final String RELATED_AJAX = "ajax";

   private BlockingQueueWrapper blockingQueueWrapper;
   
	public BlasterInstance(HttpServletRequest req,
			Map/*<String, BlasterInstance>*/ blasterInstanceMap) {
		this.blasterInstanceMap = blasterInstanceMap;
		req.getSession().setAttribute("sessionTimeoutListener",
				new SessionTimeoutListener(this));
		synchronized (this.blasterInstanceMap) {
			this.blasterInstanceMap.put(req.getSession().getId(), this);
		}
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
			return this.xmlBlasterAccess.getConnectQos()
				.getSessionCbQueueProperty().getCallbackAddresses()[0]
				.getSecretSessionId();
		}
		return "";
	}

	public void init(HttpServletRequest req, Properties props)
			throws XmlBlasterException {
		this.glob = new Global(props);
		this.xmlBlasterAccess = this.glob.getXmlBlasterAccess();
		this.session = req.getSession();
		this.sessionId = this.session.getId();
		this.updateQueue = new RamQueuePlugin();
		this.storageId = new StorageId(glob, RELATED_AJAX+":"+this.sessionId);
		// glob.getNodeId().getId() is not yet available:
		QueuePropertyBase queueProps = new CbQueueProperty(glob, RELATED_AJAX,"/node/dummy");
		queueProps.setMaxEntries(100L);
		queueProps.setMaxBytes(200000L);
		this.updateQueue.initialize(storageId, queueProps);
      this.blockingQueueWrapper = new BlockingQueueWrapper(500L);
      this.blockingQueueWrapper.init(this.updateQueue);
		log.info("Created new sessionId=" + this.sessionId);
	}

	public synchronized void execute(byte[] xmlScriptRaw, Writer out)
			throws XmlBlasterException, UnsupportedEncodingException,
			IOException {
		String xmlScript = new String(xmlScriptRaw, "UTF-8");
      log.info("Processing script: " + xmlScript);
		java.io.Reader reader = new java.io.StringReader(xmlScript);
		java.io.ByteArrayOutputStream outStream = new java.io.ByteArrayOutputStream();
		XmlScriptClient interpreter = new XmlScriptClient(this.glob,
				this.xmlBlasterAccess, this, null, outStream);
		interpreter.parse(reader);
		byte[] bytes = outStream.toByteArray();
		out.write(new String(bytes, "UTF-8"));
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
		MsgQueuePublishEntry queueEntry = new MsgQueuePublishEntry(glob,
				msgUnit, this.storageId);
		if (this.updateQueue.getNumOfEntries() >= this.updateQueue
				.getMaxNumOfEntries())
			this.updateQueue.remove(1, -1);
		this.updateQueue.put(queueEntry, I_Queue.IGNORE_PUT_INTERCEPTOR);
	}

	public int sendUpdates(Writer out, boolean onlyContent) throws XmlBlasterException,
			IOException {
		ArrayList entries = this.updateQueue.takeLowest(-1, -1, null, false);
		//For now we max send one message
		// ArrayList entries = this.updateQueue.takeLowest(1, -1, null, false);
		MsgUnit[] msgs = new MsgUnit[entries.size()];
		if (msgs.length < 1)
			return 0;
      out.write("<xmlBlasterResponse>");
		for (int i = 0; i < msgs.length; i++) {
			MsgQueuePublishEntry entry = (MsgQueuePublishEntry) entries.get(i);
         out.write("<update>");
         if (onlyContent)
            out.write(entry.getMsgUnit().getContentStr());
         else
            out.write(entry.getMsgUnit().toXml());
         out.write("</update>");
		}
      out.write("</xmlBlasterResponse>");
		this.updateQueue.clear();
		return msgs.length;
	}

   /**
    * This method gets the entries in the correct form, i.e. the first stored comes first. (sendUpdates always sends
    * the freshest).
    * 
    * @param out
    * @param onlyContent
    * @param numEntries
    * @param timeout
    * @return
    * @throws XmlBlasterException
    * @throws IOException
    */
   public int sendUpdatesBlocking(Writer out, boolean onlyContent, int numEntries, long timeout) throws XmlBlasterException,
   IOException {
      
      ArrayList entries = this.blockingQueueWrapper.blockingTakeWithPriority(numEntries, timeout, 0, 9);
      
      MsgUnit[] msgs = new MsgUnit[entries.size()];
      if (msgs.length < 1)
         return 0;
    out.write("<xmlBlasterResponse>");
    for (int i = 0; i < msgs.length; i++) {
       MsgQueuePublishEntry entry = (MsgQueuePublishEntry) entries.get(i);
       out.write("<update>");
       if (onlyContent)
          out.write(entry.getMsgUnit().getContentStr());
       else
          out.write(entry.getMsgUnit().toXml());
       out.write("</update>");
    }
    out.write("</xmlBlasterResponse>");
    this.updateQueue.clear();
    return msgs.length;
      
   }


   
   public String update(String cbSessionId, UpdateKey updateKey,
			byte[] content, UpdateQos updateQos) throws XmlBlasterException {
		if (updateKey.isInternal() || !updateQos.isOk()) {
			log.warning("Ignoring received message " + updateKey.toXml() + " "
					+ updateQos.toXml());
			return "";
		}
		MsgUnit msgUnit = new MsgUnit(updateKey.getData(), content, updateQos
				.getData());
		// if (!positionHasChanged(req, pos)) {
		// return "";String url = getUrl(pos);
		// }
      log.info("receiving '" + msgUnit.toXml() + "'");
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
		synchronized (this.blasterInstanceMap) {
			this.blasterInstanceMap.remove(this.sessionId);
		}
		if (!xmlBlasterAccess.isDead()) {
			try {
				xmlBlasterAccess.disconnect(null);
			} catch (Throwable e) {
				log.warning("Ignoring disconnect problem " + e.toString());
			}
		}
		this.session.invalidate();
	}
   
   
   void plainGet(HttpServletRequest req, HttpServletResponse res, StringWriter out) throws IOException, XmlBlasterException {
      String topic = (String)req.getParameter("topic");
      if (topic == null) {
         log.severe("The topic must be specified when using 'plainGet'");
         return;
      }
      String mimeType = (String)req.getParameter("mimeType");
      String charset =  (String)req.getParameter("charset");
      log.info("Making the request for 'plainGet' with topic='" + topic + "' mimeType='" + mimeType + "' charset='" + charset + "'");
      res.setContentType("text/xml; charset=UTF-8");
      GetKey key = new GetKey(this.glob, topic);
      GetQos qos = new GetQos(this.glob);
      MsgUnit[] ret = this.xmlBlasterAccess.get(key, qos);
      if (ret != null && ret.length > 0) {
         if (ret.length > 1)
            log.warning("" + ret.length + " entries are found but only the first will be sent back to the client");
         if (mimeType == null)
            mimeType = ret[0].getContentMime();
         if (mimeType == null)
            mimeType = "text/xml";
         if (charset == null)
            charset = "UTF-8";
         res.setContentType(mimeType + "; charset=" + charset);
         out.write(ret[0].getContentStr());
      }
      else
         log.info("No entry found for topic '" + topic + "'");
   }
   
}

/**
 * Detect when a servlet session dies (with tomcat typically after one hour).
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2007
 */
class SessionTimeoutListener implements HttpSessionBindingListener {
	private static Logger log = Logger.getLogger(SessionTimeoutListener.class
			.getName());

	private BlasterInstance blasterInstance;

	public SessionTimeoutListener(BlasterInstance blasterInstance) {
		this.blasterInstance = blasterInstance;
	}

	public void valueBound(HttpSessionBindingEvent event) {
		log.info("Session is bound: " + event.getSession().getId());
	}

	public void valueUnbound(HttpSessionBindingEvent event) {
		log.info("Session is unbound: " + event.getSession().getId());
		this.blasterInstance.shutdown();
	}

}

/**
 * This servlet supports requests from a browser, it queries the topic given by
 * "gpsTopicId" configuration which needs to contain GPS coordinates (published
 * for example by a PDA).
 *
 * <p>
 * Use xmlBlaster/demo/http/gps.html to display the coordinates in a map.
 * </p>
 *
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2007
 */
public class AjaxServlet extends HttpServlet {
	private static final long serialVersionUID = -8094289301696678030L;

	private static Logger log = Logger.getLogger(AjaxServlet.class
			.getName());

	private Properties props = new Properties();

	private int maxInactiveInterval = 60; // sec

	/** key is the browser sessionId */
	private Map/*<String, BlasterInstance>*/ blasterInstanceMap;

	public void init(ServletConfig conf) throws ServletException {
		super.init(conf);
		// Add the web.xml parameters to our environment settings:
		Enumeration enumer = conf.getInitParameterNames();
		while (enumer.hasMoreElements()) {
			String name = (String) enumer.nextElement();
			if (name != null && name.length() > 0)
				props.setProperty(name, conf.getInitParameter(name));
		}
		String tmp = props.getProperty("maxInactiveInterval");
		if (tmp != null)
			this.maxInactiveInterval = Integer.valueOf(tmp).intValue();
		this.blasterInstanceMap = new HashMap/*<String, BlasterInstance>*/();
	}

	public void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		doGet(req, res);
	}

	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		String host = req.getRemoteAddr();
		String ME = "AjaxServlet.doGet(" + host + "): ";
		//log.info("ENTERING DOGET ....");
		boolean forceLoad = req.getParameter("forceLoad") != null;
		if (forceLoad)
			log(ME + "forceLoad=" + forceLoad);
		boolean newBrowser = false;
		if (req.getSession(false) == null) {
			HttpSession session = req.getSession(true);
			session.setMaxInactiveInterval(this.maxInactiveInterval);
			newBrowser = true;
			log(ME + "new browser arrived, charset="
					+ req.getCharacterEncoding());
		}

		// set header field first
      //res.setContentType("text/plain; charset=UTF-8");
      // xml header don't like empty response, so send at least "<void/>
		res.setContentType("text/xml; charset=UTF-8");
      StringWriter out = new StringWriter();

		try {
			BlasterInstance blasterInstance = getBlasterInstance(req);

			String actionType = (String) req.getParameter("ActionType");
			if (actionType == null) {
				log("Missing ActionType, ignoring request");
				return;
			}

			// TODO: handle logout script to also destroy the session entry

			if (actionType.equals("xmlScript")) {
				String xmlScript64 = (String)req.getParameter("xmlScriptBase64");
            String xmlScriptPlain = (String)req.getParameter("xmlScriptPlain");
            byte[] raw = null;
            if (xmlScript64 != null && xmlScriptPlain != null) {
               String errTxt = "You can not set both 'xmlScriptBase64' and 'xmlScriptPlain'";
               out.write(errTxt);
               return;
            }
            if (xmlScript64 != null) {
               // the url encoder has somewhere changed my + to blanks
               // you must send this by invoking encodeURIComponent(txt) on the javascript side. 
               xmlScript64 = ReplaceVariable.replaceAll(xmlScript64, " ", "+");
               raw = Base64.decode(xmlScript64);
            }
            else if (xmlScriptPlain != null) {
               raw = xmlScriptPlain.getBytes();
            }
            else {
               String errTxt = "You must choose one of 'xmlScriptBase64' and 'xmlScriptPlain' since you choosed 'xmlScript'";
               out.write(errTxt);
               return;
            }
            blasterInstance.execute(raw, out);
            return;
			}

         if (actionType.equals("updatePoll")) {
            String onlyContentTxt = (String)req.getParameter("onlyContent");
            boolean onlyContent = onlyContentTxt != null && "true".equalsIgnoreCase(onlyContentTxt.trim());
            int count = blasterInstance.sendUpdates(out, onlyContent);
            if (count == 0) {
               if (newBrowser || forceLoad) {
                  String initGps = (String)req.getParameter("initGps");
                  if (initGps != null && "true".equalsIgnoreCase(initGps.trim())) {
                     String tmp = blasterInstance.getStartupPos();
                     if (tmp.length() > 0) {
                        out.write(tmp);
                        log(ME + tmp);
                     }
                  }
               }
            }
            else
               log(ME + " Sending " + count + " received update messages to browser");
            return;
         }
         if (actionType.equals("updatePollBlocking")) {
            String onlyContentTxt = (String)req.getParameter("onlyContent");
            boolean onlyContent = onlyContentTxt != null && "true".equalsIgnoreCase(onlyContentTxt.trim());
            String timeoutTxt = (String)req.getParameter("timeout");
            long timeout = 15000L;
            if (timeoutTxt != null)
               timeout = Long.parseLong(timeoutTxt.trim()) * 1000L;
            String numEntriesTxt = (String)req.getParameter("numEntries");
            int numEntries = 1;
            if (numEntriesTxt != null)
               numEntries = Integer.parseInt(numEntriesTxt.trim());
            blasterInstance.sendUpdatesBlocking(out, onlyContent, numEntries, timeout);
            return;
         }
         
         if (actionType.equals("plainGet")) {
            blasterInstance.plainGet(req, res, out);
         }

			// log(ME+"Ignoring identical");
		} catch (XmlBlasterException e) {
			log.warning("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
					+ e.toString());
			log("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
					+ e.toString());
			// if (newBrowser || forceLoad)
			// out.write(blasterInstance.getStartupPos());
		} catch (Throwable e) {
			log.severe("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
					+ e.toString());
         e.printStackTrace();
			log("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
					+ e.toString());
		} finally {
         PrintWriter backToBrowser = res.getWriter();
         if (out.getBuffer().length() > 0)
        	 log.info("Sending now '" + out.getBuffer().toString() + "'");
         if (out.getBuffer().length() > 0)
            backToBrowser.write(out.getBuffer().toString());
         else
            backToBrowser.write("<void/>"); // text/xml needs at least a root tag!
         backToBrowser.close();
		}
	}

	private synchronized BlasterInstance getBlasterInstance(
			HttpServletRequest req) throws XmlBlasterException {
		// Map blasterInstanceMap =
		// (Map)session.getAttribute("blasterInstanceMap");
		BlasterInstance blasterInstance = null;
		synchronized (this.blasterInstanceMap) {
			blasterInstance = (BlasterInstance) this.blasterInstanceMap.get(req
					.getSession().getId());
			if (blasterInstance != null)
				return blasterInstance;

			blasterInstance = new BlasterInstance(req, this.blasterInstanceMap);
		}
		blasterInstance.init(req, props);
		// is done in ctor
		// this.blasterInstanceMap.put(req.getSession().getId(),
		// blasterInstance);
		return blasterInstance;
	}

	public String getServletInfo() {
		return "Personalized access to xmlBlaster with XmlScript";
	}

	public BlasterInstance[] getBlasterInstances() {
		synchronized (this.blasterInstanceMap) {
			return (BlasterInstance[])this.blasterInstanceMap.values().toArray(
					new BlasterInstance[this.blasterInstanceMap.size()]);
		}
	}

	public synchronized void destroy() {
		BlasterInstance[] instances = getBlasterInstances();
		for (int i = 0; i < instances.length; i++) {
			instances[i].shutdown();
		}
		synchronized (this.blasterInstanceMap) {
			// is redundant, is done in instace.shutdown
			this.blasterInstanceMap.clear();
		}
	}

	public void log(String text) {
		// System.err.println("ERR"+text);
		// System.out.println("OUT"+text);
		super.log(text);
	}
}
