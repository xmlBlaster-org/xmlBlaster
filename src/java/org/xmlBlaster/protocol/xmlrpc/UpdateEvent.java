package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.MsgUnitRaw;


public class UpdateEvent {
   private final String method;
   private final MsgUnitRaw[] msgUnit;
   private final String qos;
   private final long uniqueId;
   private final String[] ret;
   
   public UpdateEvent(String method, MsgUnitRaw[] msgUnit, String qos, String[] ret, long uniqueId) {
      this.method = method;
      this.msgUnit = msgUnit;
      this.qos = qos;
      this.uniqueId = uniqueId;
      this.ret = ret;
   }

   public String getMethod() {
      return method;
   }

   public MsgUnitRaw[] getMsgUnit() {
      return msgUnit;
   }

   public String getQos() {
      return qos;
   }

   public long getUniqueId() {
      return uniqueId;
   }
   
   public String[] getRet() {
      return ret;
   }
   
}


