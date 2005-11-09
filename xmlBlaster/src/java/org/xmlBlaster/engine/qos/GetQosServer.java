/*------------------------------------------------------------------------------
Name:      GetQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.QuerySpecQos;

/**
 * Handling of get() quality of services in the server core.
 * <p>
 * This decorator hides the real qos data object and gives us a server specific view on it. 
 * </p>
 * <p>
 * QoS Informations sent from the client to the server via the get() method<br />
 * They are needed to control xmlBlaster
 * </p>
 * <p>
 * For the xml representation see QueryQosSaxFactory.
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 */
public final class GetQosServer
{
   private final QueryQosData queryQosData;

   /**
    * Constructor which accepts a raw data struct. 
    */
   public GetQosServer(Global glob, QueryQosData queryQosData) {
      this.queryQosData = queryQosData;
   }

   /**
    * Constructs the specialized quality of service object for a get() call.
    * @param the XML based ASCII string
    */
   public GetQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.queryQosData = glob.getQueryQosFactory().readObject(xmlQos);
   }

   /**
    * Access the internal data struct
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /**
    * Return the get filters or null if none is specified. 
    */
   public final AccessFilterQos[] getAccessFilterArr() {
      return this.queryQosData.getAccessFilterArr();
   }

   /**
    * Return the get querySpecs or null if none is specified. 
    */
   public final QuerySpecQos[] getQuerySpecArr() {
      return this.queryQosData.getQuerySpecArr();
   }

   /**
    * Query the message history
    */
   public HistoryQos getHistoryQos() {
      return this.queryQosData.getHistoryQos();
   }

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   public boolean getWantLocal() {
      return this.queryQosData.getWantLocal();
   }

   /**
    * @return false: Don't send me the meta information of a message key
    */
   public boolean getWantMeta() {
      return this.queryQosData.getWantMeta();
   }

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   public boolean getWantContent() {
      return this.queryQosData.getWantContent();
   }

   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      return this.queryQosData.toXml(extraOffset);
   }
}
