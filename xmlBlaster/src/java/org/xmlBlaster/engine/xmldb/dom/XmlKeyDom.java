/*------------------------------------------------------------------------------
Name:      XmlKeyDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Stores the xmlkey in a DOM. 
Version:   $Id: XmlKeyDom.java,v 1.1 2000/08/29 16:28:51 kron Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb.dom;

import java.util.Enumeration;
import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import org.xmlBlaster.util.*;
import org.jutils.log.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.PMessageUnit;

import org.xmlBlaster.engine.xmldb.*;
import org.xmlBlaster.engine.xmldb.file.*;
import org.xmlBlaster.engine.xmldb.dom.*;
import org.xmlBlaster.engine.xmldb.cache.*;

import com.jclark.xsl.om.*;
import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;
import com.fujitsu.xml.omquery.DomQueryMgr;
import com.fujitsu.xml.omquery.JAXP_ProcessorImpl;

import com.sun.xml.tree.XmlDocument;
import com.sun.xml.tree.ElementNode;
import gnu.regexp.*;

public class XmlKeyDom 
{

   private static final String ME = "XmlKeyDom";
   private static XmlKeyDom _domInstance;
   private static XmlDocument _xmlBlasterDoc;
   private static org.w3c.dom.Node _rootNode;
   private static Vector _oidTable;


   private XmlKeyDom(){
   }

   public static XmlKeyDom getInstance()
   {
      if(_domInstance == null)
      {
         _domInstance  = new XmlKeyDom();
         _xmlBlasterDoc = new XmlDocument();
         _oidTable      = new Vector();

         /** Create DOM-Key-Tree  */
         _rootNode = (org.w3c.dom.Node)_xmlBlasterDoc.createElement("xmlBlaster");
         _xmlBlasterDoc.appendChild(_rootNode);
      }
      return _domInstance;
   }

   /**
    * Insert a MessageUnit to xmldb.
    * <br>
    * @param mu        The MesageUnit
    * @param isDurable The durable-flag makes the MessageUnit persistent
    */
   public final void insert(PMessageUnit pmu)
   {
      if(pmu == null){
         Log.error(ME + ".insert", "The arguments of insert() are invalid (null)");
      }
      if(!keyExists(pmu.oid)){
         _oidTable.addElement(pmu.oid);
      }else{   
         return;
      }

      // Insert key to DOM
      try
      {
         XmlToDom.parse(pmu.msgUnit.xmlKey, _xmlBlasterDoc);
      }catch(Exception e){
         e.printStackTrace();
      }
   }


   public boolean keyExists(String oid)
   {
      return _oidTable.contains(oid);
   }

   public final int getKeyCount()
   {
      NodeList nl = _rootNode.getChildNodes();
      return nl.getLength();
   }

   //updates a node in the XmlKeyDom 
   public final int update(/*Message*/)
   {
      /** Update Key and MessageUnit **/
      return 0; // return a hashkey number of the xmlkey
   }


   public final void  delete(String oid)
   {
      /** Delete Key from DOM **/
      NodeList nl = _rootNode.getChildNodes();
      for(int i=0; i<nl.getLength();i++)
      {
         org.w3c.dom.Node node = nl.item(i);

         ElementNode keyNode = (com.sun.xml.tree.ElementNode)node;
         String nodeString = keyNode.toString();
         String getOid = getOid(nodeString);
         if(oid.equals(getOid))
         {
            _rootNode.removeChild(node);
            break;   
         }
      }
      _oidTable.remove(oid);
   }


   // query with your QUERY language e.g. XPATH and gets a Enumeration of Nodes
   public final Enumeration query(String queryString)
   {
     /** TODO recognize QUERY-TYPE */
      Log.trace(ME,"Querystring : "+queryString);

      if(queryString == null || queryString.equals(""))
      {
         Log.error(ME,"Sorry, can't query, because no query-string was given.");
         return null;
      }

      Enumeration  keys = null;

      try{
         DomQueryMgr query_mgr = new DomQueryMgr(_xmlBlasterDoc);

         NamespacePrefixMap nspm = query_mgr.getEmptyNamespacePrefixMap();
         nspm = nspm.bind("fuj", "http://www.fujitsu.co.jp/");

         keys = query_mgr.getNodesByXPath(_xmlBlasterDoc, queryString);
      }catch(XSLException e){
         Log.error(ME,"Can't query by XPATH because : "+ e.getMessage());
      }

      Vector v = new Vector();

      while(keys.hasMoreElements())
      {
         Object obj = keys.nextElement();
         org.w3c.dom.Node node = (org.w3c.dom.Node)obj;
         String nodeString="";
         com.sun.xml.tree.ElementNode keyNode = null;
         try{
            keyNode = (com.sun.xml.tree.ElementNode)getKeyNode(node);
         }catch(XmlBlasterException e){
            Log.error(ME,e.reason);
         }
         nodeString = keyNode.toString();
         String oid = getOid(nodeString);

         v.addElement(oid);
      }
      return v.elements();
   }


   public org.w3c.dom.Node getRootNode()
   {
      return _rootNode;
   }


   private org.w3c.dom.Node getKeyNode(org.w3c.dom.Node node) throws XmlBlasterException
   {
      if (node == null) {
         Log.warning(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }
      String nodeName = node.getNodeName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specially handled
         Log.warning(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
         throw new XmlBlasterException(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
      }

      if (!nodeName.equals("key")) {
         return getKeyNode(node.getParentNode()); // w3c: getParentNode() sun: getParentImpl()
      }else{
         return node;
      }
   }

   private String getOid(String nodeString)
   {
     RE expression = null;
     String oid = null;
     try{
        expression = new RE("oid=(\'|\"|\\s)(.*)(\'|\")");
        REMatch match = expression.getMatch(nodeString);
        if(match != null)
        {
            /** matches OID pure */
            RE re = new RE("[^oid=\'\"]");
            REMatch[] matches = re.getAllMatches(match.toString());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < matches.length; i++) {
              sb.append(matches[i]);
            }
            oid = sb.toString();

         }else{
            Log.error(ME,"Invalid xmlKey.");
         }

      }catch(REException e){
        Log.error(ME,"Can't create RE."+e.toString());
      }
     return oid;
   }

}
