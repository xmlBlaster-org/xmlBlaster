/*------------------------------------------------------------------------------
Name:      AuthenticateProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Secure layer for Authenticate.java
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.jutils.log.LogChannel;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.Global;


/**
 * AuthenticateProtector encapsulates Authenticate.java for security reasons. 
 */
final public class AuthenticateProtector implements I_Authenticate
{
   final private String ME;
   private final Global glob;
   private final LogChannel log;
   private final Authenticate authenticate;

   public AuthenticateProtector(Global global, Authenticate authenticate) throws XmlBlasterException {
      this.glob = global;
      this.log = this.glob.getLog("auth");
      this.ME = "AuthenticateProtector" + glob.getLogPrefixDashed();
      this.authenticate = authenticate;
      if (log.CALL) log.call(ME, "Entering constructor");
      this.glob.setAuthenticate(this);
   }

   public Global getGlobal() {
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
      // serialize first to have a clone for security reasons (and to guarantee our Global)
      // Note: We throw away the ConnectQosServer facade and create a new one (no specific data enters the core)
      ConnectReturnQosServer tmp = this.authenticate.connect(new ConnectQosServer(glob, xmlQos.toXml()), secretSessionId);
      return new ConnectReturnQosServer(glob, tmp.toXml());
   }

   /** helper */
   public final String connect(String connectQos_literal) throws XmlBlasterException {
      return connect(connectQos_literal, null);
   }

   public final String connect(String connectQos_literal, String secretSessionId) throws XmlBlasterException {
      //System.out.println("GOT Protector: " + connectQos_literal);
      ConnectQosServer qos = new ConnectQosServer(glob, connectQos_literal);
      //System.out.println("AFTER Protector: " + qos.toXml());
      ConnectReturnQosServer ret = this.authenticate.connect(qos, secretSessionId);
      return ret.toXml();
   }

   public final void disconnect(String secretSessionId, String qos_literal) throws XmlBlasterException {
      this.authenticate.disconnect(secretSessionId, qos_literal);
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
}
