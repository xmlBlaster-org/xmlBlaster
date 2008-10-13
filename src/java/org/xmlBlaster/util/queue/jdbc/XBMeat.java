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

   /** MetaInfo key values */
   public final static String SESSION_NAME = "sessionName";

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
   private long refCount2;
   private String dataType;
   private String metaInfo;
   private String qos;
   private byte[] content;
   private String key;

   public XBMeat(long id) {
      super();
      setId(id);
   }

   public XBMeat() {
      super();
   }

   public long getRefCount() {
      return refCount;
   }

   public void setRefCount(long refCount) {
      this.refCount = refCount;
   }

   public long getRefCount2() {
      return refCount2;
   }

   public void setRefCount2(long refCount2) {
      this.refCount2 = refCount2;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public String getMetaInfo() {
      return metaInfo;
   }

   public void setMetaInfo(String metaInfo) {
      this.metaInfo = metaInfo;
   }

   public String getQos() {
      return qos; // (qos == null) ? "" : qos;
   }

   public void setQos(String qos) {
      this.qos = qos;
   }

   public byte[] getContent() {
      return content; // (content == null) ? new byte[0] : content;
   }

   public void setContent(byte[] content) {
      this.content = content;
   }

   public String getKey() {
      return key; // (key == null) ? "" : key;
   }

   public void setKey(String key) {
      this.key = key;
   }

   public String toXml(String offset) {
      StringBuffer buf = new StringBuffer(512);
      buf.append(offset).append("<xbmeat>\n");
      super.toXml(offset + "  ", buf);
      buf.append(offset).append("  <refCount>").append(refCount).append(
            "</refCount>\n");
      if (dataType != null)
         buf.append(offset).append("  <dataType>").append(dataType).append(
               "</dataType>\n");
      if (qos != null)
         buf.append(offset).append("  <qos>").append(qos).append("</qos>\n");
      // buf.append(offset).append("  <content>").append(content).append("</content>\n");
      if (key != null)
         buf.append(offset).append("  <key>").append(key).append("</key>\n");
      buf.append(offset).append("</xbmeat>\n");
      return buf.toString();
   }

}