/*------------------------------------------------------------------------------
Name:      DisconnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.ClientProperty;
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

   private PropBoolean clearClientQueue = new PropBoolean(true);
   private boolean shutdownDispatcher = true;
   private boolean shutdownCbServer = true;

   public DisconnectQos(Global glob) {
      this(glob, null);
   }

   /**
    * Constructor for internal use. 
    * @param disconnectQosData The struct holding the data
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
    * Sets a client property (an application specific property) to the
    * given value. 
    * <p>
    * Note that this is no multimap, later similar keys will overwrite the previous
    * @param key
    * @param value
    */
   public void addClientProperty(String key, Object value) {
      this.disconnectQosData.addClientProperty(key, value);
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.disconnectQosData.getClientProperty(key);
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
    * If there are tail back messages in the client side queue, what to do with them. 
    * <p>
    * Controls client side behavior.
    * </p>
    * @param clearClientQueue true Removes all entries of the client side tailback queue which is default<br />
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
