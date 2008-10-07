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
   private long meatId;
   private String metaInfo;
   private int prio;
   private String methodName;
   private XBMeat meat;
   /**
    * The oneToMany flag is true if the entry is following a relationship one to many, i.e. each meat is referenced by zero or
    * more references. If set to false, then the relationship is one to one, i.e. every meat is referenced by one single reference.
    * one to one reference normally occurs in connection queues.
    */
   private boolean oneToMany;
   
   public XBRef() {
      super();
   }

   public long getMeatId() {
      if (meat != null)
         return meat.getId();
      return meatId;
   }


   public void setMeatId(long meatId) {
      if (meat != null)
         meat.setId(meatId);
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
   
   public XBMeat getMeat() {
      return meat;
   }
   
   public void setMeat(XBMeat meat) {
      this.meat = meat;
   }

   public void setOneToMany(boolean oneToMany) {
      this.oneToMany = oneToMany;
   }

   public boolean isOneToMany() {
      return oneToMany;
   }

   
   public String toXml(String offset) {
      StringBuffer buf = new StringBuffer(512);
      buf.append(offset).append("<xbref oneToMany='").append(oneToMany).append("'>\n");
      super.toXml(offset + "  ", buf);
      if (meatId != 0)
         buf.append(offset).append("  <meatId>").append(meatId).append("</meatId>\n");
      if (metaInfo != null)
         buf.append(offset).append("  <metaInfo>").append(metaInfo).append("</metaInfo>\n");
      buf.append(offset).append("  <prio>").append(prio).append("</prio>\n");
      if (methodName != null)
         buf.append(offset).append("  <methodName>").append(methodName).append("</methodName>\n");
      if (meat != null)
         buf.append(meat.toXml(offset + "  "));
      buf.append(offset).append("</xbref>\n");
      return buf.toString();
   }
   
}