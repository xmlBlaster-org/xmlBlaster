/*------------------------------------------------------------------------------
Name:      QosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.RouteInfo;
import org.xmlBlaster.util.enum.Constants;

import java.util.ArrayList;


/**
 * Base class for the various QoS implementations. 
 * Contains routing informations for cluster traversal
 * @see org.xmlBlaster.util.MsgUnit
 * @author xmlBlaster@marcelruff.info
 */
public abstract class QosData implements java.io.Serializable, Cloneable
{
   private transient final String ME = "QosData";
   protected transient Global glob;
   protected transient LogChannel log;
   protected transient final String serialData; // can be null - in this case use toXml()

   /** the state of the message, defaults to "OK" if no state is returned */
   private String state = Constants.STATE_OK;
   /** Human readable information */
   private String stateInfo;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   protected Timestamp rcvTimestamp;

   /**
    * ArrayList containing RouteInfo objects
    */
   protected ArrayList routeNodeList;
   /** Cache for RouteInfo in an array */
   protected transient RouteInfo[] routeNodes;
   private static RouteInfo[] ROUTE_INFO_ARR_DUMMY = new RouteInfo[0];

   /**
    * Constructor, it does not parse the data, use a factory for this. 
    */
   public QosData(Global glob, String serialData) {
      setGlobal(glob);
      this.serialData = serialData;
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public void setGlobal(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = glob.getLog("core");
   }

   /**
    * @param state The state of an update message
    */
   public void setState(String state) {
      this.state = state;
   }

   /**
    * Access state of message on update().
    * @return OK (Other values are not yet supported)
    */
   public String getState() {
      return state;
   }

   /**
    * @param state The human readable state text of an update message
    */
   public void setStateInfo(String stateInfo) {
      this.stateInfo = stateInfo;
   }

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   public String getStateInfo() {
      return this.stateInfo;
   }

   /**
    * True if the message is OK on update(). 
    */
   public boolean isOk() {
      return this.state == null || this.state.length() < 0 || Constants.STATE_OK.equals(this.state);
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   public boolean isErased() {
      return Constants.STATE_ERASED.equals(this.state);
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   public final boolean isTimeout() {
      return Constants.STATE_TIMEOUT.equals(this.state);
   }

   /**
    * True on cluster forward problems
    */
   public final boolean isForwardError() {
      return Constants.STATE_FORWARD_ERROR.equals(this.state);
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public void setRcvTimestamp(Timestamp rcvTimestamp) {
      this.rcvTimestamp = rcvTimestamp;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public Timestamp getRcvTimestamp() {
      return rcvTimestamp;
   }

   /**
    * Set timestamp to current time.
    */
   public void touchRcvTimestamp() {
      rcvTimestamp = new RcvTimestamp();
   }

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   public final void addRouteInfo(RouteInfo routeInfo) {
      if (routeInfo == null) {
         log.error(ME, "Adding null routeInfo");
         return;
      }

      this.routeNodes = null; // clear cache

      if (routeNodeList == null)
         routeNodeList = new ArrayList();
      routeNodeList.add(routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      for (int ii=routeNodeList.size()-1; ii>=0; ii--) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         ri.setStratum(offset++);
      }
   }

   /**
    * The number of hops
    */
   public final int getNumRouteNodes() {
      return (this.routeNodeList == null) ? 0 : this.routeNodeList.size();
   }

   /**
    * @return never null, but may have length==0
    */
   public final RouteInfo[] getRouteNodes() {
      if (this.routeNodeList == null)
         this.routeNodes = ROUTE_INFO_ARR_DUMMY;
      if (this.routeNodes == null)
         this.routeNodes = (RouteInfo[]) routeNodeList.toArray(new RouteInfo[routeNodeList.size()]);
      return this.routeNodes;
   }

   public final void clearRoutes() {
      this.routeNodes = null;
      if (this.routeNodeList != null)
         this.routeNodeList.clear();
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public int count(NodeId nodeId) {
      int count = 0;
      if (routeNodeList == null)
         return count;
      for (int ii=0; ii<routeNodeList.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         if (ri.getNodeId().equals(nodeId))
            count++;
      }
      return count;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public boolean dirtyRead(NodeId nodeId) {
      int count = 0;
      if (routeNodeList == null || nodeId == null)
         return false;
      for (int ii=0; ii<routeNodeList.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         if (ri.getNodeId().equals(nodeId)) {
            return ri.getDirtyRead();
         }
      }
      return false;
   }

   /**
    * The data size for persistence
    * @return The size in bytes of the data in XML form
    */
   public int size() {
      return toXml().length();
   }

   /** The literal XML string of the QoS */
   public abstract String toXml();

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public abstract String toXml(String extraOffset);

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}
