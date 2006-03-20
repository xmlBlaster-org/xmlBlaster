/*------------------------------------------------------------------------------
Name:      EraseQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * Handling of erase() quality of services in the server core.
 * <p>
 * This decorator hides the real qos data object and gives us a server specific view on it. 
 * </p>
 * <p>
 * QoS Informations sent from the client to the server via the erase() method<br />
 * They are needed to control xmlBlaster
 * </p>
 * <p>
 * For the xml representation see QueryQosSaxFactory.
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 */
public final class EraseQosServer
{
   private final QueryQosData queryQosData;

   /**
    * Constructor which accepts a raw data struct. 
    */
   public EraseQosServer(Global glob, QueryQosData queryQosData) {
      this.queryQosData = queryQosData;
   }

   /**
    * Constructs the specialized quality of service object for a erase() call.
    * @param the XML based ASCII string
    */
   public EraseQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.queryQosData = glob.getQueryQosFactory().readObject(xmlQos);
   }

   /**
    * Access the internal data struct
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   public boolean getWantNotify() {
      return this.queryQosData.getWantNotify();
   }

   /**
    * @param forceDestroy
    */
   public void setForceDestroy(boolean forceDestroy) {
      this.queryQosData.setForceDestroy(forceDestroy);
   }

   /**
    * @return for erase behavior: destroy even if we are referenced?
    */
   public boolean getForceDestroy() {
      return this.queryQosData.getForceDestroy();
   }

   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      return this.queryQosData.toXml(extraOffset);
   }
}
