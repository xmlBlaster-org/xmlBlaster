/*------------------------------------------------------------------------------
Name:      EraseQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * This class encapsulates the QoS of an erase() request. 
 * <p />
 * A full specified <b>erase</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;erase forceDestroy='false'/>
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html">erase interface</a>
 */
public final class EraseQos
{
   private String ME = "EraseQos";
   private final Global glob;
   private final QueryQosData queryQosData;

   /**
    * Constructor for default qos (quality of service).
    */
   public EraseQos(Global glob) {
      this(glob, null);
   }

   /**
    * Constructor for internal use. 
    * @param queryQosData The struct holding the data
    */
   public EraseQos(Global glob, QueryQosData queryQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.queryQosData = (queryQosData==null) ? new QueryQosData(this.glob, this.glob.getQueryQosFactory(), MethodName.ERASE) : queryQosData;
      this.queryQosData.setMethod(MethodName.ERASE);
   }

   /**
    * Access the wrapped data holder
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /*
    * Notify the subscribers on erase. 
    * <p/>
    * Defaults to true. 
    * NOTE: This is not supported, currently only the subscriber decides if
    * he wants notification.
   public void notifySubscribers(boolean notify) {
      this.queryQosData.setWantNotify(notify);
   }
   */

   /**
    * Defaults to false: If a topic is still referenced by callback messages
    * it will be not erased immediately but we wait until all pending messages are delivered. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">engine.message.lifecycle requirement</a>
    */
   public void setForceDestroy(boolean forceDestroy) {
      this.queryQosData.setForceDestroy(forceDestroy);
   }

   /**
    * Mark the erase request to be persistent. 
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
}
