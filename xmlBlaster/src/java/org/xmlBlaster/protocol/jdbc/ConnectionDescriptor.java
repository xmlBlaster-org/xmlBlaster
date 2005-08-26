/*------------------------------------------------------------------------------
 * Name:      ConnectionDescriptor.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Provides a wrapper defining a database connection
 * Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.w3c.dom.Document;
import org.w3c.dom.Node;


/**
 * @author James
 * @see org.xmlBlaster.client.XmlDbMessageWrapper
 */
public class ConnectionDescriptor {

   private String       url = "";
   private String       username = "";
   private String       password = "";
   private String       interaction = "query";
   private String       command = "";
   //private String       connectionkey = "";
   private long         connectionlifespan = 300000;
   private boolean      confirmation = true;
   private String       documentrootnode = "dbadapterresults";
   private String       rowrootnode = "row";
   private int          rowlimit = -1;

   private Document  document = null;

   /**
    * @param document This will be parsed
    */
   public ConnectionDescriptor(Document document) {
      this.document = document;

      parse();
   }

   /**
    */
   private void parse() {
      Node firstNode = document.getFirstChild();
      //org.apache.crimson.tree.TreeWalker  tw = new org.apache.crimson.tree.TreeWalker(firstNode);
      //Ported to org/w3c/dom/traversal/TreeWalker.java DOM Level 2, since JDK 1.5 (marcel 2005-08-22)
      org.w3c.dom.traversal.TreeWalker tw = org.xmlBlaster.util.XmlNotPortable.getTreeWalker(document, null);
      Node curNode = null;

      while ((curNode = tw.nextNode()) != null) { // crimson: tw.getNext()
         if (curNode.getNodeType() == Node.ELEMENT_NODE
                 && curNode.getNodeName().equalsIgnoreCase("database:url")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               url = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:username")) {
            if (curNode.getFirstChild() == null) {
               username = ""; // dBase for example needs no user name
            }
            else if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               username = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:password")) {
            if (curNode.getFirstChild() == null) {
               password = ""; // dBase for example needs no password
            }
            else if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               password = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:interaction")) {
            interaction =
               curNode.getAttributes().getNamedItem("type").getNodeValue();

            if (interaction == null || interaction.equals("")) {
               interaction = "query";
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:command")) {
            if (curNode.getFirstChild().getNodeType() == Node.CDATA_SECTION_NODE) {
               command = curNode.getFirstChild().getNodeValue();
            }
            else if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               command = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:connectionkey")) {
            //if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            //   connectionkey = curNode.getFirstChild().getNodeValue();
            //}
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:connectionlifespan")) {
            String   ttl =
               curNode.getAttributes().getNamedItem("ttl").getNodeValue();

            try {
               connectionlifespan = Long.parseLong(ttl);
               connectionlifespan *= 60000;
            }
            catch (NumberFormatException e) {
               connectionlifespan = -1;
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:confirmation")) {
            String   con =
               curNode.getAttributes().getNamedItem("confirm").getNodeValue();

            confirmation = (new Boolean(con)).booleanValue();
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:documentrootnode")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               documentrootnode = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:rowrootnode")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               rowrootnode = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:rowlimit")) {
            String   lim =
               curNode.getAttributes().getNamedItem("max").getNodeValue();

            try {
               rowlimit = Integer.parseInt(lim);
            }
            catch (NumberFormatException e) {
               rowlimit = -1;
            }
         }
      }
   }

   /**
    * @return The JDBC connection string
    */
   public String getUrl() {
      return url;
   }

   /**
    * @return The database user
    */
   public String getUsername() {
      return username;
   }

   /**
    * @return The database password
    */
   public String getPassword() {
      return password;
   }

   /**
    * @return Intercation string
    */
   public String getInteraction() {
      return interaction;
   }

   /**
    * @return The SQL statement
    */
   public String getCommand() {
      return command;
   }

   /**
    * TODO: pass through to PoolManager
    */
   public long getConnectionlifespan() {
      return connectionlifespan;
   }

   /**
    * @return User wants a return messsage?
    */
   public boolean getConfirmation() {
      return confirmation;
   }

   /**
    */
   public String getDocumentrootnode() {
      return documentrootnode;
   }

   /**
    */
   public String getRowrootnode() {
      return rowrootnode;
   }

   /**
    * @return The max number of returned result sets
    */
   public int getRowlimit() {
      return rowlimit;
   }
}

