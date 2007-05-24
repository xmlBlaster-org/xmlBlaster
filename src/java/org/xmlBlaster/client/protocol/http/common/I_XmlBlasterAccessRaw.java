/*------------------------------------------------------------------------------
 Name:      I_XmlBlasterRaw.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.common;

import java.util.Hashtable;

/**
 * Interface for XmlBlaster, the supported methods on applet client side.
 * <p>
 * All returned parameters are hold in Hashtables, to access the different
 * key/QoS elements use JXPath syntax, see the API references below for more
 * details.
 * </p>
 * 
 * @see org.xmlBlaster.util.qos.MsgQosData#toJXPath()
 * @see org.xmlBlaster.util.key.MsgKeyData#toJXPath()
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html">The
 *      interface requirement</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl"
 *      target="others">CORBA xmlBlaster.idl</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface I_XmlBlasterAccessRaw {
   public final static String CONNECT_NAME = "connect";

   public final static String PUBLISH_NAME = "publish";

   public final static String GET_NAME = "get";

   public final static String SUBSCRIBE_NAME = "subscribe";

   public final static String UNSUBSCRIBE_NAME = "unSubscribe";

   public final static String ERASE_NAME = "erase";

   public final static String DISCONNECT_NAME = "disconnect";

   public final static String EXCEPTION_NAME = "exception";

   public final static String CREATE_SESSIONID_NAME = "dummyToCreateASessionId";

   public final static String PONG_NAME = "pong";

   public final static String PING_NAME = "ping";

   public final static String UPDATE_NAME = "update";

   /**
    * Access the unique counter of this object instance for logging.
    */
   public String getInstanceId();

   /**
    * Send a xml script request to xmlBlaster. You need to call connect() first!
    * 
    * @return xml script returned
    * @see <a
    *      href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">client.script
    *      requirement</a>
    */
   public String sendXmlScript(String xmlRequest) throws Exception;

   /**
    * Connect to xmlBlaster.
    * 
    * @param qos
    *           If your qos is null the APPLET PARAMs will be checked
    *           for"xmlBlaster/loginName" and "xmlBlaster/passwd"<br />
    *           If your qos is "<qos/>" the servlet will choose its configured
    *           connectQoS (take care on security issues!)<br />
    *           If qos is not null and pre-filled with authentication
    *           informations it will be used to authenticate at xmlBlaster<br />
    * @param callback
    *           Where to send asynchronous callback messages.
    * 
    * @return never null TODO!!!: return JXPath Hashtable for easier parameter
    *         access Currently the ConnectQos.toXml() is returned
    */
   public String connect(String qos, I_CallbackRaw callback) throws Exception;

   /**
    * Check wether we are connected
    */
   public boolean isConnected();

   /**
    * Returns "/qos/state/@info"="OK" if communication from servlet to
    * xmlBlaster is OK <br />
    * Returns "/qos/state/@info"="POLLING" if communication from servlet to
    * xmlBlaster is down but polling <br />
    * Returns "/qos/state/@info"="DEAD" if communication from servlet to
    * xmlBlaster is permanently lost
    * 
    * @return never null, contains QoS in XJPath format
    */
   public Hashtable ping(java.lang.String qos) throws Exception;

   /**
    * @return never null, contains QoS in XJPath format
    */
   public Hashtable subscribe(java.lang.String xmlKey, java.lang.String qos)
         throws Exception;

   /**
    * @return never null, contains keys and QoS in XJPath format
    */
   public Msg[] get(java.lang.String xmlKey, java.lang.String qos)
         throws Exception;

   /**
    * @return never null, contains QoS in XJPath format
    */
   public Hashtable[] unSubscribe(java.lang.String xmlKey, java.lang.String qos)
         throws Exception;

   /**
    * @return never null, contains QoS in XJPath format
    */
   public Hashtable publish(java.lang.String xmlKey, byte[] content,
         java.lang.String qos) throws Exception;

   /**
    * @return never null, contains QoS in XJPath format
    */
   public Hashtable[] erase(java.lang.String xmlKey, java.lang.String qos)
         throws Exception;

   public void disconnect(String qos);

   /**
    * Register to receive the logging output
    */
   public void setLogListener(I_Log logListener);

   /**
    * Log to the logListener or to the java console of the browser if
    * logListener is null.
    * 
    * @param location
    *           Your class and/or method name
    * @param leve
    *           One of "ERROR", "WARN", "INFO", "DEBUG"
    * @param text
    *           The text to log
    */
   public void log(String location, String level, String text);

   /**
    * Get a list of all PARAM in the HTML file following our convention.
    * <p>
    * All param names starting with "servlet/" are passed to the servlet. They
    * must start with "servlet/xyz=someValue". The "servlet/" will be stripped
    * away and in the web-servlet will arrive "xyz=someValue". The key/values
    * are send in the URL.
    * </p>
    * <p>
    * As the applet class has no getAllParameters() method we expect a PARAM
    * <i>deliveredParamKeys</i> which contains a list of all delivered PARAM in
    * the HTML page:
    * </p>
    * 
    * <pre>
    *    &lt;applet ...&gt;
    *       &lt;param name=&quot;deliveredParamKeys&quot; value=&quot;protocol,anotherKey,Key3&quot;&gt;
    *       &lt;param name=&quot;protocol&quot; value=&quot;SOCKET&quot;&gt;
    *       &lt;param name=&quot;anotherKey&quot; value=&quot;someValue&quot;&gt;
    *       &lt;param name=&quot;Key3&quot; value=&quot;xxx&quot;&gt;
    *    &lt;/applet&gt;
    * </pre>
    * 
    * <p>
    * It may contain additional customized properties from the applet
    * programmer.
    * </p>
    * 
    * @return The found parameters
    */
   public Hashtable getHtmlProperties();

   /**
    * Creates a connection to the specified servlet.
    * 
    * @param urlString
    * @return
    * @throws Exception
    */
   public I_Connection createConnection(String urlString) throws Exception;

}
