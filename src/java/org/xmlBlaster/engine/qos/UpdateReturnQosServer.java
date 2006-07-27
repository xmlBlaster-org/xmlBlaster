/*------------------------------------------------------------------------------
Name:      UpdateReturnQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;

/**
 * Handling the returned QoS (quality of service) of a update() call. 
 * <p />
 * The server calls back the client and the client return an xml string
 * which is parsed by this class. 
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK'/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 */
public final class UpdateReturnQosServer
{
   private final StatusQosData statusQosData;

   /**
    * Constructor which parses XML string.
    */
   public UpdateReturnQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    */
   public UpdateReturnQosServer(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
   }

   /**
    * Access the raw data object, usually you shouldn't do it.
    * @return The raw data object
    */
   public StatusQosData getData() {
      return statusQosData;
   }

   /**
    * Access the state of message.
    * @return OK (Other values are not yet supported)
    */
   public final String getState() {
      return this.statusQosData.getState();
   }

   /**
    * @see #toXml(String)
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    * @return The XML representation
    */
   public final String toXml(String extraOffset) {
      return this.statusQosData.toXml(extraOffset, (Properties)null);
   }

   public final String toString() {
      return toXml(null);
   }

   /**
    * Setter for the exception 
    */
   public void setException(Throwable ex) {
      this.statusQosData.setException(ex);
   }
   
   public Throwable getException() {
      return this.statusQosData.getException();
   }
   


}

