/*------------------------------------------------------------------------------
Name:      DisconnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.java,v 1.3 2003/05/06 16:06:26 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.util.property.PropBoolean;

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

   private PropBoolean clearClientQueue = new PropBoolean(true);
   private boolean shutdownDispatcher = true;
   private boolean shutdownCbServer = true;

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

   /**
    * If there are tail back messages in the client side queue, what to do with them. 
    * <p>
    * Controls client side behavior.
    * </p>
    * @param clearClientQueue true Removes all entries of the client side tailback queue<br />
    *                         false Keep persistent entries in client side queue, will be sent on next connect
    *                               of the same client with the same public session ID.
    */
   public void clearClientQueue(boolean clearClientQueue) {
      this.clearClientQueue.setValue(clearClientQueue);
   }

   /**
    * @see #clearClientQueue(boolean)
    */
   public boolean clearClientQueue() {
      return this.clearClientQueue.getValue();
   }

   public PropBoolean getClearClientQueueProp() {
      return this.clearClientQueue;
   }

   /**
    * Shutdown the client side dispatcher framework on disconnect, which
    * includes the low level connection like CORBA. 
    * <p>
    * Controls client side behavior.
    * </p>
    * @param shutdownDispatcher true is default
    */
   public void shutdownDispatcher(boolean shutdownDispatcher) {
      this.shutdownDispatcher = shutdownDispatcher;
   }

   /**
    * @return Defaults to true
    */
   public boolean shutdownDispatcher() {
      return this.shutdownDispatcher;
   }

   /**
    * Shutdown the client side callback server on disconnect. 
    * <p>
    * Controls client side behavior.
    * </p>
    * @param shutdownCbServer true is default
    */
   public void shutdownCbServer(boolean shutdownCbServer) {
      this.shutdownCbServer = shutdownCbServer;
   }

   /**
    * @return Defaults to true
    */
   public boolean shutdownCbServer() {
      return this.shutdownCbServer;
   }
}
