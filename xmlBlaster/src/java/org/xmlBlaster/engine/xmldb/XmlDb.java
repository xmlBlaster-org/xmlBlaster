/*------------------------------------------------------------------------------
Name:      XmlDb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Store MessageUnits in a File-database or holds in a Cache
Version:   $Id $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb;

import org.w3c.dom.*;

import java.util.Enumeration;
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

public class XmlDb
{
    private static final String ME = "XmlDb";

    private static long _cacheSize = 0L;
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
    public final String insert(MessageUnit mu, boolean isDurable)
    {
       if(mu == null){
         Log.error(ME + ".insert", "The arguments of insert() are invalid (null)");
      }

      PMessageUnit pmu = new PMessageUnit(mu,isDurable);

      // Check if key exists in cache
      if(_cache.keyExists(pmu.oid)){
         return pmu.oid;
      }

      // write MessageUnit to Cache
      _cache.write(pmu);

      // Insert key to DOM
      _pdomInstance.insert(mu);
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
       Enumeration muArr = null;
       return muArr;
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
}


