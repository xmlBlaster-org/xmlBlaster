/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

/*
 * ------------------------------------------------------------------------------
 * Name:      ConnectionDescriptor.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Provides a wrapper defining a database connection
 * Version:   $Id: ConnectionDescriptor.java,v 1.3 2000/07/03 16:39:50 ruff Exp $
 * ------------------------------------------------------------------------------
 */

package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.Log;
import com.sun.xml.tree.*;
import org.w3c.dom.*;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class ConnectionDescriptor {

   private String       url = "";
   private String       username = "";
   private String       password = "";
   private String       interaction = "query";
   private String       command = "";
   private String       connectionkey = "";
   private long         connectionlifespan = 300000;
   private boolean      confirmation = true;
   private String       documentrootnode = "dbadapterresults";
   private String       rowrootnode = "row";
   private int          rowlimit = -1;

   private XmlDocument  document = null;

   /**
    * Constructor declaration
    *
    *
    * @param document
    *
    * @see
    */
   public ConnectionDescriptor(XmlDocument document) {
      this.document = document;

      parse();
   }

   /**
    * Method declaration
    *
    *
    * @see
    */
   private void parse() {
      Node        firstNode = document.getFirstChild();
      TreeWalker  tw = new TreeWalker(firstNode);
      Node        curNode = null;

      while ((curNode = (Node) tw.getNext()) != null) {
         if (curNode.getNodeType() == Node.ELEMENT_NODE
                 && curNode.getNodeName().equalsIgnoreCase("database:url")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               url = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:username")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               username = curNode.getFirstChild().getNodeValue();
            }
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:password")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
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
         }
         else if (curNode.getNodeType() == Node.ELEMENT_NODE
                  && curNode.getNodeName().equalsIgnoreCase("database:connectionkey")) {
            if (curNode.getFirstChild().getNodeType() == Node.TEXT_NODE) {
               connectionkey = curNode.getFirstChild().getNodeValue();
            }
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
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getUrl() {
      return url;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getUsername() {
      return username;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getPassword() {
      return password;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getInteraction() {
      return interaction;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getCommand() {
      return command;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getConnectionkey() {
      if (connectionkey.equals("")) {
         connectionkey = username + "::" + url;
      }

      return connectionkey;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public long getConnectionlifespan() {
      return connectionlifespan;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public boolean getConfirmation() {
      return confirmation;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getDocumentrootnode() {
      return documentrootnode;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public String getRowrootnode() {
      return rowrootnode;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @see
    */
   public int getRowlimit() {
      return rowlimit;
   }

}







/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

