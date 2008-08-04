package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.QosData;
import org.xmlBlaster.util.qos.address.Destination;

/**
 * Container to transport information to the isAuthorized() method.
 * @author xmlblast@marcelruff.info
 */
public class DataHolder {
   private final MethodName action;
   private final MsgUnit msgUnit;

   /**
    * @param action May not be null
    * @param msgUnit May not be null
    */
   public DataHolder(MethodName action, MsgUnit msgUnit) {
      super();
      if (action == null) throw new IllegalArgumentException("Creating DataHolder expects none null action");
      if (msgUnit == null) throw new IllegalArgumentException("Creating DataHolder expects none null msgUnit");
      this.action = action;
      this.msgUnit = msgUnit;
   }
   /**
    * @return Returns the action, never null
    */
   public MethodName getAction() {
      return this.action;
   }

   /**
    * @return Returns the msgUnit, is never null
    */
   public MsgUnit getMsgUnit() {
      return this.msgUnit;
   }

   /**
    * The key oid
    * @return Never null, but can be empty
    */
   public String getKeyOid() {
      return this.msgUnit.getKeyOid() == null ? "" : this.msgUnit.getKeyOid();
   }

   /**
    * The key url notation like "exact:hello", "xpath://key", "domain:sport"
    * @return Never null, but can be empty
    */
   public String getKeyUrl() {
      return this.msgUnit.getKeyData() == null ? "" : this.msgUnit.getKeyData().getUrl();
   }

   /**
    * Convenience method to access QoS of MsgUnit
    * @return Never null
    */
   public QosData getQosData() {
	   if (this.msgUnit.getQosData() == null) 
		   throw new IllegalStateException("msgUnit.getQosData should never be null");
	   return this.msgUnit.getQosData();
   }

   /**
    * Convenience method to access PtP destination
    * @return null if no PtP publish message
    */
   public SessionName getDestinationSessionName() {
      final Destination d = getDestination();
      if (d == null) return null;
      return d.getDestination();
   }
   
   /**
    * Convenience method to access PtP destination
    * @return null if no PtP publish message
    */
   public Destination getDestination() {
      final QosData qosData = getQosData();
      final MethodName m = qosData.getMethod();
      if (MethodName.PUBLISH.equals(m) || MethodName.PUBLISH_ARR.equals(m) || MethodName.PUBLISH_ONEWAY.equals(m)
       || MethodName.UPDATE.equals(m) || MethodName.UPDATE_ONEWAY.equals(m)) {
         if (qosData instanceof MsgQosData) {
            MsgQosData msgQosData = (MsgQosData)qosData;
            return (msgQosData.getDestinationArr().length > 0) ? msgQosData
               .getDestinationArr()[0]
               : null;
         }
      }
      return null;
   }
	
   public String toString() {
      return this.action + " " + this.msgUnit.getKeyOid();
   }
}
