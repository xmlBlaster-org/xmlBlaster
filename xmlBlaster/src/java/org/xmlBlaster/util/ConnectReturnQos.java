package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @deprecated Use org.xmlBlaster.client.qos.ConnectReturnQos instead
 */
public class ConnectReturnQos {
   public static final String ME = "ConnectReturnQos";
   private Global glob;
   private ConnectQos connectQos;

   public ConnectReturnQos(Global glob, ConnectQos connectQos) throws XmlBlasterException {
      this.glob = glob;
      this.connectQos = connectQos;
   }
   public ConnectReturnQos(Global glob, String xmlQos_literal) throws XmlBlasterException {
      this.glob = glob;
      boolean isServerSide = false;
      connectQos = new ConnectQos(glob, xmlQos_literal, isServerSide);
   }
   public final String toXml() {
      return connectQos.toXml();
   }
   public final String toXml(String extraOffset) {
      return connectQos.toXml(extraOffset);
   }
   public final void setSessionId(String id) {
      connectQos.setSessionId(id);
   }
   public final void setSessionName(SessionName sessionName) {
      connectQos.setSessionName(sessionName);
   }
   /**
    * Adds a server reference
    */
   public final void setServerRef(ServerRef addr) {
      connectQos.setServerRef(addr);
   }
   public final ServerRef getServerRef() {
      return connectQos.getServerRef();
   }
   public String getSessionId() {
      return connectQos.getSessionId();
   }
   public SessionName getSessionName() {
      return connectQos.getSessionName();
   }
   public String getUserId() {
      return connectQos.getUserId();
   }
}
