package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This class wraps the return string of
 * <code>org.xmlBlaster.authentication.authenticate.connect(...)</code>.
 * <p>
 * It is used on the server side to wrap the return.
 * </p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
 */
public class ConnectReturnQosServer {
   public static final String ME = "ConnectReturnQosServer";
   private Global glob;
   private ConnectQosData connectQosData;

   public ConnectReturnQosServer(Global glob, ConnectQosData connectQosData) throws XmlBlasterException {
      this.glob = glob;
      this.connectQosData = connectQosData;
   }
   public final String toXml() {
      return this.connectQosData.toXml();
   }
   public final String toXml(String extraOffset) {
      return this.connectQosData.toXml(extraOffset);
   }
   public final void setSessionId(String id) {
      this.connectQosData.getSessionQos().setSessionId(id);
   }
   public final void setSessionName(SessionName sessionName) {
      this.connectQosData.getSessionQos().setSessionName(sessionName);
   }
   /**
    * Adds a server reference
    */
   public final void addServerRef(ServerRef addr) {
      this.connectQosData.addServerRef(addr);
   }
   public final ServerRef getServerRef() {
      return this.connectQosData.getServerRef();
   }
   public SessionQos getSessionQos() {
      return this.connectQosData.getSessionQos();
   }
   public String getSessionId() {
      return this.connectQosData.getSessionQos().getSessionId();
   }
   public SessionName getSessionName() {
      return this.connectQosData.getSessionQos().getSessionName();
   }
   public String getUserId() {
      return this.connectQosData.getUserId();
   }
}
