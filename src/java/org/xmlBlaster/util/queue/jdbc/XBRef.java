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

public class XBRef {

   /**
    * <pre>
    *  xbrefid NUMBER(20) primary key,
    *  xbstoreid NUMBER(20) not null,
    *  xbmeatid NUMBER(20) ,
    *  xbdurable char(1) default 'F' not null ,
    *  xbbytesize NUMBER(10) ,
    *  xbmetainfo clob default '',
    *  xbflag1 varchar(32) default '',
    *  xbprio  NUMBER(10)
    * </pre>
    * 
    */
   private long refId;
   private long storeId;
   private long meatId;
   
   private boolean durable;
   private long byteSize;
   private String metaInfo;
   
   private String flag1;
   private int prio;
   
   
   public XBRef() {
   }


   public long getRefId() {
      return refId;
   }


   public void setRefId(long refId) {
      this.refId = refId;
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


   public boolean isDurable() {
      return durable;
   }


   public void setDurable(boolean durable) {
      this.durable = durable;
   }


   public long getByteSize() {
      return byteSize;
   }


   public void setByteSize(long byteSize) {
      this.byteSize = byteSize;
   }


   public String getMetaInfo() {
      return metaInfo;
   }


   public void setMetaInfo(String metaInfo) {
      this.metaInfo = metaInfo;
   }


   public String getFlag1() {
      return flag1;
   }


   public void setFlag1(String flag1) {
      this.flag1 = flag1;
   }


   public int getPrio() {
      return prio;
   }


   public void setPrio(int prio) {
      this.prio = prio;
   }

   
}