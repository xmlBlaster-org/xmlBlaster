/*------------------------------------------------------------------------------
Name:      GetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.QuerySpecQos;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.def.MethodName;

/**
 * This class encapsulates the QoS (quality of service) of a get() request. 
 * <p />
 * A full specified <b>get</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;meta>false&lt;/meta>       &lt;!-- Don't return me the xmlKey meta data -->
 *   &lt;content>false&lt;/content> &lt;!-- Don't return me the content data (notify only) -->
 *   &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>
 *                                  &lt;!-- MIME access filters plugin -->
 *   &lt;history numEntries='20'/>  &lt;!-- Default is to deliver the current entry (numEntries='1'), '-1' deliver all -->
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html">get interface</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">MIME access filter requirement</a>
 */
public final class GetQos
{
   private String ME = "GetQos";
   private final Global glob;
   private final QueryQosData queryQosData;
   /**
    * ClientProperty key to avoid exception if xmlBlaster.get() call is client side queued and sent async
    * 2014-10-06 marcel
    */
   public static final String CP_ASYNC_GET_ALLOWED = "__asyncGetAllowed";
   /**
    * If __asyncGetAllowed is true and xmlBlaster client is in async mode (for example polling)
    * the timeout to wait for the server response, defaults to 10sec
    */
   public static final String CP_ASYNC_GET_TIMEOUT_MILLIS = "__asyncGetTimeoutMillis";

   /**
    * Constructor for default qos (quality of service).
    */
   public GetQos(Global glob) {
      this(glob, null);
   }

   /**
    * Constructor for internal use. 
    * @param queryQosData The struct holding the data
    */
   public GetQos(Global glob, QueryQosData queryQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.queryQosData = (queryQosData==null) ? new QueryQosData(this.glob, this.glob.getQueryQosFactory(), MethodName.GET) : queryQosData;
      this.queryQosData.setMethod(MethodName.GET);
   }

   /**
    * Access the wrapped data holder
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /*
    * Shall key meta information be delivered?
   public void setWantMeta(String meta) {
      this.queryQosData.setWantMeta(meta);
   }
    */

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * TODO: Implement in server!!!
    */
   public void setWantContent(boolean content) {
      this.queryQosData.setWantContent(content);
   }

   /**
    * Adds your supplied get filter. 
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">The access filter plugin requirement</a>
    */
   public void addAccessFilter(AccessFilterQos filter) {
      this.queryQosData.addAccessFilter(filter);
   }

   /**
    * Adds your supplied get querySpec. 
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/query.plugin.html">The query plugin requirement</a>
    */
   public void addQuerySpec(QuerySpecQos querySpec) {
      this.queryQosData.addQuerySpec(querySpec);
   }

   /**
    * Query historical messages. 
    */
   public void setHistoryQos(HistoryQos historyQos) {
      this.queryQosData.setHistoryQos(historyQos);
   }

   /**
    * Sets a client property (an application specific property) to the
    * given value
    * @param key
    * @param value
    */
   public void addClientProperty(String key, Object value) {
      this.queryQosData.addClientProperty(key, value);
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.queryQosData.getClientProperty(key);
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return this.queryQosData.toXml();
   }

   public String toXml() {
      return toXml((Properties)null);
   }
   
   /**
    * Converts the data into a valid XML ASCII string.
    * @param props Formatting control, see Constants.TOXML_*
    * @return An XML ASCII string
    */
   public String toXml(Properties props) {
      return this.queryQosData.toXml((String)null, props);
   }

   /** For testing: java org.xmlBlaster.client.qos.GetQos */
   public static void main(String[] args) {
      Global glob = new Global(args);
      try {
         GetQos qos = new GetQos(glob);
         qos.setWantContent(false);
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "3.2", new Query(glob, "a<10")));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
