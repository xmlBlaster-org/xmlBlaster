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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.xmlBlaster.util.Base64;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;

//Debug: $TOMCAT_HOME/bin/catalina.sh jpda start

/**
 * This servlet supports requests from a browser, it queries the topic given by
 * "gpsTopicId" configuration which needs to contain GPS coordinates (published
 * for example by a PDA).
 *
 * <p>
 * Use xmlBlaster/demo/http/gps.html to display the coordinates in a map.
 * </p>
 * 
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
public class AjaxServlet extends HttpServlet implements AjaxServletMBean {
	private static final long serialVersionUID = -8094289301696678030L;

	//private static Logger log = Logger.getLogger(AjaxServlet.class.getName());

	private Properties props = new Properties();

	private int maxInactiveInterval = 1800; // sec, see web.xml and javadoc of doGet() for details

    private ContextNode contextNode;
    /** My JMX registration */
    private JmxMBeanHandle mbeanHandle;
    
    private Set/*<String>*/ blockedIPs;

	/** key is the browser sessionId */
	private Map/*<String, BlasterInstance>*/blasterInstanceMap;
	
	/** We support additional admin sessions */
	private int maxUserSessions = 10000;
	
	private int getConfProp(String key, int def) {
		try {
			String tmp = props.getProperty(key);
			if (tmp != null)
				return Integer.valueOf(tmp).intValue();
		}
		catch (Throwable e) {
			return def;
		}
		return def;
	}

	public void init(ServletConfig conf) throws ServletException {
		super.init(conf);
		// Add the web.xml parameters to our environment settings:
		Enumeration enumer = conf.getInitParameterNames();
		while (enumer.hasMoreElements()) {
			String name = (String) enumer.nextElement();
			if (name != null && name.length() > 0)
				props.setProperty(name, conf.getInitParameter(name));
		}
		this.maxInactiveInterval = getConfProp("maxInactiveInterval",this.maxInactiveInterval);

		this.blasterInstanceMap = new HashMap/*<String, BlasterInstance>*/();

		this.maxUserSessions = getConfProp("maxUserSessions",this.maxUserSessions);

		this.blockedIPs = new TreeSet/*<String>*/();
		
        // JMX
	    try {
	       Global glob = Global.instance();
           String instanceName = glob.validateJmxValue("AjaxServlet");
           this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG, instanceName, null);//, glob.getContextNode());
           this.mbeanHandle = glob.registerMBean(this.contextNode, this);
        }
	    catch (XmlBlasterException e) {
	       log("Ignoring problem during JMX session registration: " + e.toString());
	    }
	    
	    //ServletContext ctx = getServletContext();
	    //InputStream is = ctx.getResourceAsStream("/WEB-INF/errorText.txt");
	    //String filename = getServletContext().getRealPath("/WEB-INF/appconfig.xml");
	}

	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException,
			IOException {
		doGet(req, res);
	}

	public String getInfo(HttpServletRequest req) {
		return getInfo(req, false, false);
	} 

	public String getInfo(HttpServletRequest req, boolean more, boolean all) {
		StringBuffer buf = new StringBuffer(128);
		buf.append("AjaxServlet.doGet(");
		buf.append(req.getRemoteAddr());
		
		if (more) {
			//req.getHeader("user-agent")
			buf.append(", ").append("").append(req.getHeader("User-Agent"));
			buf.append(", ").append("len=").append(req.getContentLength());
			buf.append(", ").append("charset=").append(req.getCharacterEncoding());
		}
		
		if (all) {
			Enumeration en = req.getHeaderNames();
			buf.append(", HEADER:");
			while (en.hasMoreElements()) {
				String key = (String)en.nextElement();
				buf.append(", ").append(key).append("=");
				buf.append(req.getHeader(key));
			}
			
			en = req.getAttributeNames();
			buf.append(", ATTR:");
			while (en.hasMoreElements()) {
				String key = (String)en.nextElement();
				buf.append(", ").append(key).append("=");
				buf.append(req.getAttribute(key));
			}
			
			Cookie [] cookies = req.getCookies();
			buf.append(", COOKIES:");
			if (cookies != null) {
				for (int i=0; i<cookies.length; i++)
					buf.append(", ").append(cookies[i].getName()).append("=").append(cookies[i].getValue());
			}	
			
			buf.append(", ").append("uri=").append(req.getRequestURI()); 
		}
		buf.append("): ");
		return buf.toString();
	}
	
	private Locale getLocale(HttpServletRequest request) {
		//String client_encoding = request.getCharacterEncoding();
		Locale default_locale = request.getLocale();
		default_locale = default_locale == null ? Locale.US : default_locale;
		return default_locale;
		/* 
		Enumeration en = request.getLocales();
		ArrayList list = new ArrayList();
		while(en.hasMoreElements()) {
		  list.add(en.nextElement());
		}
		Locale[] enabled_locales = (Locale[]) list.toArray(new Locale[list.size()]);
		*/
	}

	/**
	 * Support for internationalization of exceptions. 
	 * Throw e.g. ajaxServlet_de.properties into the war CLASSPATH
	 * @param request
	 * @param key The key to lookup the text
	 * @param defaultVal The text to take if not found
	 * @param arg1 Can be null
	 * @param arg2 Can be null
	 * @return The localized text depending on the browser language
	 */
	private String getText(HttpServletRequest request, String key, String defaultVal, String arg1, String arg2) { 
	    Locale locale = getLocale(request);
	    ResourceBundle bundle = 
	         ResourceBundle.getBundle("ajaxServlet", locale);
    	String bundleValue = bundle.getString(key);
    	if (bundleValue == null) {
    		if (arg1 != null)
    			defaultVal = ReplaceVariable.replaceAll(defaultVal, "{0}", arg1);
    		if (arg2 != null)
    			defaultVal = ReplaceVariable.replaceAll(defaultVal, "{1}", arg2);
    		return defaultVal;
    	}
	    if (arg1 == null && arg2 == null)
	    	return bundleValue;
	    Object[] args;
	    if (arg1 != null && arg2 != null)
	    	args = new Object[]{arg1, arg2};
	    else
	    	args = new Object[]{arg1};
	    String result = MessageFormat.format(bundleValue, args);
	    return result;
	}

	private void cleanupSession(HttpServletRequest req) {
		try { 
			BlasterInstance bi = hasBlasterInstance(req);
			if (bi != null)
				bi.shutdown();
			
			HttpSession session = req.getSession(false);
			if (session != null)
				session.invalidate();
		} catch (Throwable e) {
			log(e.toString());
		}
	}

	/**
	 * Access a boolean parameter. 
	 * @param req
	 * @param key The URL key
	 * @param def The default if not found
	 * @return boolean support without value: "&admin&name=xy" will return true for admin
	 */
	public boolean getReqProp(HttpServletRequest req, String key, boolean def) {
		try {
			String tmp = req.getParameter(key);
			if ("".equals(tmp))
				return true; // boolean support without value: "&admin&name=xy"
			if (tmp != null)
				return Boolean.valueOf(tmp).booleanValue();
		}
		catch (Throwable e) {
			return def;
		}
		return def;
	}

	/**
	 * <pre>
	 * ServletSessionTimeout according to specification:
	 * The default timeout period for sessions is defined by the servlet container and
	 *	can be obtained via the getMaxInactiveInterval method of the HttpSession
	 *	interface. This timeout can be changed by the Developer using the
	 *	setMaxInactiveInterval method of the HttpSession interface. The timeout
	 *	periods used by these methods are defined in seconds. By definition, if the timeout
	 *	period for a session is set to -1, the session will never expire.
	 * </pre>
	 * <p>
	 * We have set the maxInactiveInterval to 1800 sec in web.xml (30 min):
	 * <p/>
	 * If no Ajax call arrives after the given timeout the servlet-session dies,
	 * as a browser user may choose to halt NMEA updates we must
	 * set this to a high enough value.
	 * <p/>
	 * The web.xml setting &lt;session-config>
	 *       <session-timeout>30</session-timeout>
	 *       &lt;/session-config>
	 * is overwritten by our maxInactiveInterval
	 * <p />
	 * Test:
	 * http://localhost:8080/watchee/ajax?ActionType=request&task=getRawPhoto&locationPictureId=5823&random=0.6236216717670112
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException,
			IOException {
		req.setCharacterEncoding("UTF-8"); // We know the browser sends UTF-8: tell the servlet engine
		String actionType = (String) req.getParameter("ActionType");
		StringWriter out = new StringWriter();
		Throwable throwable = null;
		BlasterInstance blasterInstance = null;
		boolean newBrowser = false;
		boolean forceLoad = getReqProp(req, "forceLoad", false);
		try {
			if (actionType == null) {
				log(getInfo(req, true, true) + "Missing ActionType, ignoring request");
				String msg = getText(req, "noActionType", "Invalid access", null, null);
				cleanupSession(req);
				throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT,
						"AjaxServlet", msg);
			}
			
			if (isBlockedIP(req.getRemoteAddr())) {
				log(getInfo(req, true, true) + "Blocked IP " + req.getRemoteAddr() + ", ignoring request");
				String msg = getText(req, "isBlocked", "Access is currently not allowed", null, null); 
				cleanupSession(req);
				throw new XmlBlasterException(Global.instance(), ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED,
						"AjaxServlet", msg);
			}
	
			//String host = req.getRemoteAddr();
			//String ME = "AjaxServlet.doGet(" + host + "): ";
			//log.info("ENTERING DOGET ....");
			if (forceLoad)
				log(getInfo(req) + "forceLoad=" + forceLoad);

			if (req.getSession(false) == null) {
				if (maxUserSessionsReached(req)) { // "?admin=true" will ignore this test
					log(getInfo(req, true, true) + "Max user sessions = " + getMaxUserSessions() + " reached, please try again later");
					String msg = getText(req, "maxUserSessionReached", "Max user sessions = {0} reached, please try again later",
							""+getMaxUserSessions(), null); 
					throw new XmlBlasterException(Global.instance(), ErrorCode.USER_CONFIGURATION_MAXSESSION,
							"AjaxServlet", msg);
				}
				HttpSession session = req.getSession(true);
				session.setMaxInactiveInterval(this.maxInactiveInterval);
				newBrowser = true;
				log(getInfo(req, true, false) + "New browser arrived");
			}
		/*
		}
		catch (XmlBlasterException ex) {
			res.setContentType("text/xml; charset=UTF-8");
			PrintWriter backToBrowser = res.getWriter();
			StringBuffer sb = new StringBuffer(512);
			sb.append("<xmlBlasterResponse>");
			sb.append(ex.toXml());
			sb.append("</xmlBlasterResponse>");
			backToBrowser.write(sb.toString());
			backToBrowser.close();
			return;
		}
		*/

		// set header field first
		//res.setContentType("text/plain; charset=UTF-8");
		// xml header don't like empty response, so send at least "<void/>
		//res.setContentType("text/xml; charset=UTF-8");
			blasterInstance = getBlasterInstance(req);
			boolean admin = getReqProp(req, "admin", false);
			if (admin) blasterInstance.setAdmin(admin); // can be passed on connect()

			if (actionType.equals("xmlScript")) {
				String xmlScript64 = (String) req.getParameter("xmlScriptBase64");
				String xmlScriptPlain = (String) req.getParameter("xmlScriptPlain");
				byte[] raw = null;
				if (xmlScript64 != null && xmlScriptPlain != null) {
					String errTxt = "You can not set both 'xmlScriptBase64' and 'xmlScriptPlain'";
					throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, "AjaxServlet", errTxt);
				}
				String xmlScript = xmlScriptPlain;
				if (xmlScript64 != null) {
					// the url encoder has somewhere changed my + to blanks
					// you must send this by invoking encodeURIComponent(txt) on the javascript side.
					xmlScript64 = ReplaceVariable.replaceAll(xmlScript64, " ", "+");
					raw = Base64.decode(xmlScript64);
					xmlScript = new String(raw, "UTF-8");
				} else if (xmlScriptPlain != null) {
					raw = xmlScriptPlain.getBytes();
				} else {
					String errTxt = "You must choose one of 'xmlScriptBase64' and 'xmlScriptPlain' since you choosed 'xmlScript'";
					throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, "AjaxServlet", errTxt);
				}
				if (newBrowser && xmlScript.indexOf("<connect")==-1)
					throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, "AjaxServlet", "The first call must contain a connect markup");
				blasterInstance.execute(raw, xmlScript, out);
				return;
			}

			// A request/reply with direct binary streaming of the MsgUnit content
			// back to the browser, can be used e.g. to access backend DB pictures
			// Todo: Add a variant with service response, e.g. &binary=false
			// "/watchee/ajax?ActionType=request&task=getRawPhoto&data=4517&timeout=5000&maxEntries=10"
			else if (actionType.equals("request")) { // request-response pattern, blocking for repsonse
				// "image/gif" "image/jpeg" "image/bmp" "image/x-png" "application/x-msdownload" "video/avi" "video/mpeg"
				long timeout = (req.getParameter("timeout") == null) ? 8000 : Long.valueOf(
						(String) req.getParameter("timeout")).longValue();
				int maxEntries = (req.getParameter("maxEntries") == null) ? 1 : Integer.valueOf(
						(String) req.getParameter("maxEntries")).intValue();
				String topicId = (req.getParameter("topicId") == null) ? "service" : (String) req
						.getParameter("topicId");
				String content = req.getParameter("content");
				String qos = (req.getParameter("qos") == null) ? "<qos/>" : (String) req
						.getParameter("qos");
				if (content == null) {
					String task = (String) req.getParameter("task"); // "getRawPhoto"
					String data = (String) req.getParameter("data"); // "locationPictureId"
					String serviceName = (String) req.getParameter("serviceName");
					if (serviceName == null)
						serviceName = "track";
					String taskType = (String) req.getParameter("taskType");
					if (taskType == null)
						taskType = "named";
					// Service markup: sc=serviceCollection, s=service, p=property
					content = "<sc>" + " <s>" + "  <p k='serviceName'>" + serviceName + "</p>"
							+ "  <p k='taskType'>" + taskType + "</p>" + "  <p k='task'>" + task
							+ "</p>" + "  <p k='data'>" + data + "</p>" + " </s>" + "</sc>";
				}
				MsgUnit msgUnit = new MsgUnit("<key oid='" + topicId + "'/>", content, qos);
				log(getInfo(req) + "Sending request to " + topicId + ": " + content);
				MsgUnit[] msgUnitArr = blasterInstance.getXmlBlasterAccess().request(msgUnit,
						timeout, maxEntries);
				if (msgUnitArr.length > 0) {
					String contentMime = msgUnitArr[0].getQosData().getClientProperty(
							"contentMime", "image/jpeg");
					res.setContentType(contentMime);
					log(getInfo(req) + "Returning request/reply image '" + contentMime + "' length="
							+ msgUnitArr[0].getContent().length);
					out = null;
					ServletOutputStream binOut = res.getOutputStream();
					byte[] buff = msgUnitArr[0].getContent();
					binOut.write(buff, 0, buff.length);
					binOut.flush();
					binOut.close();
				}
				return;
			}

			/* see XbAccess.js/watchee.js for an example:
			In the mode "updatePoll" this script polls every 8000 millis for update
			and the servlet returns directly if nothing is available, this is suboptimal
			as we have a delay of up to 8 seconds.
			In the mode "updatePollBlocking" we don't poll but the servlet blocks our call
			and immediately returns when update messages arrive.
			To prevent from e.g. proxy timeouts the servlet returns after 15sec and we immediately
			poll again.
			*/
			// "timeout" and "numEntries" is only evaluated for "updatePollBlocking"
			boolean updatePoll = actionType.equals("updatePoll");
			boolean updatePollBlocking = actionType.equals("updatePollBlocking");
			if (updatePoll || updatePollBlocking) {
				
				if (newBrowser) // The initial call should not be the AjaxPoller
					throw new XmlBlasterException(Global.instance(), ErrorCode.USER_NOT_CONNECTED, "AjaxServlet", "Connection is lost, please login again");
				
				boolean onlyContent = get(req, "onlyContent", false);
				long timeout = get(req, "timeout", (updatePoll) ? 0L : 15000L);
				int numEntries = get(req, "numEntries", -1);
				int count = blasterInstance.sendUpdates(out, onlyContent, numEntries, timeout);
				if (count == 0) { // watchee hack
					if (newBrowser || forceLoad) {
						String initGps = (String) req.getParameter("initGps");
						if (initGps != null && "true".equalsIgnoreCase(initGps.trim())) {
							String tmp = blasterInstance.getStartupPos();
							if (tmp.length() > 0) {
								out.write(tmp);
								log(getInfo(req) + tmp);
							}
						}
					}
				} else
					log(getInfo(req) + " Sending " + count + " received update messages to browser");
				return;
			}

			if (actionType.equals("plainGet")) {
				blasterInstance.plainGet(req, res, out);
			}

			// log(getInfo(req)+"Ignoring identical");
		} catch (XmlBlasterException e) {
			log("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
					+ e.toString());
			// if (newBrowser || forceLoad)
			// out.write(blasterInstance.getStartupPos());
			throwable = e;
		} catch (Throwable e) {
			log("newBrowser=" + newBrowser + " forceLoad=" + forceLoad + ": "
							+ e.toString());
			e.printStackTrace();
			throwable = e;
		} finally {
			if (out != null) {
				//res.setContentType("text/xml");
			    //res.setCharacterEncoding("UTF-8");
				res.setContentType("text/xml; charset=UTF-8");
				// For HTML: out.println("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\" />");
				PrintWriter backToBrowser = res.getWriter();
				if (out.getBuffer().length() > 0)
					log("Sending now '" + out.getBuffer().toString() + "'");
				if (out.getBuffer().length() > 0)
					backToBrowser.write(out.getBuffer().toString());
				else {
					if (throwable != null) {
						StringBuffer sb = new StringBuffer(256);
						sb.append("<xmlBlasterResponse>");
						XmlBlasterException ex = (XmlBlasterException)throwable;
						if (throwable instanceof XmlBlasterException) {
							ex = (XmlBlasterException)throwable;
							if (ex.isErrorCode(ErrorCode.INTERNAL_STOP)) { // XmlScript Sax Parser stopped
								Throwable th = ex.getEmbeddedException();
								if (th != null && th instanceof XmlBlasterException) {
									ex = (XmlBlasterException)th;
									//ex.changeErrorCode(((XmlBlasterException)th).getErrorCode());
								}
							}
						}
						else {
							ex = new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_UNKNOWN, "AjaxServlet", "Unknown problem", throwable);
						}
						sb.append(ex.toXml());
						sb.append("</xmlBlasterResponse>");
						if (blasterInstance != null)
							blasterInstance.shutdown();
						/*
						<xmlBlasterResponse>
						  <exception errorCode='user.illegalArgument'>
						    <class>org.xmlBlaster.util.XmlBlasterException</class>
						    <isServerSide>true</isServerSide>
						    ...
						*/
						backToBrowser.write(sb.toString());
					}
					else {
						backToBrowser.write("<void/>"); // text/xml needs at least a root tag!
					}
				}
				backToBrowser.close();
			}
		}
	}

	public boolean get(HttpServletRequest req, String key, boolean defaultVal) {
		String value = (String) req.getParameter(key);
		if (value == null)
			return defaultVal;
		return "true".equalsIgnoreCase(value.trim());
	}

	public long get(HttpServletRequest req, String key, long defaultVal) {
		String value = (String) req.getParameter(key);
		if (value == null)
			return defaultVal;
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	public int get(HttpServletRequest req, String key, int defaultVal) {
		String value = (String) req.getParameter(key);
		if (value == null)
			return defaultVal;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	public void add(String sessionId, BlasterInstance blasterInstance) {
		synchronized (this.blasterInstanceMap) {
			//Map/*<String, BlasterInstance>*/blasterInstanceMap
			this.blasterInstanceMap.put(sessionId, blasterInstance);
		}
	}
	
	public void removeBlasterInstance(String sessionId) {
		synchronized (this.blasterInstanceMap) {
			this.blasterInstanceMap.remove(sessionId);
		}
	}

	/**
	 * @param req
	 * @return null if not known
	 * @throws XmlBlasterException
	 */
	private BlasterInstance hasBlasterInstance(HttpServletRequest req) throws XmlBlasterException {
		synchronized (this.blasterInstanceMap) {
			HttpSession session = req.getSession(false);
			if (session == null) return null;
			return (BlasterInstance) this.blasterInstanceMap.get(session.getId());
		}
	}

	private synchronized BlasterInstance getBlasterInstance(HttpServletRequest req)
			throws XmlBlasterException {
		// Map blasterInstanceMap =
		// (Map)session.getAttribute("blasterInstanceMap");
		BlasterInstance blasterInstance = null;
		synchronized (this.blasterInstanceMap) {
			blasterInstance = (BlasterInstance) this.blasterInstanceMap.get(req.getSession()
					.getId());
			if (blasterInstance != null)
				return blasterInstance;

			blasterInstance = new BlasterInstance(this, req);
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
	
	public int getNumBlasterInstances() {
		synchronized (this.blasterInstanceMap) {
			return this.blasterInstanceMap.size();
		}
	}

	public BlasterInstance[] getBlasterInstances() {
		synchronized (this.blasterInstanceMap) {
			return (BlasterInstance[]) this.blasterInstanceMap.values().toArray(
					new BlasterInstance[this.blasterInstanceMap.size()]);
		}
	}

	public synchronized void destroy() {
        Global.instance().unregisterMBean(this.mbeanHandle);
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

	public ContextNode getContextNode() {
		return contextNode;
	}

	public String cleanupOldSessionsKeepGivenAmount(int maxSessions, String notificationText, final boolean creationTimestamp) {
		if (maxSessions >= getNumBlasterInstances())
			return "Nothing destroyed, given maxSessions=" + maxSessions + " current sessions=" + getNumBlasterInstances();
		ArrayList list = null;
		synchronized (this.blasterInstanceMap) {
			list = new ArrayList(this.blasterInstanceMap.values());
		}
		
		Collections.sort(list, new Comparator(){
			public int compare( Object a, Object b )
            {
				BlasterInstance one = (BlasterInstance)a;
				BlasterInstance two = (BlasterInstance)b;
				if (creationTimestamp)
					return one.getCreationTimestamp().compareToIgnoreCase(two.getCreationTimestamp());
				else
					return one.getLastAccessedTimestamp().compareToIgnoreCase(two.getLastAccessedTimestamp());
            }
        });
		int count = 0;
		int countAdmin = 0;
		for (int i=0; i<list.size(); i++) {
			BlasterInstance bi = (BlasterInstance)list.get(i);
			if (creationTimestamp)
				log("Processing " + bi.getRelativeName() + " lastAccessed=" + bi.getLastAccessedTimestamp() + " admin=" + bi.isAdmin());
			else
				log("Processing " + bi.getRelativeName() + " created=" + bi.getCreationTimestamp() + " admin=" + bi.isAdmin());
			if (bi.isAdmin()) {
				countAdmin++;
				continue; // don't remove admin accounts
			}
			if ((list.size() - count) <= maxSessions) {
				log("Stopping now");
				break;
			}
			count++;
			bi.shutdown();
		}
		// TODO: How to transport notificationText back to browser??
		return "Destroyed " + count + " sessions, current = " + getNumBlasterInstances() + " (" + countAdmin + " admin=true sessions where untouched)";
	}
	
	public boolean isBlockedIP(String ip) {
		if (ip == null || ip.length() < 1) return false;
		synchronized(this.blockedIPs) {
			return this.blockedIPs.contains(ip);
		}
	}

	public void addBlockedIP(String ip) {
		if (ip == null || ip.length() < 1) return;
		synchronized(this.blockedIPs) {
			this.blockedIPs.add(ip);
		}
	}

	public void clearBlockedIPs() {
		synchronized(this.blockedIPs) {
			this.blockedIPs.clear();
		}
	}

	public String[] getBlockedIPs() {
		synchronized(this.blockedIPs) {
			return (String[])this.blockedIPs.toArray(new String[this.blockedIPs.size()]);
		}
	}

	public int getMaxUserSessions() {
		return maxUserSessions;
	}

	public void setMaxUserSessions(int maxUserSessions) {
		this.maxUserSessions = maxUserSessions;
	}
	
	public boolean maxUserSessionsReached(HttpServletRequest req) {
		boolean admin = getReqProp(req, "admin", false);
		if (admin)
			return false;
		return getNumBlasterInstances() >= getMaxUserSessions();
	}
}

/**
 * Detect when a servlet session dies (with tomcat typically after one hour). 
 * Causes an object to be notified when it is bound to or unbound from a session.
 * The object is notified by an HttpSessionBindingEvent object.
 * This may be as a result of a servlet programmer explicitly unbinding an attribute from a session,
 * due to a session being invalidated, or due to a session timing out. 
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2007
 */
class SessionTimeoutListener implements HttpSessionBindingListener {
	private static Logger log = Logger.getLogger(SessionTimeoutListener.class.getName());

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

