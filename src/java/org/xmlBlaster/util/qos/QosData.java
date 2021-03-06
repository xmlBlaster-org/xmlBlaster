/*------------------------------------------------------------------------------
Name:      QosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.property.PropBoolean;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;


/**
 * Base class for the various QoS implementations. 
 * Contains routing informations for cluster traversal
 * @see org.xmlBlaster.util.MsgUnit
 * @author xmlBlaster@marcelruff.info
 */
public abstract class QosData implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = -8909581788281969020L;
   protected transient Global glob;
   private static Logger log = Logger.getLogger(QosData.class.getName());
   protected transient final String serialData; // can be null - in this case use toXml()

   /** the state of the message, defaults to "OK" if no state is returned */
   private String state = Constants.STATE_OK;
   /** Human readable information */
   private String stateInfo;

   /**
    * Marker if message comes from persistent store and is recovered after a server restart. 
    * NOTE: This information is for server side usage only and is NOT dumped to XML!
    */
   private transient boolean fromPersistenceRecovery = false;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   protected Timestamp rcvTimestamp;

   public transient final static boolean DEFAULT_persistent = false;
   private PropBoolean persistent = new PropBoolean(DEFAULT_persistent);

   /**
    * The sender (publisher) of this message (unique loginName),
    * is set by server on arrival, and delivered with UpdateQos (with XML). 
    */
   private SessionName sender;

   /**
    * ArrayList containing RouteInfo objects
    */
   protected ArrayList<RouteInfo> routeNodeList;
   /** Cache for RouteInfo in an array */
   protected transient RouteInfo[] routeNodes;
   private static RouteInfo[] ROUTE_INFO_ARR_DUMMY = new RouteInfo[0];

   private MethodName methodName;

   private Map<String, ClientProperty> clientProperties;
   private final Object clientPropertiesMutex = new Object();

   /**
    * Constructor, it does not parse the data, use a factory for this. 
    */
   public QosData(Global glob, String serialData, MethodName methodName) {
      this.methodName = methodName;
      setGlobal(glob);
      this.serialData = serialData;
      this.clientProperties = new HashMap<String, ClientProperty>();
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public synchronized void setGlobal(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;

   }

   /**
    * @param state The state of an update message
    */
   public synchronized void setState(String state) {
      this.state = state;
   }

   /**
    * Access state of message on update().
    * @return Usually Constants.OK
    */
   public synchronized String getState() {
      return (this.state==null) ? Constants.STATE_OK : this.state;
   }

   /**
    * @param state The human readable state text of an update message
    */
   public synchronized void setStateInfo(String stateInfo) {
      this.stateInfo = stateInfo;
   }

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   public synchronized String getStateInfo() {
      return this.stateInfo;
   }

   public synchronized boolean hasStateInfo() {
      return this.stateInfo!=null && this.stateInfo.length()>0;
   }

   /**
    * True if the message is OK on update(). 
    */
   public synchronized boolean isOk() {
      return this.state == null || this.state.length() < 0 || Constants.STATE_OK.equals(this.state);
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   public synchronized boolean isErased() {
      return Constants.STATE_ERASED.equals(this.state);
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   public synchronized final boolean isTimeout() {
      return Constants.STATE_TIMEOUT.equals(this.state);
   }

   /**
    * True on cluster forward problems
    */
   public synchronized final boolean isForwardError() {
      return Constants.STATE_FORWARD_ERROR.equals(this.state);
   }

   /**
    * Marker if the message comes from persistent store after recovery. 
    * NOTE: This information is not saved in to XML and is lost after a XML dump.
    */
   public synchronized void isFromPersistenceRecovery(boolean fromPersistenceRecovery) {
      // AddressBase contains the same TODO: assure they are in sync or remove one
      this.fromPersistenceRecovery = fromPersistenceRecovery;
   }

   /**
    * Flag if the message comes from persistent store after recovery. 
    */
   public synchronized boolean isFromPersistenceRecovery() {
      return this.fromPersistenceRecovery;
   }

   /**
    * Access sender unified naming object. 
    *
    * The sender (publisher) of this message (unique loginName),
    * is set by server on arrival, and delivered with UpdateQos (with XML). 
    * @return sessionName of sender or null if not known
    * @todo Pass it with QueryQos XML to have more info in cluster environment
    */
   public synchronized SessionName getSender() {
      return sender;
   }

   /**
    * Access sender name.
    * @param loginName of sender
    */
   public synchronized void setSender(SessionName senderSessionName) {
      this.sender = senderSessionName;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public synchronized void setRcvTimestamp(Timestamp rcvTimestamp) {
      this.rcvTimestamp = rcvTimestamp;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC<br />
    * <p>
    * This timestamp is unique for a message instance published and may be
    * used to identify this message. For example a publisher and a receiver
    * of a message can identify this message by its topic (key oid) and its
    * receive timestamp.
    * </p>
    * <p>
    * To get a human readable view on the timestamp try:
    * </p>
    * <pre>
    * String time = qos.getRcvTimestamp().toString();
    *
    * -> "2002-02-10 10:52:40.879456789"
    * </pre>
    * @return can be null if not touchRcvTimestamp() was called
    */
   public synchronized Timestamp getRcvTimestamp() {
      return rcvTimestamp;
   }

   /**
    * @see #getRcvTimestamp
    * @return Never null
    */
   public synchronized Timestamp getRcvTimestampNotNull() {
      if (this.rcvTimestamp == null)
         touchRcvTimestamp();
      return this.rcvTimestamp;
   }

   /**
    * Set timestamp to current time.
    */
   public synchronized void touchRcvTimestamp() {
      this.rcvTimestamp = new RcvTimestamp();
   }

   /**
    * Human readable form of message receive time in xmlBlaster server,
    * in SQL representation e.g.:<br />
    * 2001-12-07 23:31:45.862000004
    * @deprecated Use getXmlRcvTimestamp()
    */
   public synchronized String getRcvTime() {
      return (rcvTimestamp != null) ? rcvTimestamp.toString() : "";
   }

   /**
    * @param persistent mark a message as persistent
    */
   public synchronized void setPersistent(boolean persistent) {
      this.persistent.setValue(persistent);
   }

   /**
    * @return true/false
    */
   public synchronized boolean isPersistent() {
      return this.persistent.getValue();
   }

   public synchronized PropBoolean getPersistentProp() {
      return this.persistent;
   }

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   public synchronized final void addRouteInfo(RouteInfo routeInfo) {
      if (routeInfo == null) {
         log.severe("Adding null routeInfo");
         return;
      }

      this.routeNodes = null; // clear cache

      if (routeNodeList == null)
         routeNodeList = new ArrayList<RouteInfo>();
      routeNodeList.add(routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      for (int ii=routeNodeList.size()-1; ii>=0; ii--) {
         RouteInfo ri = routeNodeList.get(ii);
         ri.setStratum(offset++);
      }
   }

   /**
    * The number of hops
    */
   public synchronized final int getNumRouteNodes() {
      return (this.routeNodeList == null) ? 0 : this.routeNodeList.size();
   }

   /**
    * @return never null, but may have length==0
    */
   public synchronized final RouteInfo[] getRouteNodes() {
      if (this.routeNodeList == null)
         this.routeNodes = ROUTE_INFO_ARR_DUMMY;
      if (this.routeNodes == null)
         this.routeNodes = (RouteInfo[]) routeNodeList.toArray(new RouteInfo[routeNodeList.size()]);
      return this.routeNodes;
   }

   public synchronized final void clearRoutes() {
      this.routeNodes = null;
      if (this.routeNodeList != null)
         this.routeNodeList.clear();
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public synchronized int count(NodeId nodeId) {
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
    * Check if this message is at its master cluster location
    */
   public synchronized final boolean isAtMaster() {
      if (routeNodeList == null || routeNodeList.size() == 0)
         return true;
      for (int ii=routeNodeList.size()-1; ii>=0; ii--) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         if (ri.getStratum() == 0 && !ri.getDirtyRead() && ri.getNodeId().equals(glob.getNodeId()))
            return true;
      }
      return false;
   }


   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public synchronized boolean dirtyRead(NodeId nodeId) {
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
    * @param forceReadable hint to dump in human readable form (avoid Base64)
    * @return internal state of the query as a XML ASCII string
    * 
    */
   public abstract String toXml(String extraOffset, Properties props);
   
   public String toXmlReadable() {
      Properties props = new Properties();
      props.put(Constants.TOXML_FORCEREADABLE, ""+true);
      return toXml("", props);
   }
   
   /*
   public String toXml(String extraOffset, Properties props) {
      // Derived classes may implement it
      return toXml();
   }*/

   public final Global getGlobal() {
      return this.glob;
   }

   public final MethodName getMethod() {
      return this.methodName;
   }

   public final void setMethod(MethodName methodName) {
      this.methodName = methodName;
   }

   public final boolean isPublish() {
      return this.methodName == MethodName.PUBLISH;
   }

   public final boolean isSubscribe() {
      return this.methodName == MethodName.SUBSCRIBE;
   }

   public final boolean isUnSubscribe() {
      return this.methodName == MethodName.UNSUBSCRIBE;
   }

   public final boolean isErase() {
      return this.methodName == MethodName.ERASE;
   }

   public final boolean isGet() {
      return this.methodName == MethodName.GET;
   }

   public final boolean isUpdate() {
      return this.methodName == MethodName.UPDATE;
   }

   /**
    * Returns a deep clone, you can change savely all mutable types. 
    * Immutable types are not cloned as they can't be changed.
    */
   public Object clone() {
      QosData newOne = null;
      try {
         newOne = (QosData)super.clone();
         synchronized(this) {
            //Timestamp is immutable, no clone necessary
            //newOne.rcvTimestamp = (Timestamp)this.rcvTimestamp.clone();

            newOne.persistent = (PropBoolean)this.persistent.clone();
            
            //SessionName is immutable, no clone necessary
            //if (this.sender != null) {
            //   newOne.sender = (SessionName)this.sender.clone();
            //}
            
            if (this.routeNodeList != null/* && this.routeNodeList.size() > 0*/) {
               newOne.routeNodeList = (ArrayList<RouteInfo>)this.routeNodeList.clone();
            }
            
            synchronized (this.clientPropertiesMutex) {
               newOne.clientProperties = (HashMap<String, ClientProperty>)((HashMap<String, ClientProperty>)this.clientProperties).clone();
			}
         }
      }
      catch (CloneNotSupportedException e) {
         e.printStackTrace();
      }
      return newOne;
   }
   
   /**
    * Sets the client property to the given value
    * @param prefix "_bounce:"
    * @return can be null, the properties added
    */   
   public final Properties addClientProperties(String prefix, ClientProperty[] clientProperties, boolean trimPrefix) {
      Properties properties = null;
      for (ClientProperty p: clientProperties) {
         String key = p.getName();
         if (key.startsWith(prefix)) {
  		     String value = p.getStringValue("");
  		     if (trimPrefix) {
                key = key.substring(prefix.length());
                if (key == null || key.length() == 0)
                   continue;
  		     }
  		     addClientProperty(key, value);
  		     if (properties == null)
  		    	properties = new Properties();
             properties.put(key, value);
         }
      }
      return properties;
   }

   /**
    * Sets the client property to the given value
    */   
   public synchronized final void addClientProperty(ClientProperty clientProperty) {
      synchronized (this.clientPropertiesMutex) {
         this.clientProperties.put(clientProperty.getName(), clientProperty);
      }
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param type For example Constants.TYPE_FLOAT
    * @param value Of any type, it will be forced to the given <code>type</code>
    */   
   public synchronized final void addClientProperty(String key, String type, Object value) {
      String encoding = null;
      String str = (value == null) ? null : value.toString();
      ClientProperty clientProperty = new ClientProperty(key, type, encoding, str);
      synchronized (this.clientPropertiesMutex) {
         this.clientProperties.put(clientProperty.getName(), clientProperty);
      }
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value for example a Float or Integer value
    */   
   public final void addClientProperty(String key, Object value) {
      addClientProperty(key, ClientProperty.getPropertyType(value), value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, boolean value) {
      addClientProperty(key, Constants.TYPE_BOOLEAN, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, int value) {
      addClientProperty(key, Constants.TYPE_INT, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, byte value) {
      addClientProperty(key, Constants.TYPE_BYTE, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, long value) {
      addClientProperty(key, Constants.TYPE_LONG, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, short value) {
      addClientProperty(key, Constants.TYPE_SHORT, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, double value) {
      addClientProperty(key, Constants.TYPE_DOUBLE, ""+value);
   }

   /**
    * Sets the client property to the given value
    * @param key
    * @param value
    */   
   public final void addClientProperty(String key, float value) {
      addClientProperty(key, Constants.TYPE_FLOAT, ""+value);
   }

   /**
    * Access the client property. 
    * @param name The property key
    * @return The ClientProperty instance or null if not found
    */
   public synchronized final ClientProperty getClientProperty(String name) {
      if (name == null) return null;
      synchronized (this.clientPropertiesMutex) {
         return (ClientProperty)this.clientProperties.get(name);
      }
   }
   
   public final boolean hasClientProperty(String name) {
     if (name == null) return false;
       synchronized (this.clientPropertiesMutex) {
         return this.clientProperties.containsKey(name);
     }
   }
	   
   public final ClientProperty removeClientProperty(String name) {
     if (name == null) return null;
     synchronized (this.clientPropertiesMutex) {
        return (ClientProperty)this.clientProperties.remove(name);
     }
   }
   
   public final int clearClientProperties() {
      synchronized (this.clientPropertiesMutex) {
         int count = this.clientProperties.size();
         this.clientProperties.clear();
         return count;
      }
   }
	   
   /**
    * Check for client property. 
    * @param name The property key
    * @return true if the property exists
    */
   public synchronized final boolean propertyExists(String name) {
      if (name == null) return false;
      synchronized (this.clientPropertiesMutex) {
         return (this.clientProperties.get(name) != null);
      }
   }
   
   /**
    * Access the String client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final String getClientProperty(String name, String defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getStringValue();
      }
   }
   
   /**
    * Access the integer client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final int getClientProperty(String name, int defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getIntValue();
      }
   }
   
   /**
    * Access the boolean client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final boolean getClientProperty(String name, boolean defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getBooleanValue();
      }
   }
   
   /**
    * Access the double client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final double getClientProperty(String name, double defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getDoubleValue();
      }
   }
   
   /**
    * Access the float client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final float getClientProperty(String name, float defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getFloatValue();
      }
   }
   
   /**
    * Access the byte client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final byte getClientProperty(String name, byte defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getByteValue();
      }
   }
   
   /**
    * Access the byte[] client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final byte[] getClientProperty(String name, byte[] defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getBlobValue();
      }
   }
   
   /**
    * Access the long client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final long getClientProperty(String name, long defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getLongValue();
      }
   }
   
   /**
    * Access the short client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public synchronized final short getClientProperty(String name, short defaultValue) {
      if (name == null) return defaultValue;
      synchronized (this.clientPropertiesMutex) {
         ClientProperty p = (ClientProperty)this.clientProperties.get(name);
         if (p == null) return defaultValue;
         return p.getShortValue();
      }
   }
   
   /**
    * Access all client properties. 
    * @return a map The return is unordered and the map values are of type ClientProperty. 
    * @see org.xmlBlaster.util.qos.ClientProperty
    * @deprecated Not thread safe, use only with synchronize over getClientPropertiesMutex()
    */
   public synchronized final Map<String, ClientProperty> getClientProperties() {
      return this.clientProperties;
   }
   
   public Object getClientPropertiesMutex() {
      return clientPropertiesMutex;
   }
  
   /**
    * @return never null
    */
   public synchronized final ClientProperty[] getClientPropertyArr() {
      synchronized (this.clientPropertiesMutex) {
         return (ClientProperty[])this.clientProperties.values().toArray(new ClientProperty[this.clientProperties.size()]);
      }
   }

   public final int getClientPropertySize() {
      synchronized (this.clientPropertiesMutex) {
	    return this.clientProperties.size();
      }
   }

   public final String writePropertiesXml(String offset) {
      return writePropertiesXml(offset, false);
   }

   public synchronized final String writePropertiesXml(String offset, boolean forceReadable) {
      synchronized (this.clientPropertiesMutex) {
         if (this.clientProperties.size() > 0) {
            Object[] arr = this.clientProperties.keySet().toArray();
            StringBuilder sb = new StringBuilder(arr.length*256);
            for (int i=0; i < arr.length; i++) {
               ClientProperty p = this.clientProperties.get(arr[i]);
               sb.append(p.toXml(offset, null, forceReadable));
            }
            return sb.toString();
         }
      }
      return "";
   }

   public String getContentCharset() {
      return getClientProperty(Constants.CLIENTPROPERTY_CONTENT_CHARSET, Constants.UTF8_ENCODING);
   }

   /**
    * Convenience method to get the raw content as a string, the encoding is UTF-8 if not specified by clientProperty {@link Constants#CLIENTPROPERTY_CONTENT_CHARSET}
    * @return never null
    */
   public String getContentStr(byte[] msgContent) throws XmlBlasterException {
      if (msgContent == null)
         return null;
      String encoding = getClientProperty(Constants.CLIENTPROPERTY_CONTENT_CHARSET, Constants.UTF8_ENCODING);

      try {
         return new String(msgContent, encoding);
      } catch (UnsupportedEncodingException e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "UpdateQos-ClientProperty", "Could not encode according to '" + encoding + "': " + e.getMessage());
      }
   }

   public String getContentStrNoEx(byte[] msgContent) {
      if (msgContent == null)
         return null;
      String encoding = getClientProperty(Constants.CLIENTPROPERTY_CONTENT_CHARSET, Constants.UTF8_ENCODING);

      try {
         return new String(msgContent, encoding);
      } catch (UnsupportedEncodingException e) {
         e.printStackTrace();
         System.err.println("UpdateQos-ClientProperty: Could not encode according to '" + encoding + "': " + e.getMessage());
         return Constants.toUtf8String(msgContent);
      }
   }

}
