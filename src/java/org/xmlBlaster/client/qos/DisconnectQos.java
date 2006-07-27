/*------------------------------------------------------------------------------
Name:      DisconnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.ClientProperty;
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
 * The following properties are evaluated (command line or xmlBlaster.properties)
 * and control the behaviour on client side:
 * <pre>
 * dispatch/connection/shutdownDispatcher  true/false
 * dispatch/connection/shutdownCbServer    true/false
 * dispatch/connection/leaveServer         true/false
 * </pre>
 * Additionally you can set these values as clientProperties, which have priority:
 * <pre>
 *    &lt;qos>
 *       &lt;clientProperty name='shutdownDispatcher'>true&lt;/clientProperty>
 *       &lt;clientProperty name='shutdownCbServer'>true&lt;/clientProperty>
 *       &lt;clientProperty name='leaveServer'>false&lt;/clientProperty>
 *    &lt;/qos>
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 */
public class DisconnectQos
{
   private final Global glob;
   private final DisconnectQosData disconnectQosData;

   private PropBoolean clearClientQueue = new PropBoolean(true);
   private boolean shutdownDispatcher;
   private boolean shutdownCbServer;
   private boolean leaveServer;

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
      init();
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
   
   private void init() {
      
      this.shutdownDispatcher = this.glob.getProperty().get("dispatch/connection/shutdownDispatcher", true);
      if (this.disconnectQosData.getClientProperties().containsKey("shutdownDispatcher")) {
         this.shutdownDispatcher = this.disconnectQosData.getClientProperty("shutdownDispatcher", this.shutdownDispatcher);
      }
      
      this.shutdownCbServer = this.glob.getProperty().get("dispatch/connection/shutdownCbServer", true);
      if (this.disconnectQosData.getClientProperties().containsKey("shutdownCbServer")) {
         this.shutdownCbServer = this.disconnectQosData.getClientProperty("shutdownCbServer", this.shutdownCbServer);
      }
      
      this.leaveServer = this.glob.getProperty().get("dispatch/connection/leaveServer", false);
      if (this.disconnectQosData.getClientProperties().containsKey("leaveServer")) {
         this.leaveServer = this.disconnectQosData.getClientProperty("leaveServer", this.leaveServer);
      }
   }

   /**
    * Return true if subject queue shall be deleted with last user session
    * @return true;
    */
   public boolean deleteSubjectQueue() {
      return this.disconnectQosData.deleteSubjectQueue();
   }

   /**
    * If subject queue shall be deleted with last user session logout
    * @param del defaults to true
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
   
   public final String toXml(String extraOffset, Properties props) {
      return this.disconnectQosData.toXml(extraOffset, props);
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

   /**
    * @return Returns the current setting
    */
   public boolean isLeaveServer() {
      return this.leaveServer;
   }

   /**
    * Set this to true if you just want to cleanup the
    * client library but not disconnect from the server.
    * At the server our session remains and will queue messages
    * for us until we login again.
    * @param leaveServer The leaveServer to set.
    */
   public void setLeaveServer(boolean leaveServer) {
      this.leaveServer = leaveServer;
   }
}
