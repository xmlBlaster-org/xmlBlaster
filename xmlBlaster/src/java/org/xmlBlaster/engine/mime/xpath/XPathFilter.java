/*------------------------------------------------------------------------------
Name:      XPathFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with XPath expressions.
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.xpath;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.Global;

import java.util.LinkedList;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;

import org.jaxen.JaxenException;
import org.jaxen.dom.DOMXPath;

/**
 * Filter content on an XPath expression.
 *
 *<p>Filter on the content of a text/xml mime message. The filter will cache the message dom tree it produces, keyd on message oid and message timestamp, and reuse it. For example if there is 1000 subscribers with an XPathFileter, it will not create 1000 DOM trees for each message, but one that will be resused in each match(). The backlog is 10 by default, and old entries will be descarded. This is settable with the paramater <code>engine.mime.xpath.maxcachesize</code>. For example: MimeAccessPlugin[XPathFilter][1.0]=org.xmlBlaster.engine.mime.xpath.XPathFilter,engine.mime.xpath.maxcachesize=20.</p>
 *
 * @author Peter Antman
 * @version $Revision: 1.1 $ $Date: 2002/09/27 07:35:37 $
 */

public class XPathFilter implements I_Plugin, I_AccessFilter {
   public static final String MAX_DOM_CACHE_SIZE = "engine.mime.xpath.maxcachesize";
   public static final String DEFAULT_MAX_CACHE_SIZE = "10";

