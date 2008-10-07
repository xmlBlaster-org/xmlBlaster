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

public abstract class XBEntry {

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
   private long id;
   private boolean durable;
   private long byteSize;
   private String flag1;
   private long storeId;

   public XBEntry() {
   }

   public long getId() {
      return id;
   }


   public void setId(long id) {
      this.id = id;
   }

   public long getStoreId() {
      return storeId;
   }


   public void setStoreId(long storeId) {
      this.storeId = storeId;
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


   public String getFlag1() {
      return flag1;
   }


   public void setFlag1(String flag1) {
      this.flag1 = flag1;
   }

   
   protected void toXml(String offset, StringBuffer buf) {
      buf.append(offset).append("<id>").append(id).append("</id>\n");
      buf.append(offset).append("<storeId>").append(storeId).append("</storeId>\n");
      buf.append(offset).append("<durable>").append(durable).append("</durable>\n");
      buf.append(offset).append("<byteSize>").append(byteSize).append("</byteSize>\n");
      if (flag1 != null)
         buf.append(offset).append("<flag1>").append(flag1).append("</flag1>\n");
   }
   
}