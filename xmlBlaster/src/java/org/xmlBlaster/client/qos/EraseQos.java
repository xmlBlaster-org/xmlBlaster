/*------------------------------------------------------------------------------
Name:      EraseQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * This class encapsulates the QoS of an erase() request. 
 * <p />
 * A full specified <b>erase</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;!-- The subscribers shall not be notified when this message is destroyed -->
 *   &lt;notify>false&lt;/notify> <!-- currently not implemented -->
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
      this.queryQosData = (queryQosData==null) ? new QueryQosData(this.glob, this.glob.getQueryQosFactory()) : queryQosData;
   }

   /**
    * Access the wrapped data holder
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /**
    * NOT IMPLEMENTED
    * @param notify true - notify subscribers that message is erased (default is true)
    */
   public void setWantNotify(boolean notify) {
      this.queryQosData.setWantNotify(notify);
   }

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
