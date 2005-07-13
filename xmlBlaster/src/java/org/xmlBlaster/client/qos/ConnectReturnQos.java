package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * This class wraps the return string of
 * <code>org.xmlBlaster.authentication.Authenticate.connect(...)</code>.
 * <p>
 * It is used on the server side to wrap the return, and on the client side
 * to parse the returned ASCII xml string.
 * Please see documentation at ConnectQos which implements all features.
 * </p>
 * <p>
 * The only thing you may be interested in is the returned sessionId, and
 * the flag if you have reconnected to your previous setting:
 * <p>
 * <pre>
 *&lt;qos>
 *
 *   &lt;securityService type='htpasswd' version='1.0'>
 *      &lt;![CDATA[
 *      &lt;user>joe&lt;/user>
 *      &lt;passwd>secret&lt;/passwd>
 *      ]]>
 *   &lt;/securityService>
 *
 *   &lt;ptp>true&lt;/ptp>
 *
 *   &lt;session name='/node/heron/client/joe/2' timeout='86400000'
 *               maxSessions='10' clearSessions='false'
 *               sessionId='sessionId:192.168.1.2-null-1018875420070--582319444-3'/>
 *
 *   &lt;reconnected>false&lt;/reconnected>  &lt;!-- Has the client reconnected to an existing session? -->
 *
 *   &lt;!-- CbQueueProperty -->
 *   &lt;queue relating='callback'>
 *      &lt;callback type='SOCKET'>
 *         192.168.1.2:33301
 *      &lt;/callback>
 *   &lt;/queue>
 *
 *&lt;/qos>
 * </pre>
 * @see org.xmlBlaster.client.qos.ConnectQos
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html">connect interface</a>
 */
public class ConnectReturnQos {
   public static final String ME = "ConnectReturnQos";
   private Global glob;
   private ConnectQosData connectQosData;

   public ConnectReturnQos(Global glob, ConnectQosData connectQosData) throws XmlBlasterException {
      this.glob = (glob==null) ? Global.instance() : glob;
      //this.log = glob.getLog("client");
      this.connectQosData = connectQosData;
   }
   public ConnectReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, glob.getConnectQosFactory().readObject(xmlQos));
   }

   /**
    * Access the wrapped data holder (for internal use only). 
    */
   public ConnectQosData getData() {
      return this.connectQosData;
   }

   /**
    * The address of the xmlBlaster server to which we are connected. 
    */
   public final ServerRef getServerRef() {
      return this.connectQosData.getServerRef();
   }

   /**
    * The secret sessionId which you need to use for further communication
    * @see SessionQos#getSecretSessionId()
    */
   public String getSecretSessionId() {
      return this.connectQosData.getSessionQos().getSecretSessionId();
   }

   /**
    * The object holding the unique connection name of the client. 
    * @see SessionQos#getSessionName()
    */
   public SessionName getSessionName() {
      return this.connectQosData.getSessionQos().getSessionName();
   }

   /**
    * The object holding all session specific information. 
    */
   public SessionQos getSessionQos() {
      return this.connectQosData.getSessionQos();
   }

   /**
    * The user ID as used by the security framework. 
    */
   public String getUserId() {
      return this.connectQosData.getUserId();
   }

   /**
    * The callback queue exists exactly once per login session, it
    * is used to hold the callback messages for the session. 
    * @return never null. 
    */
   public CbQueueProperty getSessionCbQueueProperty() {
      return this.connectQosData.getSessionCbQueueProperty();
   }

   /**
    * The subjectQueue is exactly one instance for a subjectId (a loginName), it
    * is used to hold the PtP messages send to this subject.
    * <p>
    * The subjectQueue has never callback addresses, the addresses of the sessions are used
    * if configured.
    * </p>
    * @return never null. 
    */
   public CbQueueProperty getSubjectQueueProperty() {
      return this.connectQosData.getSubjectQueueProperty();
   }

   /**
    * Returns the connection state directly after the connect() method returns. 
    * @return Usually ConnectionStateEnum.ALIVE or ConnectionStateEnum.POLLING
    */
   public ConnectionStateEnum getInitialConnectionState() {
      return this.connectQosData.getInitialConnectionState();
   }

   /**
    * @return true A client has reconnected to an existing session
    */
   public boolean isReconnected() {
      return this.connectQosData.isReconnected();
   }

   /**
    * Unique id of the xmlBlaster server, changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @return nodeId + timestamp, '/node/heron/instanceId/33470080380'
    */
   public String getServerInstanceId() {
      return this.connectQosData.getInstanceId();
   }

   public final String toXml() {
      return this.connectQosData.toXml();
   }
   
   public final String toXml(String extraOffset) {
      return this.connectQosData.toXml(extraOffset);
   }
}
