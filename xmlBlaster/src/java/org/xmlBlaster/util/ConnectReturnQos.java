package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This class wraps the return string of
 * <code>org.xmlBlaster.authentication.authenticate.connect(...)</code>.
 * <p />
 * It is used on the server side to wrap the return, and on the client side
 * to parse the returned ASCII xml string.
 * Please see documentation at ConnectQos which implements all features.
 * <p />
 * The only thing you may be interested in is the returned sessionId, example:
 * <pre>
 *   &lt;qos>
 *      &lt;securityService type='htpasswd' version='1.0'>
 *         &lt;![CDATA[
 *         &lt;user>joe&lt;/user>
 *         &lt;passwd>secret&lt;/passwd>
 *         ]]>
 *      &lt;/securityService>
 *      &lt;ptp>true&lt;/ptp>
 *      &lt;session name='/node/heron/client/joe/2' timeout='86400000' maxSessions='10' clearSessions='false'>
 *         &lt;sessionId>sessionId:192.168.1.2-null-1018875420070--582319444-3&lt;/sessionId>
 *      &lt;/session>
 *      &lt;!-- CbQueueProperty -->
 *      &lt;queue relating='session'>
 *         &lt;callback type='SOCKET'>
 *            192.168.1.2:33301
 *         &lt;/callback>
 *      &lt;/queue>
 *   &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.ConnectQos
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
