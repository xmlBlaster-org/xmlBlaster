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

public class XBMeat {

   /**
    * 
    * <pre>
    * xbmeatid NUMBER(20) primary key,
    * xbdurable char default 'F' not null,
    * xbrefcount NUMBER(10),
    * xbbytesize NUMBER(10),
    * xbdatatype varchar(32) default '' not null,
    * xbflag1 varchar(32) default '',
    * xbmsgqos clob default '',
    * xbmsgcont blob default '',
    * xbmsgkey clob default ''
    * </pre>
    * 
    * @return
    */
   private long id;
   
   private boolean durable;
   
   private long refCount;
   
   private long byteSize;
   private String dataType;
   
   private String flag1;
   private String qos;
   
   private byte[] content;
   private String key;
   
   
   public XBMeat() {
   }


   public long getId() {
      return id;
   }


   public void setId(long id) {
      this.id = id;
   }


   public boolean isDurable() {
      return durable;
   }


   public void setDurable(boolean durable) {
      this.durable = durable;
   }


   public long getRefCount() {
      return refCount;
   }


   public void setRefCount(long refCount) {
      this.refCount = refCount;
   }


   public long getByteSize() {
      return byteSize;
   }


   public void setByteSize(long byteSize) {
      this.byteSize = byteSize;
   }


   public String getDataType() {
      return dataType;
   }


   public void setDataType(String dataType) {
      this.dataType = dataType;
   }


   public String getFlag1() {
      return flag1;
   }


   public void setFlag1(String flag1) {
      this.flag1 = flag1;
   }


   public String getQos() {
      return qos;
   }


   public void setQos(String qos) {
      this.qos = qos;
   }


   public byte[] getContent() {
      return content;
   }


   public void setContent(byte[] content) {
      this.content = content;
   }


   public String getKey() {
      return key;
   }


   public void setKey(String key) {
      this.key = key;
   }
   
   
   
}