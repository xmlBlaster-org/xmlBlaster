package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;

/**
 * Container to transport information to the isAuthorized() method. 
 * @author xmlblast@marcelruff.info
 */
public class DataHolder {
   private MethodName action;
   private MsgUnit msgUnit;
   /**
    * @param action
    * @param msgUnit
    */
   public DataHolder(MethodName action, MsgUnit msgUnit) {
      super();
      this.action = action;
      this.msgUnit = msgUnit;
   }
   /**
    * @return Returns the action.
    */
   public MethodName getAction() {
      return this.action;
   }
   /**
    * @param action The action to set.
    */
   public void setAction(MethodName action) {
      this.action = action;
   }
   /**
    * @return Returns the msgUnit.
    */
   public MsgUnit getMsgUnit() {
      return this.msgUnit;
   }
   /**
    * @param msgUnit The msgUnit to set.
    */
   public void setMsgUnit(MsgUnit msgUnit) {
      this.msgUnit = msgUnit;
   }
   
   /**
    * The key oid
    * @return Never null, but empty if msgUnit is null
    */
   public String getKeyOid() {
      return (this.msgUnit != null) ? this.msgUnit.getKeyOid() : "";
   }
   
}
