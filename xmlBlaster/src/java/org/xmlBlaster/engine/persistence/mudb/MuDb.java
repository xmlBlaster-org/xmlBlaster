/*------------------------------------------------------------------------------
Name:      MuDb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Stores MessageUnits in a file-database or holds in a Cache.
Version:   $Id: MuDb.java,v 1.2 2000/09/15 17:16:16 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb;

import org.w3c.dom.*;

import java.util.Enumeration;
import java.util.Vector;
import org.xmlBlaster.util.*;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.PMessageUnit;

import org.xmlBlaster.engine.persistence.mudb.file.*;
import org.xmlBlaster.engine.persistence.mudb.dom.*;
import org.xmlBlaster.engine.persistence.mudb.cache.*;

import com.jclark.xsl.om.*;
import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;
import com.fujitsu.xml.omquery.DomQueryMgr;
import com.fujitsu.xml.omquery.JAXP_ProcessorImpl;

import com.sun.xml.tree.XmlDocument;
import com.sun.xml.tree.ElementNode;

import gnu.regexp.*;

/**
 * This MuDb is a persistence for the xmlBlaster and stores MessageUnits.
 * <p />
 * You can store MessageUnits (Key,Content,QoS) durable controlled by
 * the isDurable-Flag in the Xml-QoS.
 * @see org.xmlBlaster.client.PublishQosWrapper
 * <br />
 * Example: <br />
 * <pre>
 *    MuDb mudb = new MuDb();        // Get a MuDb-instance
 *    mudb.setMaxCacheSize(2000000L);  // The max. cachesize in Byte
 *
 *    PublishQosWrapper qos = new PublishQosWrapper(true); // <qos><isDurable /></qos>
 *    MessageUnit       mu = new MessageUnit(key,content,qos);
 *
 *    mudb.insert(mu);   // Insert the MessageUnit to MuDb.
 *    mudb.get(oid);     // Get the MessageUnit by oid.
 *
 *    Enummeration muIter mudb.query("//key[@oid="100"); // Query by XPATH
 *
 *    mudb.delete(oid);     // Delete MessageUnit from db by oid.
 *
 *    mudb.showCacheState() // Shows you the current state of the cache.
 *
 * </pre>
 */
public class MuDb
{
    private static final String ME = "MuDb";

    private static Cache _cache;
    private static XmlKeyDom _domInstance;

    /**
    * This class store MessageUnits in a File-database or holds in a Cache
    * <p />
    * Configure the MessageUnit database through xmlBlaster.properties
    * The xmldb consists of a LRU-Cache and a File-Database
    */
    public MuDb()
    {
       _domInstance = XmlKeyDom.getInstance();
       _cache = new Cache();
    }


    /**
    * Insert a MessageUnit to MessageUnit-database.
    * <p />
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
         Log.warn(ME,"Can't recognize QoS of this MessageUnit! I set isDurable=false");
      }

      PMessageUnit pmu = new PMessageUnit(mu,isDurable);

      // Check if Xmlkey exists in DOM
      if(_domInstance.keyExists(pmu.oid)){
         return pmu.oid;
      }

      // Insert key to DOM
      _domInstance.insert(pmu);

      // write MessageUnit to Cache
      _cache.write(pmu);

      return null;
    }

    /**
    * Delete a MessageUnit from Cache and File-database by OID.
    * <p />
    * @param oid The oid of the MessageUnit
    */
    public final void delete(String oid)
    {
       _domInstance.delete(oid);
       _cache.delete(oid);
    }

    /**
    * Delete a stored MessageUnit by XmlKey from Cache and File-database by OID.
    * <p />
    * @param xmlkey The XmlKey for deleted MessageUnits
    */
    public final void delete(XmlKey xmlkey)
    {
    }

    /**
    * Updates the MessageUnit in the xmldb.
    * <p />
    * @param mu the new extened MessageUnit
    */
    public final void update(MessageUnit mu)
    {
    }


    /**
    * Query by XPath
    * <p />
    * @param queryString The Query-String formed by XPath
    */
    public Enumeration query(String queryString)
    {
       Enumeration oidIter = _domInstance.query(queryString);
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
    * <p />
    * @param oid The oid of the MessageUnit
    */
    public final PMessageUnit get(String oid)
    {
       PMessageUnit pmu = _cache.read(oid);
       return pmu;
    }

   /**
   * Set the max Cachesize.
   * <p />
   * @param size The Cachesize in Byte
   */
   public void setMaxCacheSize(long size)
   {
      _cache.setMaxCacheSize(size);
   }

   /**
   * Set the max MessageUnit-Size.
   * <p />
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
   * <p />
   * Max. CacheSize, Max. MessageSize, Cacheoverflow, Number of Messages in Cache,
   * Number of Messages isDurable, Number of Messages swapped, current cachesize
   */
   public void showCacheState()
   {
      _cache.statistic();
   }

}
