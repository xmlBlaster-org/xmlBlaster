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

public class XBStore {

/**
 * <pre> 
 * xbstoreid NUMBER(20) primary key,
 * xbname varchar(512) not null unique,
 * xbflag1 varchar(32) default ''
 * </pre>
 * 
 */
   
   private long id;
   private String node;
   private String  type;
   private String postfix;
   
   private String flag1;
   
   public XBStore() {
   }

   public long getId() {
      return id;
   }

   public void setId(long id) {
      this.id = id;
   }

   public String getNode() {
      return node;
   }

   public void setNode(String node) {
      this.node = node;
   }

   public String getFlag1() {
      return flag1;
   }

   public void setFlag1(String flag1) {
      this.flag1 = flag1;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getPostfix() {
      return postfix;
   }

   public void setPostfix(String postfix) {
      this.postfix = postfix;
   }

   public String toString() {
      return node + ":" + type + postfix;
   }
   
}