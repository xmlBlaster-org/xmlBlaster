/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBRef extends XBEntry {

   public final static String KEY_OID = "keyOid";
   public final static String MSG_WRAPPER_ID = "msgWrapperId";
   public final static String RECEIVER_STR = "receiverStr";
   public final static String SUB_ID = "subId";
   public final static String FLAG = "flag";
   public final static String REDELIVER_COUNTER = "redeliverCounter";
   

   /**
    * <pre>
    *  xbrefid NUMBER(20) primary key,
    *  xbstoreid NUMBER(20) not null,
    *  xbmeatid NUMBER(20) ,
    *  xbdurable char(1) default 'F' not null ,
    *  xbbytesize NUMBER(10) ,
    *  xbmetainfo clob default '',
    *  xbflag1 varchar(32) default '',
    *  xbmethodname varchar(32) default '',
    *  xbprio  NUMBER(10)
    * </pre>
    * 
    */
   private long storeId;
   private long meatId;
   private String metaInfo;
   private int prio;
   private String methodName;
   
   public XBRef() {
      super();
   }

   public long getStoreId() {
      return storeId;
   }


   public void setStoreId(long storeId) {
      this.storeId = storeId;
   }


   public long getMeatId() {
      return meatId;
   }


   public void setMeatId(long meatId) {
      this.meatId = meatId;
   }


   public String getMetaInfo() {
      return metaInfo;
   }


   public void setMetaInfo(String metaInfo) {
      this.metaInfo = metaInfo;
   }


   public String getMethodName() {
      return methodName;
   }


   public void setMethodName(String methodName) {
      this.methodName = methodName;
   }


   public int getPrio() {
      return prio;
   }


   public void setPrio(int prio) {
      this.prio = prio;
   }

   
}