   private final String ME = "XPathFilter";
   private Global glob;
   private LogChannel log;
   private int maxCacheSize = 10;
   private LinkedList domCache;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("mime");
      log.info(ME, "Filter is initialized, we check xml mime types");

   }


   
   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      Properties prop = pluginInfo.getParameters();
      maxCacheSize = Integer.parseInt( prop.getProperty(MAX_DOM_CACHE_SIZE,
                                                        DEFAULT_MAX_CACHE_SIZE));
      domCache = new LinkedList();
   }

   /**
    * Return plugin type for Plugin loader
    * @return "GnuRegexFilter"
    */
   public String getType() {
      return "XPathFilter";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "GnuRegexFilter"
    */
   public String getName() {
      return "XPathFilter";
   }

   /**
    * Get the content MIME type for which this plugin applies
    * @return "*" This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      String[] mimeTypes = { "text/xml" };
      return mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = { Constants.DEFAULT_CONTENT_MIME_EXTENDED }; // "1.0"
      return mimeExtended;
   }

   /**
    * Check if the filter rule matches for this message.
    *
    * <p>The dom tree generated will be cached for each message, to be used for other queries against the same message.</p>
    * @param publisher The subject object describing the publisher
    * @param receiver The subject object describing the receiver
    * @param msgUnit The message to check
    * @param query   The Query instance holding the xpath expression from your filter.<br />
    * @return true   The filter xpath expression matches the message content.
    * @exception XmlBlasterException Is thrown on problems, for example if the MIME type
    *            does not fit to message content.<br />
    *            Take care throwing an exception, as the
    *            exception is routed back to the publisher. Subscribers which where served before
    *            may receive the update, subscribers which are served after us won't get it.
    *            For the publisher it looks as if the publish failed completely. Probably it is
    *            best to return 'false' instead and log the situation.
    */
   public boolean match(SessionInfo publisher, SessionInfo receiver, MessageUnitWrapper msgUnitWrapper, Query query) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal argument in xpath match() call");
      }
      
      DOMXPath expression;
      if (query.getPreparedQuery() == null) {
         try {
            expression = new DOMXPath(query.getQuery());
            query.setPreparedQuery(expression); 
         } catch (JaxenException e) {
            log.error(ME, "Can't compile XPath filter expression '" + query + "':" + e.toString());
            throw new XmlBlasterException(ME, "Can't compile XPath filter expression '" + query + "':" + e.toString());
         }
      }
      else
         expression = (DOMXPath)query.getPreparedQuery();
      
      Document doc = getDocument(msgUnitWrapper);
      
      try {
         if ( log.DUMP)
            log.dump(ME,"Matching query " + query.getQuery() + " against document: " + msgUnitWrapper.getMessageUnit().getContentStr());
         
         boolean match = expression.booleanValueOf(doc);
         if (log.TRACE )
            log.trace(ME,"Query "+query.getQuery()+" did" + (match ? " match" : " not match"));
         
         return match;
      }catch (JaxenException e) {
         log.error(ME, "Error in querying dom tree with query " + query + ": " + e.toString());
         throw new XmlBlasterException(ME, "Error in querying dom tree with query " + query + ": " + e.toString());
      }
   }
   
   public void shutdown() {
   }
   
   /**
    * Get a dom document for message, from cache or create a new one.
    */
   
   private synchronized Document getDocument(MessageUnitWrapper msg) throws XmlBlasterException {
      /*
      log.trace(ME,"Number of times qued: " + msg.getEnqueueCounter());
      log.trace(ME,"Timestamp: " +msg.getRcvTimestamp());
      log.trace(ME,"Unique key: " +msg.getUniqueKey());
      log.trace(ME,"Key: " +msg.getXmlKey().toXml());
      log.trace(ME,"Message: "+msg.getMessageUnit().getContentStr());
      */
      Document doc = null;
      String key = msg.getUniqueKey()+":"+msg.getRcvTimestamp().getTimestamp();
      // try get document from cache
      int index = domCache.indexOf(new Entry(key,null));
      if ( index != -1) {
         if (log.TRACE )log.trace(ME,"Returning doc from cache with key: " +key);
         Entry e = (Entry)domCache.get(index);
         doc =  e.doc;
      } else {
         if (log.TRACE )log.trace(ME,"Constructing new doc from with key: " +key);
         doc = getDocument(msg.getMessageUnit().getContentStr());
         
         // Put into cache and check size
         Entry e = new Entry(key,doc);
         domCache.addFirst(e);
         if ( domCache.size() >= maxCacheSize) {
            domCache.removeLast();
         } // end of if ()
                  
      } // end of else
      return doc;
   }
   
   /**
    * Create a new dom document.
    * 
    */
   private Document getDocument(String xml) throws XmlBlasterException {
      try {   
         java.io.StringReader reader = new java.io.StringReader(xml);
         InputSource input = new InputSource(reader);
         DocumentBuilderFactory factory = glob.getDocumentBuilderFactory();
         DocumentBuilder builder = factory.newDocumentBuilder ();
         return builder.parse(input);  
      } catch (org.xml.sax.SAXException ex) {
         String reason = ex.getMessage();
         if(ex instanceof org.xml.sax.SAXParseException) {
            org.xml.sax.SAXParseException s = (org.xml.sax.SAXParseException)ex;
            reason = reason + " at line="+s.getLineNumber() + " column=" +
               s.getColumnNumber() +
               " in systemID" + s.getSystemId();
         }
         throw new XmlBlasterException(ME,"Could not parse xml: " + reason);
      }  catch (javax.xml.parsers.ParserConfigurationException ex) {
         throw new XmlBlasterException(ME,"Could not setup parser " + ex);
      } catch (java.io.IOException ex) {
         throw new XmlBlasterException(ME,"Could not read xml " + ex);
      }
      
   } // end of try-catch

   /**
    * An entry in the domCache.
    */
   class Entry {
      String key;
      Document doc;

      public Entry(String key, Document doc) {
         this.key = key;
         this.doc = doc;
      }

      /**
       * An object is equal if it either an Entry with a key of the same value sa this, or a String with the same value as the key of this object!.
       */
      public boolean equals(Object o) {
         if ( o != null && 
              (o instanceof Entry || o instanceof String)

              ){
            
            String k = (o instanceof String) ? (String)o : ((Entry)o).key;
            
            if ( key.equals(k)) {
               return true;
            } // end of if ()
         }
         return false;
      }
   }
         
}





