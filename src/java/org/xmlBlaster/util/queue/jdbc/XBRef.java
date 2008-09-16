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
   private long storeId;
   private long meatId;
   private String metaInfo;
   private int prio;
   
   
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


   public int getPrio() {
      return prio;
   }


   public void setPrio(int prio) {
      this.prio = prio;
   }

   
}