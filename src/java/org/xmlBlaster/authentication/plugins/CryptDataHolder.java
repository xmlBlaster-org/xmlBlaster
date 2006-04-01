package org.xmlBlaster.authentication.plugins;

import java.util.Map;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.MethodName;

/**
 * Container to transport information to the exportMessage() and importMessage() method.
 * <p>
 * Is used on client and on server side. 
 * @author xmlblast@marcelruff.info
 */
public class CryptDataHolder {
   private MethodName action;
   private MsgUnitRaw msgUnitRaw;
   private Map clientProperties;
   private boolean returnValue;
   
   /**
    * @param msgUnitRaw
    * @param action
    * @param clientProperties
    */
   public CryptDataHolder(MethodName action, MsgUnitRaw msgUnitRaw) {
      this(action, msgUnitRaw, (Map)null);
   }
   
   /**
    * @param msgUnitRaw
    * @param action
    * @param clientProperties
    */
   public CryptDataHolder(MethodName action, MsgUnitRaw msgUnitRaw, Map clientProperties) {
      super();
      this.action = action;
      this.msgUnitRaw = msgUnitRaw;
      this.clientProperties = clientProperties;
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
    * @return Returns the clientProperties, can be null
    */
   public Map getClientProperties() {
      return this.clientProperties;
   }
   /**
    * @param clientProperties The clientProperties to set.
    */
   public void setClientProperties(Map clientProperties) {
      this.clientProperties = clientProperties;
   }
   /**
    * @return Returns the msgUnitRaw.
    */
   public MsgUnitRaw getMsgUnitRaw() {
      return this.msgUnitRaw;
   }
   /**
    * @param msgUnitRaw The msgUnitRaw to set.
    */
   public void setMsgUnitRaw(MsgUnitRaw msgUnitRaw) {
      this.msgUnitRaw = msgUnitRaw;
   }

   /**
    * @return Returns the returnValue.
    */
   public boolean isReturnValue() {
      return this.returnValue;
   }

   /**
    * @param returnValue The returnValue to set.
    */
   public void setReturnValue(boolean returnValue) {
      this.returnValue = returnValue;
   }
}
