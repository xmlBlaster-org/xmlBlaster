/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.xmlBlaster.util.def.Constants;

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
   
   public final static int TYPE_UNDEF = 0;
   public final static int TYPE_MEAT = 1;
   public final static int TYPE_REF = 2;
   
   public final static String EMPTY = " "; // Oracle handles "" as NULL
   private long id;
   private String node;
   private String type;
   private String postfix;
   
   private String flag1;
   
   public XBStore() {
   }

   /**
    * Create store from unique compound key.
    * 
    * @param node
    *           "heron"
    * @param type
    *           Constants.RELATING_SESSION
    * @param postfix
    *           "subPersistence,1_0"
    */
   public XBStore(String node, String type, String postfix) {
      super();
      this.node = node;
      this.type = type;
      this.postfix = postfix;
   }

   public long getId() {
      return id;
   }

   public void setId(long id) {
      this.id = id;
   }

   public boolean hasNode() {
      return this.node != null && this.node.length() > 0 && !this.node.equals(EMPTY);
   }

   /**
    * Returns " " for empty node, as Oracle handles empty string as NULL
    * 
    * @return e.g. "heron", glob.getDatabaseNodeStr(), never null
    */
   public String getNode() {
      return (node == null) ? "" : node;
   }

   /**
    * @return " " for empty node, as Oracle handles empty string as NULL
    */
   public String getNodeDb() {
      return (node == null || node.length() == 0) ? EMPTY : node;
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

   public boolean hasType() {
      return this.type != null && this.type.length() > 0 && !this.type.equals(EMPTY);
   }

   /**
    * @return e.g. Constants.RELATING_HISTORY = "history", never null
    */
   public String getType() {
      return (this.type == null) ? "" : this.type;
   }

   /**
    * @return " " for empty node, as Oracle handles empty string as NULL
    */
   public String getTypeDb() {
      return (this.type == null || this.type.length() == 0) ? EMPTY : this.type;
   }

   public void setType(String type) {
      this.type = type;
   }
   
   public boolean hasPostfix() {
      return this.postfix != null && this.postfix.length() > 0 && !this.postfix.equals(EMPTY);
   }

   /**
    * Unique identifier together with getNode() and getType().
    * 
    * @return e.g. strippedRelativeName="clientjoe1" or topicId="Hello" or
    *         pluginId="subPersistence,1_0"
    */
   public String getPostfix() {
      return (this.postfix == null) ? "" : this.postfix;
   }

   /**
    * @return " " for empty node, as Oracle handles empty string as NULL
    */
   public String getPostfixDb() {
      return (this.postfix == null || this.postfix.length() == 0) ? EMPTY : this.postfix;
   }

   public void setPostfix(String postfix) {
      this.postfix = postfix;
   }

   public String toString() {
      return getType() + ":" + getNode() + getPostfix();
   }
   
   /**
    * 
    *  Is Similar to numOfEntriesInTheStore : if true use refs to count the entries in the store
    **/
   public boolean containsRefsForNumOfEntries() {
      if (type == null)
         throw new IllegalArgumentException("The XBStore " + id + " has no type defined: can not determine if it contains refs");
      
      if (type.equals(Constants.RELATING_MSGUNITSTORE))
         return false;
      
      return true;
      /*
      if (type.equals(Constants.RELATING_CALLBACK))
         return true;
      if (type.equals(Constants.RELATING_HISTORY))
         return true;
      if (type.equals(Constants.RELATING_CLIENT))
         return true;
      if (type.equals(Constants.RELATING_SESSION))
         return true;
      if (type.equals(Constants.RELATING_SUBJECT))
         return true;
      if (type.equals(Constants.RELATING_SUBSCRIBE))
         return true;
      if (type.equals(Constants.RELATING_TOPICSTORE))
         return true;
       */
   }
   
   
   
}