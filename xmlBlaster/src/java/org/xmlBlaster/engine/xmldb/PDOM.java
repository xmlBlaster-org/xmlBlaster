package org.xmlBlaster.engine.xmldb;

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

public class PDOM
{

   private static final String ME = "PDOM";
   private static PDOM _pdomInstance;
   private static XmlDocument _xmlBlasterDoc;
   private static org.w3c.dom.Node _rootNode;

   private static long _cacheSize = 0L;
   private static Cache _cache;

   private PDOM()
   {
   }

   public static PDOM getInstance()
   {
      if(_pdomInstance == null)
      {
         _pdomInstance  = new PDOM();
         _xmlBlasterDoc = new XmlDocument();

         /** Create DOM-Key-Tree  */
         _rootNode = (org.w3c.dom.Node)_xmlBlasterDoc.createElement("xmlBlaster");
         _xmlBlasterDoc.appendChild(_rootNode);

         /** Cache */
        _cache = new Cache();
      }
      return _pdomInstance;
   }

   //
   //Xmldb operations with transaction support
   //

   // commits the transaction
   public final void commit(){
   }

   // open xmldb
   public final int open(){
      return 0;
   }

   // close the xmldb session
   public final void close(){
   }


   /**
    * Insert a MessageUnit to xmldb.
    * <br>
    * @param mu        The MesageUnit
    * @param isDurable The durable-flag makes the MessageUnit persistent
    */
   public final void insert(MessageUnit mu, boolean isDurable)
   {
      if(mu == null){
         Log.error(ME + ".insert", "The arguments of insert() are invalid (null)");
      }

      PMessageUnit pmu = new PMessageUnit(mu,isDurable);

      // Check if key exists in cache
      if(_cache.keyExists(pmu.oid)){
         Log.warning(ME,"XmlKey with oid : "+pmu.oid+" exists...");
         return;
      }

      // Insert key to DOM
      try
      {
         XMLtoDOM.parse(mu.xmlKey, _xmlBlasterDoc);
      }catch(Exception e){
         e.printStackTrace();
      }

      // write MessageUnit to Cache
      _cache.write(pmu);
   }


   public final int getKeyCount()
   {
      NodeList nl = _rootNode.getChildNodes();
      return nl.getLength();
   }

   //updates a node in the PDOM
   public final int update(/*Message*/)
   {
      /** Update Key and MessageUnit **/
      return 0; // return a hashkey number of the xmlkey
   }

   
   
   public final void delete(String key)
   {
       /** Delete PMessageUnit in Cache. */
//       _cache.delete(pmu.oid);
   }



   public final void  delete(String oid)
   {
      /** Delete PMessageUnit from Cache and file-database. */
      _cache.delete(oid);
      
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

      /** Read PMessageUnits from Cache and give it back to the engine */
      Enumeration pmus = null;
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

         /** Read from Cache */
         PMessageUnit pmu = _cache.read(oid);
         if(pmu != null){
            v.addElement(pmu);
         }
      }
      pmus = v.elements();

      return pmus;
   }

   public org.w3c.dom.Node getRootNode()
   {
      return _rootNode;
   }

   public Vector getState()
   {
      Vector stateCache = _cache.getCacheState();
      stateCache.addElement(String.valueOf(getKeyCount()));

      return stateCache;
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

}//PDOM
