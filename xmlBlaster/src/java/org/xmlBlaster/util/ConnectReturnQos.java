package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This class wraps the return string of
 * <code>org.xmlBlaster.authentication.authenticate.connect(...)</code>.
 * <p />
 * It is used on the server side to wrap the return, and on the client side
 * to parse the returned ASCII xml string.
 * Please see documentation at ConnectQos which implements all features.
 * @see org.xmlBlaster.util.ConnectQos
 */
public class ConnectReturnQos {
   public static final String ME = "ConnectReturnQos";
   private ConnectQos connectQos;

   public ConnectReturnQos(ConnectQos connectQos) throws XmlBlasterException {
      this.connectQos = connectQos;
   }
   public ConnectReturnQos(String xmlQos_literal) throws XmlBlasterException {
      connectQos = new ConnectQos(xmlQos_literal);
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
}
