/*------------------------------------------------------------------------------
Name:      XmlDb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Store MessageUnits in a file-database or holds in a Cache.
Version:   $Id: XmlDb.java,v 1.6 2000/08/29 11:17:38 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb;

import org.w3c.dom.*;

import java.util.Enumeration;
import java.util.Vector;
import org.xmlBlaster.util.*;
import org.jutils.log.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.xml2java.XmlKey;
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

/**
 * This XmlDb is a persistence for the xmlBlaster. 
 * <p />
 * You can store MessageUnits (Key,Content,QoS) durable controlled by
 * the isDurable-Flag in the Xml-QoS. @see org.xmlBlaster.client.PublishQosWrapper
 * <br />
 * Example: <br />
 * <pre>
 *    XmlDb xmldb = new XmlDb();        // Get a xmldb-instance 
 *    xmldb.setMaxCacheSize(2000000L);  // The max. cachesize in Byte
 *
 *    PublishQosWrapper qos = new PublishQosWrapper(true); // <qos><isDurable /></qos>
 *    MessageUnit       mu = new MessageUnit(key,content,qos);
 *
 *    xmldb.insert(mu);   // Insert the MessageUnit to xmldb.
 *    xmldb.get(oid);     // Get the MessageUnit by oid.
 *
 *    Enummeration muIter xmldb.query("//key[@oid="100"); // Query by XPATH
 *
 *    xmldb.delete(oid);     // Delete MessageUnit from db by oid.
 *   
 *    xmldb.showCacheState() // Shows you the current state of the cache.
 *
 * </pre>
 */
public class XmlDb 
{
    private static final String ME = "XmlDb";

    private static Cache _cache;
    private static PDOM _pdomInstance;

    /**
    * This class store MessageUnits in a File-database or holds in a Cache
    * <p />
    * Configure the XmlDb through xmlBlaster.properties
    * The xmldb consists of a LRU-Cache and a File-Database
    */
    public XmlDb()
    {
       _pdomInstance = PDOM.getInstance();
       _cache = new Cache();
    }


    public final void recover()
    {
    }

    /**
    * Insert a MessageUnit to xmldb.
    * <br>
    * @param mu        The MesageUnit
    * @param isDurable The durable-flag makes the MessageUnit persistent
    * @return 0  : insert was ok
    *         oid: MessageUnit exists
    */
    public final String insert(MessageUnit mu)
    {
       if(mu == null){
         Log.error(ME + ".insert", "The arguments of insert() are invalid (null)");
      }

      // Is Message durable ?
      boolean isDurable = false;
      try{
         RE expression = new RE("(.*)<isDurable(.*)");
         isDurable = expression.isMatch(mu.qos);
      }catch(REException e){
         Log.warning(ME,"Can't recognize QoS of this MessageUnit! I set isDurable=false");
      }

      PMessageUnit pmu = new PMessageUnit(mu,isDurable);

      // Check if Xmlkey exists in DOM
      if(_pdomInstance.keyExists(pmu.oid)){
         return pmu.oid;
      }

      // Insert key to DOM
      _pdomInstance.insert(pmu);

      // write MessageUnit to Cache
      _cache.write(pmu);

      return null;
    }

    /**
    * Delete a MessageUnit from Cache and File-database by OID.
    * <br>
    * @param oid The oid of the MessageUnit
    */
    public final void delete(String oid)
    {
       _pdomInstance.delete(oid);
       _cache.delete(oid);
    }

    /**
    * Delete a stored MessageUnit by XmlKey from Cache and File-database by OID.
    * <br>
    * @param xmlkey The XmlKey for deleted MessageUnits
    */
    public final void delete(XmlKey xmlkey)
    {
    }

    /**
    * Updates the MessageUnit in the xmldb.
    * <br>
    * @param mu The new MessageUnit
    */
    public final void update(MessageUnit mu)
    {
    }


    /**
    * Query by XPath
    * <br>
    * @param queryString The Query-String formed by XPath
    */
    public Enumeration query(String queryString)
    {
       Enumeration oidIter = _pdomInstance.query(queryString);
       Vector v = new Vector();
       while(oidIter.hasMoreElements())
       {
          String oid = (String)oidIter.nextElement();
          /** Read from Cache */
          PMessageUnit pmu = _cache.read(oid);

          if(pmu != null){
            v.addElement(pmu);
          }

       }
       return v.elements();
    }

    /**
    * Get a MessageUnit from xmldb by oid.
    * <br>
    * @param oid The oid of the MessageUnit
    */
    public final PMessageUnit get(String oid)
    {
       PMessageUnit pmu = _cache.read(oid);
       return pmu;
    }

   /**
   * Set the max Cachesize.
   * <br>
   * @param size The Cachesize in Byte
   */
   public void setMaxCacheSize(long size)
   {
      _cache.setMaxCacheSize(size);
   }

   /**
   * Set the max MessageUnit-Size.
   * <br>
   * @param size The MessageUnit-Size
   */
   public void setMaxMsgSize(long size)
   {
      _cache.setMaxMsgSize(size);
   }

   /**
   * Reset all cache parameters.
   */
   public void resetCache()
   {
      _cache.reset();
   }

   /**
   * Shows all cache parameters.
   * <br />
   * Max. CacheSize, Max. MessageSize, Cacheoverflow, Number of Messages in Cache,
   * Number of Messages isDurable, Number of Messages swapped, current cachesize 
   */
   public void showCacheState()
   {
      _cache.statistic();
   }

}
