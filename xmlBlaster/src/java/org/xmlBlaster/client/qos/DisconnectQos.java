/*------------------------------------------------------------------------------
Name:      DisconnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.java,v 1.2 2003/03/24 16:13:07 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;

/**
 * This class encapsulates the qos of a logout() or disconnect()
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>logout</b> qos could look like this:<br />
 * <pre>
 *  &lt;qos>
 *    &lt;deleteSubjectQueue>true&lt;/deleteSubjectQueue>
 *    &lt;clearSessions>false&lt;/clearSessions>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 */
public class DisconnectQos
{
   private final Global glob;
   private final DisconnectQosData disconnectQosData;
   private I_MsgSecurityInterceptor securityInterceptor;

   public DisconnectQos(Global glob) {
      this(glob, null);
   }

   /**
    * Constructor for internal use. 
    * @param queryQosData The struct holding the data
    */
   public DisconnectQos(Global glob, DisconnectQosData disconnectQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.disconnectQosData = (disconnectQosData==null) ? new DisconnectQosData(this.glob) : disconnectQosData;
   }

   /** @deprecated */
   public DisconnectQos() {
      this(null, null);
   }

   /**
    * Access the wrapped data holder
    */
   public DisconnectQosData getData() {
      return this.disconnectQosData;
   }

   /**
    * Return true if subject queue shall be deleted with last user session
    * @return true;
    */
   public boolean deleteSubjectQueue() {
      return this.disconnectQosData.deleteSubjectQueue();
   }

   /**
    * @param true if subject queue shall be deleted with last user session logout
    */
   public void deleteSubjectQueue(boolean del) {
      this.disconnectQosData.deleteSubjectQueue(del);
   }

   /**
    * Return true if we shall kill all other sessions of this user on logout (defaults to false). 
    * @return false
    */
   public boolean clearSessions() {
      return this.disconnectQosData.clearSessions();
   }

   /**
    * @param true if we shall kill all other sessions of this user on logout (defaults to false). 
    */
   public void clearSessions(boolean del) {
      this.disconnectQosData.clearSessions(del);
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return this.disconnectQosData.toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.disconnectQosData.toXml();
   }

   /**
    * Access the security interceptor to encrypt/decrypt. 
    * @return I_MsgSecurityInterceptor plugin or null
    */
   public I_MsgSecurityInterceptor getSecurityInterceptor() {
      return this.securityInterceptor;
   }

   /**
    * Access the security interceptor to encrypt/decrypt. 
    * @return I_MsgSecurityInterceptor
    */
   public void setSecurityInterceptor(I_MsgSecurityInterceptor securityInterceptor) {
      this.securityInterceptor = securityInterceptor;
   }
}
