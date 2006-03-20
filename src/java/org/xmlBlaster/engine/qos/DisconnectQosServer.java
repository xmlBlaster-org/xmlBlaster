/*------------------------------------------------------------------------------
Name:      DisconnectQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.DisconnectQosData;

/**
 * This class encapsulates the qos of a disconnect() invocation. 
 * <p />
 * @see org.xmlBlaster.util.qos.DisconnectQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">disconnect interface</a>
 */
public final class DisconnectQosServer
{
   private final Global glob;
   private final DisconnectQosData disconnectQosData;

   public DisconnectQosServer(Global glob) {
      this(glob, (DisconnectQosData)null);
   }

   public DisconnectQosServer(Global glob, DisconnectQosData disconnectQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.disconnectQosData = (disconnectQosData == null) ? new DisconnectQosData(this.glob) : disconnectQosData;
   }

   public DisconnectQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.disconnectQosData = glob.getDisconnectQosFactory().readObject(xmlQos);
   }

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
      return toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.disconnectQosData.toXml();
   }
}

