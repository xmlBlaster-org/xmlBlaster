/*------------------------------------------------------------------------------
Name:      XmlKeyDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Stores the xmlkey in a DOM.
Version:   $Id: XmlKeyDom.java,v 1.3 2000/12/26 14:56:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb.dom;

import java.util.Enumeration;
import java.io.*;
import java.util.Hashtable;
import java.util.Vector;

import org.w3c.dom.*;

import org.xmlBlaster.util.*;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.persistence.PMessageUnit;

import com.jclark.xsl.om.*;
import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;
import com.fujitsu.xml.omquery.DomQueryMgr;
import com.fujitsu.xml.omquery.JAXP_ProcessorImpl;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;


public class XmlKeyDom
{

   private static final String ME = "XmlKeyDom";

   private  Hashtable _oidTable;
   private  XmlToDom _xmlToDom;

   private  org.w3c.dom.Document _xmlBlasterDoc;
   private  org.w3c.dom.Node     _rootNode;


   public XmlKeyDom()
   {
      if (Log.CALL) Log.call(ME, "Entering XmlKeyDom ...");
      try{
       /* Instantiate the Factory  */
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

         /* Get a Parser, which is defined in the system-properties */
         DocumentBuilder builder = factory.newDocumentBuilder();

         /* Create a new Document, which is w3c conform */
         _xmlBlasterDoc = builder.newDocument();

      }catch(ParserConfigurationException pc){
         Log.error(ME, "ParserConfigurationException: " + pc.toString());
      }

      _oidTable      = new Hashtable();

      _xmlToDom      = new XmlToDom(this);

      /** Create DOM-Key-Tree  */
      _rootNode = (org.w3c.dom.Node)_xmlBlasterDoc.createElement("xmlBlaster");

      /* Add the root node to the XmlDocument */
      _xmlBlasterDoc.appendChild(_rootNode);
   }

   /**
    * Insert a MessageUnit to xmldb.
    * <br>
    * @param pmu  MessageUnit wrapper for persistence.
    */
   public final void insert(PMessageUnit pmu) throws XmlBlasterException
   {

      OidInfo oidInfo = null;
      if(!keyExists(pmu.oid)){
         oidInfo = new OidInfo(pmu.oid);
         _oidTable.put(pmu.oid, oidInfo);
      }else{
         if (Log.TRACE) Log.trace(ME, "pmu.oid=" + pmu.oid + " existed already");
         return;
      }

      // Insert key to DOM
      try
      {
         oidInfo.setNode(_xmlToDom.parse(pmu.msgUnit.xmlKey, _xmlBlasterDoc));
      }catch(Exception e){
         throw new XmlBlasterException(ME,e.toString());
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


   public final void  delete(String oid) throws XmlBlasterException
   {
      /** Delete Key from DOM **/

     OidInfo oidInfo = (OidInfo)_oidTable.get(oid);

     if( oidInfo != null )
     {
        try{
           if (Log.TRACE) Log.trace(ME, "delete: " + oidInfo.getNode());
           _rootNode.removeChild(oidInfo.getNode());
        }catch(Exception e){
           Log.warn(ME, "removeChild(" + oid + " [" + oidInfo.getNode() + "]): " + e.toString());
           throw new XmlBlasterException(ME, "removeChild(" + oid + "): " + e.toString());
        }
        finally{
           _oidTable.remove(oid);
        }

     }else{
        Log.error(ME,"Trying to remove unknown oid="+oid);
     }
   }


   // query with your QUERY language e.g. XPATH and gets a Enumeration of Nodes
   public final Enumeration query(String queryString) throws XmlBlasterException
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

         /* TODO: in future porting to xmlBlaster namespace */
         //NamespacePrefixMap nspm = query_mgr.getEmptyNamespacePrefixMap();
         //nspm = nspm.bind("xmlBlaster", "http://www.xmlBlaster.org/");

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
         org.w3c.dom.Node keyNode = null;

         keyNode = getKeyNode(node);

         String oid = getOid((Element)keyNode);

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
         Log.warn(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();      // com.sun.xml.tree.ElementNode: getLocalName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specially handled
         Log.warn(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
         throw new XmlBlasterException(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
      }

      // check if we have found the <documentRoot><xmlBlaster><key oid=''> element
      boolean foundKey = false;
      if (nodeName.equals("key")) {
         org.w3c.dom.Node parent = node.getParentNode();
         if (parent == null) throw new XmlBlasterException(ME+".InvalidDom", "DOM tree is invalid");

         if (parent.getParentNode().getParentNode() == null)
            foundKey = true;
      }

      if (!foundKey) {
         return getKeyNode(node.getParentNode()); // w3c: getParentNode() sun: getParentImpl()
      }

      return node;
   }

   /* Precondition: element must be the key */
   private String getOid(org.w3c.dom.Element elem) throws XmlBlasterException
   {
      org.w3c.dom.Attr nodeAttr = elem.getAttributeNode("oid");
      if (nodeAttr  == null)
      {
          throw new XmlBlasterException(ME,"Internal Errror oid is null");
      }
      try{
         return nodeAttr.getValue();
      }catch(DOMException ex){
         throw new XmlBlasterException(ME,ex.toString());
      }
   }

}

class OidInfo
{
   private String _oid;
   private org.w3c.dom.Node _node;

   public OidInfo(String oid)
   {
      _oid = oid;
   }

   public final org.w3c.dom.Node getNode()
   {
      return _node;
   }

   public final void setNode(org.w3c.dom.Node node)
   {
      _node = node;
   }
}
