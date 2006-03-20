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
public final class ConnectReturnQosServer {
   public static final String ME = "ConnectReturnQosServer";
   private Global glob;
   private ConnectQosData connectQosData;

   public ConnectReturnQosServer(Global glob, ConnectQosData connectQosData) throws XmlBlasterException {
      this.glob = glob;
      this.connectQosData = connectQosData;
      setServerInstanceId(this.glob.getInstanceId());
   }

   public ConnectReturnQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, glob.getConnectQosFactory().readObject(xmlQos));
   }

   public ConnectQosData getData() {
      return this.connectQosData;
   }

   public String toXml() {
      return this.connectQosData.toXml();
   }

   public String toXml(String extraOffset) {
      return this.connectQosData.toXml(extraOffset);
   }

   public void setSecretSessionId(String id) {
      this.connectQosData.getSessionQos().setSecretSessionId(id);
   }

   public void setSessionName(SessionName sessionName) {
      this.connectQosData.getSessionQos().setSessionName(sessionName);
   }

   /**
    * Adds a server reference
    */
   public void addServerRef(ServerRef addr) {
      this.connectQosData.addServerRef(addr);
   }

   public ServerRef getServerRef() {
      return this.connectQosData.getServerRef();
   }

   /**
    * @return true If the entry of protocol given by type was found and removed
    */
   public boolean removeServerRef(String type) {
      return this.connectQosData.removeServerRef(type);
   }

   public SessionQos getSessionQos() {
      return this.connectQosData.getSessionQos();
   }

   public String getSecretSessionId() {
      return this.connectQosData.getSessionQos().getSecretSessionId();
   }

   public SessionName getSessionName() {
      return this.connectQosData.getSessionQos().getSessionName();
   }

   public String getUserId() {
      return this.connectQosData.getUserId();
   }

   /**
    * If reconnected==true a client has reconnected to an existing session
    */
   public void setReconnected(boolean reconnected) {
      this.connectQosData.setReconnected(reconnected);
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

   /**
    * Unique id of the xmlBlaster server, changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @param instanceId e.g. '/node/heron/instanceId/33470080380'
    */
   public void setServerInstanceId(String instanceId) {
      this.connectQosData.setInstanceId(instanceId);
   }
}
