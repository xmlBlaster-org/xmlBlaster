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

public class XBMeat extends XBEntry {

   public final static String SESSION_NAME="sessionName";
   
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
   private long refCount;
   private String dataType;
   private String qos;
   private byte[] content;
   private String key;
   
   
   public XBMeat() {
      super();
   }


   public long getRefCount() {
      return refCount;
   }


   public void setRefCount(long refCount) {
      this.refCount = refCount;
   }


   public String getDataType() {
      return dataType;
   }


   public void setDataType(String dataType) {
      this.dataType = dataType;
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