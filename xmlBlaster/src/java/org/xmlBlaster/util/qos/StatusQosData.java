/*------------------------------------------------------------------------------
Name:      StatusQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.MethodName;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * Data container handling of status returned by subscribe(), unSubscribe(), erase() and ping(). 
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeReturnQos Returned QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeReturnQos Returned QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseReturnQos Returned QoS of an erase() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see StatusQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public final class StatusQosData extends QosData implements java.io.Serializable, Cloneable
{
   private String ME = "StatusQosData";
   private transient final I_StatusQosFactory factory;

   /** The subscription ID of a subscribe() invocation */
   private String subscriptionId;

   /** The key oid of a publish(), helpful if the oid was generated by xmlBlaster */
   private String keyOid;

   /** is set only in case of an exception */
   private Throwable ex;

   /**
    * Constructs the specialized quality of service object for status informations,
    * e.g. for a return of a subscribe() call
    * <p>
    * The state defaults to Constants.STATE_OK
    * </p>
    * @param The factory which knows how to serialize and parse me
    */
   public StatusQosData(Global glob, MethodName methodName) {
      this(glob, null, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for status informations. 
    * E.g. for a return of a subscribe() call
    * @param The factory which knows how to serialize and parse me
    */
   public StatusQosData(Global glob, I_StatusQosFactory factory, MethodName methodName) {
      this(glob, factory, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for a publish() call. 
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    * @param true
    */
   public StatusQosData(Global glob, I_StatusQosFactory factory, String serialData, MethodName methodName) {
      super(glob, serialData, methodName);
      this.factory = (factory==null) ? glob.getStatusQosFactory() : factory;
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   public void setSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   public String getSubscriptionId() {
      return this.subscriptionId;
   }

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   public String getKeyOid() {
      return this.keyOid;
   }

   /**
    * Access unique oid of a message topic. 
    */
   public void setKeyOid(String oid) {
      this.keyOid = oid;
   }

   /**
    * Dump the QoS to a flattened JXPath representation. 
    * <p>
    * This is experimental code for the simple Applet client
    * </p>
    * <pre>
    *   /qos/state/@id             -> getState()
    *   /qos/state/@info           -> getStateInfo()
    *   /qos/rcvTimestamp/@nanos   -> getRcvTimestamp()
    *   /qos/rcvTimestamp/text()   -> getRcvTime()
    *   /qos/methodName/text()     -> getMethod()
    *   /qos/key/@oid              -> getKeyOid()
    *   /qos/subscribe/@id         -> getSubscriptionId()
    * </pre>
    * <p>
    * Currently only an UpdateQos dump is supported
    * @see <a href="http://jakarta.apache.org/commons/jxpath/">Apache JXPath</a>
    */
   public Map toJXPath() {
      /* Problems with current java objects / JXPath mapping:
        1.  getState() returns the <state id=''> instead of a state object with state.getId(), state.getInfo()
      */

      TreeMap map = new TreeMap();
      map.put("/qos/rcvTimestamp/@nanos", ""+getRcvTimestamp());
      map.put("/qos/rcvTimestamp/text()", ""+getRcvTime());
      map.put("/qos/methodName/text()", getMethod());
      map.put("/qos/state/@id", getState());
      map.put("/qos/state/@info", getStateInfo());
      map.put("/qos/key/@oid", getKeyOid());
      map.put("/qos/subscribe/@id", getSubscriptionId());
      return map;
   }

   /**
    * The size in bytes of the data in XML form. 
    */
   public int size() {
      return toXml().length();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the status as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the status as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return factory.writeObject(this, extraOffset);
   }

   /*
   public static String toXml(String state, String stateInfo, String subscriptionId, String keyOid) {
      // hack to use directly SaxFactory to avoid object creation
      // if in future we want to support other formats (not XML) we need to go the usual way over 'new StatusQosData'
      return org.xmlBlaster.util.qos.StatusQosSaxFactory.writeObject_(state, stateInfo, subscriptionId, keyOid, null);
   }
   */

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    */
   public Object clone() {
      return super.clone();
   }
   
   
   /**
    * Setter for the exception 
    */
   public void setException(Throwable ex) {
      this.ex = ex;
   }
   
   public Throwable getException() {
      return this.ex;
   }
   
}
