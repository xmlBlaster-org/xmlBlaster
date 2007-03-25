package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;

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

   public String toString() {
      return this.action + " " + this.msgUnit.getKeyOid();
   }
}
