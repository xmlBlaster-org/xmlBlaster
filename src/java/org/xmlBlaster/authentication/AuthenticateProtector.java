/*------------------------------------------------------------------------------
Name:      AuthenticateProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Secure layer for Authenticate.java
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.AvailabilityChecker;


/**
 * AuthenticateProtector encapsulates Authenticate.java for security reasons.
 */
final public class AuthenticateProtector implements I_Authenticate
{
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(AuthenticateProtector.class.getName());
   private final Authenticate authenticate;
   private final AvailabilityChecker availabilityChecker;

   public AuthenticateProtector(ServerScope global, Authenticate authenticate) throws XmlBlasterException {
      this.glob = global;

      this.authenticate = authenticate;
      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor");
      this.glob.setAuthenticate(this);
      this.availabilityChecker = new AvailabilityChecker(this.glob);
   }

   public ServerScope getGlobal() {
      return this.glob;
   }

   public I_XmlBlaster getXmlBlaster() {
      return this.authenticate.getXmlBlaster();
   }

   public boolean sessionExists(String sessionId) {
      return this.authenticate.sessionExists(sessionId);
   }

   /** helper */
   public final ConnectReturnQosServer connect(ConnectQosServer xmlQos) throws XmlBlasterException {
      return connect(xmlQos, null);
   }

   /** helper */
   public final ConnectReturnQosServer connect(ConnectQosServer xmlQos, String secretSessionId) throws XmlBlasterException {

      MsgUnit msgUnit = new MsgUnit(null, null, xmlQos.getData());
      this.availabilityChecker.checkServerIsReady(xmlQos.getSessionName(), xmlQos.getAddressServer(), msgUnit, MethodName.CONNECT);

      try {
         // serialize first to have a clone for security reasons (and to guarantee our Global)
         // Note: We throw away the ConnectQosServer facade and create a new one (no specific data enters the core)
         ConnectQosServer clone = xmlQos.getClone(glob);
         ConnectReturnQosServer tmp = this.authenticate.connect(clone, secretSessionId);
         return new ConnectReturnQosServer(glob, tmp.toXml());
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.CONNECT, e);
      }
   }

   /** helper */
   public final String connect(AddressServer addressServer, String connectQos_literal) throws XmlBlasterException {
      return connect(addressServer, connectQos_literal, null);
   }

   public final String connect(AddressServer addressServer, String connectQos_literal, String secretSessionId) throws XmlBlasterException {

      // Parse XML QoS
      MsgUnit msgUnit = new MsgUnit(glob, null, null, connectQos_literal, MethodName.CONNECT);
      ConnectQosServer qos = new ConnectQosServer(glob, (ConnectQosData)msgUnit.getQosData());
      qos.setAddressServer(addressServer);

      // Currently we have misused used the clientProperty to transport this information
      if (qos.getData().getClientProperty(Constants.PERSISTENCE_ID) != null)
         qos.isFromPersistenceRecovery(true);

      this.availabilityChecker.checkServerIsReady(qos.getSessionName(), addressServer, msgUnit, MethodName.CONNECT);

      try {
         //System.out.println("GOT Protector: " + connectQos_literal);
         //System.out.println("AFTER Protector: " + qos.toXml());
         ConnectReturnQosServer ret = this.authenticate.connect(qos, secretSessionId);
         return ret.toXml();
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.CONNECT, e);
      }
   }

   public final void disconnect(AddressServer addressServer, String secretSessionId, String qos_literal) throws XmlBlasterException {

      // Parse XML QoS
      MsgUnit msgUnit = new MsgUnit(glob, null, null, qos_literal, MethodName.DISCONNECT);
      this.availabilityChecker.checkServerIsReady(null, addressServer, msgUnit, MethodName.DISCONNECT);

      try {
         this.authenticate.disconnect(secretSessionId, qos_literal);
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.DISCONNECT, e);
      }
   }

   /**
    * Ping xmlBlaster if everything is OK, checks the availability.
    * @return "<qos><state id='OK'/></qos>" if we are ready, otherwise the current run level string
    * @see org.xmlBlaster.engine.AvailabilityChecker#getStatus(String)
    */
   public final String ping(AddressServer addressServer, String qos) {
      return "<qos><state id='" + this.availabilityChecker.getStatus(qos) + "'/></qos>";
   }

   public void connectionState(String secretSessionId, ConnectionStateEnum state) {
      this.authenticate.connectionState(secretSessionId, state);
   }

   /**
    * Administrative access.
    */
   public I_AdminSubject getSubjectInfoByName(SessionName sessionName) throws XmlBlasterException {
      SubjectInfo subjectInfo = this.authenticate.getSubjectInfoByName(sessionName);
      return (subjectInfo == null) ? null : subjectInfo.getSubjectInfoProtector();
   }

   /**
    * @deprecated Security hole, currently need by MainGUI.java
    */
   public SessionInfo unsecureCreateSession(SessionName loginName) throws XmlBlasterException {
      org.xmlBlaster.client.qos.ConnectQos connectQos = new org.xmlBlaster.client.qos.ConnectQos(glob);
      connectQos.setSessionName(loginName);
      return this.authenticate.unsecureCreateSession(connectQos);
   }

   public String toXml() throws XmlBlasterException {
      return this.authenticate.toXml();
   }

   public final void shutdown() {
      this.availabilityChecker.shutdown();
   }
}
