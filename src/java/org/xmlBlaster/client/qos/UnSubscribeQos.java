/*------------------------------------------------------------------------------
Name:      UnSubscribeQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.def.MethodName;

/**
 * This class encapsulates the QoS of an unSubcribe() request. 
 * <p />
 * A full specified <b>unSubcribe</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">unSubscribe interface</a>
 */
public final class UnSubscribeQos
{
   private String ME = "UnSubscribeQos";
   private final Global glob;
   private final QueryQosData queryQosData;

   /**
    * Constructor for default qos (quality of service).
    */
   public UnSubscribeQos(Global glob) {
      this(glob, null); 
   }

   /**
    * Constructor for internal use. 
    * @param queryQosData The struct holding the data
    */
   public UnSubscribeQos(Global glob, QueryQosData queryQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.queryQosData = (queryQosData==null) ? new QueryQosData(this.glob, this.glob.getQueryQosFactory(), MethodName.UNSUBSCRIBE) : queryQosData;
      this.queryQosData.setMethod(MethodName.UNSUBSCRIBE);
   }

   /**
    * Access the wrapped data holder
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /**
    * Mark the unSubscribe request to be persistent. 
    * <p>
    * NOTE: The request is only persistent in the client side
    * queue if we are polling for xmlBlaster.
    * </p>
    */
   public void setPersistent(boolean persistent) {
      this.queryQosData.setPersistent(persistent);
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

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.queryQosData.toXml();
   }

   public String toXml(Properties props) {
      return this.queryQosData.toXml((String)null, props);
   }
}
