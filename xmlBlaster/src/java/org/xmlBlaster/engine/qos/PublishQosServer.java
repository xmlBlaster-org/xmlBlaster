/*------------------------------------------------------------------------------
Name:      PublishQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.QosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.SessionName;

import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.engine.queuemsg.TopicEntry;

import java.util.ArrayList;


/**
 * Handling of publish() quality of services in the server core.
 * <p />
 * This decorator hides the real qos data object and gives us a server specific view on it. 
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.MsgQosData
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 */
public final class PublishQosServer
{
   private final Global glob;
   private final MsgQosData msgQosData;
   private boolean isClusterUpdate = false;
   private TopicEntry topicEntry;

   /**
    * Constructor which accepts parsed object. 
    */
   public PublishQosServer(Global glob, QosData msgQosData) {
      this(glob, (MsgQosData)msgQosData, false);
   }

   /**
    * Constructor which accepts parsed object.
    * @param fromPersistenceStore true if recovered from persistency
    */
   public PublishQosServer(Global glob, MsgQosData msgQosData, boolean fromPersistenceStore) {
      this.glob = glob;
      this.msgQosData = msgQosData;
      this.msgQosData.setFromPersistenceStore(fromPersistenceStore);
      if (!this.msgQosData.isFromPersistenceStore()) {
         this.msgQosData.touchRcvTimestamp();
      }
      completeDestinations();
   }

   /**
    * Constructs the specialized quality of service object for a publish() call,
    * and parses the given XML string.
    * @param the XML based ASCII string
    */
   public PublishQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, xmlQos, false);
   }

   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param xmlQos The XML based ASCII string
    * @param fromPersistenceStore true if recovered from persistency
    */
   public PublishQosServer(Global glob, String xmlQos, boolean fromPersistenceStore) throws XmlBlasterException {
      this(glob, glob.getMsgQosFactory().readObject(xmlQos), fromPersistenceStore);
   }

   /**
    * Checks for relative destination names and completes them with
    * our cluster node id
    */
   private void completeDestinations() {
      if (getNumDestinations() > 0) {
         Destination[] arr = getDestinationArr();
         for(int i=0; i<arr.length; i++) {
            SessionName sessionName = arr[i].getDestination();
            if (sessionName.getNodeId() == null) {
               sessionName = new SessionName(glob, glob.getNodeId(), sessionName.getRelativeName());
               arr[i].setDestination(sessionName);
            }
         }
      }
   }

   /**
    * Access the internal data struct
    */
   public MsgQosData getData() {
      return this.msgQosData;
   }

   public boolean isClusterUpdate() {
      return this.isClusterUpdate;
   }

   /**
    * The PublishQosServer supports to transport information about cluster callbacks
    */
   public void setClusterUpdate(boolean isClusterUpdate) {
      this.isClusterUpdate = isClusterUpdate;
   }

   /**
    * @return Not null if administratice message which is recovered from persistency
    */
   public TopicEntry getTopicEntry() {
      return this.topicEntry;
   }

   /**
    * The PublishQosServer supports to transport a topicEntry instance
    */
   public void setTopicEntry(TopicEntry topicEntry) {
      this.topicEntry = topicEntry;
   }

   /**
    * @param isSubscribable true if Publish/Subscribe style is used<br />
    *         false Only possible for PtP messages to keep PtP secret (you can't subscribe them)
    */
   public void setSubscribable(boolean isSubscribable) {
      this.msgQosData.setSubscribable(isSubscribable);
   }

   public boolean isSubscribable() {
      return this.msgQosData.isSubscribable();
   }

   public boolean isPtp() {
      return this.msgQosData.isPtp();
   }

   public boolean isVolatile() {
      return this.msgQosData.isVolatile();
   }

   public boolean isPersistent() {
      return this.msgQosData.isPersistent();
   }

   public boolean isForceUpdate() {
      return this.msgQosData.isForceUpdate();
   }

   public boolean isReadonly() {
      return this.msgQosData.isReadonly();
   }

   public SessionName getSender() {
      return this.msgQosData.getSender();
   }

   public void setSender(SessionName sender) {
      this.msgQosData.setSender(sender);
   }

   public String getState() {
      return this.msgQosData.getState();
   }

   /**
    * @see org.xmlBlaster.util.qos.MsgQosData#addRouteInfo(RouteInfo)
    */
   public void addRouteInfo(RouteInfo routeInfo) {
      this.msgQosData.addRouteInfo(routeInfo);
   }

   /**
    * @return never null, but may have length==0
    */
   public RouteInfo[] getRouteNodes() {
      return this.msgQosData.getRouteNodes();
   }

   public void clearRoutes() {
      this.msgQosData.clearRoutes();
   }

   public int count(NodeId nodeId) {
      return this.msgQosData.count(nodeId);
   }

   public boolean dirtyRead(NodeId nodeId) {
      return this.msgQosData.dirtyRead(nodeId);
   }

   public PriorityEnum getPriority() {
      return this.msgQosData.getPriority();
   }

   public void setPriority(PriorityEnum priority) {
      this.msgQosData.setPriority(priority);
   }

   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public boolean isFromPersistenceStore() {
      return this.msgQosData.isFromPersistenceStore();
   }

   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   public void setFromPersistenceStore(boolean fromPersistenceStore) {
      this.msgQosData.setFromPersistenceStore(fromPersistenceStore);
   }

   public void setLifeTime(long lifeTime) {
      this.msgQosData.setLifeTime(lifeTime);
   }

   public long getLifeTime() {
      return this.msgQosData.getLifeTime();
   }

   public boolean isExpired() {
      return this.msgQosData.isExpired();
   }

   public Timestamp getRcvTimestamp() {
      return this.msgQosData.getRcvTimestamp();
   }

   public String getXmlRcvTimestamp() {
      return this.msgQosData.getXmlRcvTimestamp();
   }

   public void touchRcvTimestamp() {
      this.msgQosData.touchRcvTimestamp();
   }

   public long getRemainingLife() {
      return this.msgQosData.getRemainingLife();
   }

   public ArrayList getDestinations() {
      return this.msgQosData.getDestinations();
   }

   public Destination[] getDestinationArr() {
      return this.msgQosData.getDestinationArr();
   }

   public void removeDestination(Destination destination) {
      this.msgQosData.removeDestination(destination);
   }

   public int getNumDestinations() {
      return this.msgQosData.getNumDestinations();
   }

   public boolean hasTopicProperty() {
      return this.msgQosData.hasTopicProperty();
   }

   public TopicProperty getTopicProperty() {
      return this.msgQosData.getTopicProperty();
   }

   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      return this.msgQosData.toXml(extraOffset);
   }
}
