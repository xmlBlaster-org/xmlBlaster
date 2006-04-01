/*------------------------------------------------------------------------------
Name:      XPathFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with XPath expressions.
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.xpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.property.Args;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XslTransformer;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.ServerScope;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;

import org.jaxen.SimpleFunctionContext;
import org.jaxen.XPathFunctionContext;
import org.jaxen.Function;
import org.jaxen.JaxenException;
import org.jaxen.dom.DOMXPath;

/**
 * Filter content on an XPath expression.
 *
 *<p>Filter on the content of an xml mime message.
 * <br />
 * The applicable mime types for this filter can be specified using the 
 * <code>engine.mime.xpath.types</code> parameter with semi-colon separated mime types
 * e.g. engine.mime.xpath.types=text/xml;image/svg+xml;application/xml
 * <br />
 * The filter will cache the message dom tree it produces,
 * keyd on message oid and message timestamp, and reuse it.
 * For example if there is 1000 subscribers with an XPathFilter,
 * it will not create 1000 DOM trees for each message, but one that will be reused in each match().
 * The backlog is 10 by default, and old entries will be discarded.
 * This is settable with the paramater <code>engine.mime.xpath.maxcachesize</code>.
 * </p>
 * <p>
 * For example:
 * </p>
 * <pre>
 * MimeAccessPlugin[XPathFilter][1.0]=org.xmlBlaster.engine.mime.xpath.XPathFilter,engine.mime.xpath.maxcachesize=20.
 * </pre>
 *
 * <p>
 * Additional xpath functions can be loaded by setting the parameter <code>engine.mime.xpath.extension_functions</code>.
 * For a description of the parameter syntax and implementation requirements, see {@link #loadXPathExtensionFunctions(String) loadXPathExtensionFunctions}
 * </p>
 *
 * @author Peter Antman
 * @author Jens Askengren
 * @author Robert Leftwich <robert@leftwich.info>
 * @author Marcel Ruff
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/mime.plugin.access.xpath.html">The mime.plugin.access.xpath requirement</a>
 */
public class XPathFilter implements I_Plugin, I_AccessFilter {
   public static final String MAX_DOM_CACHE_SIZE = "engine.mime.xpath.maxcachesize";
   public static final String DEFAULT_MAX_CACHE_SIZE = "10";
   public static final String MATCH_AGAINST_QOS = "matchAgainstQos";
   public static final String XSL_CONTENT_TRANSFORMER_FILE_NAME = "xslContentTransformerFileName";
   //public static final String XSL_QOS_TRANSFORMER_FILE_NAME = "xslQosTransformerFileName";
   public static final String XPATH_EXTENSTION_FUNCTIONS = "engine.mime.xpath.extension_functions";
   public static final String XPATH_MIME_TYPES = "engine.mime.xpath.types";

   private final String ME = "XPathFilter";
   private Global glob;
   private static Logger log = Logger.getLogger(XPathFilter.class.getName());
   private int maxCacheSize = 10;
   private LinkedList domCache;
   private String [] mimeTypes;
   private PluginInfo pluginInfo;
   private boolean matchAgainstQos;
   private String xslContentTransformerFileName;
   //private String xslQosTransformerFileName;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob) {
      this.glob = glob;

      log.info("Filter is initialized, we check xml mime types");

   }
   
   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      Properties prop = pluginInfo.getParameters();
      maxCacheSize = Integer.parseInt( prop.getProperty(MAX_DOM_CACHE_SIZE,
                                                        DEFAULT_MAX_CACHE_SIZE));

      loadXPathExtensionFunctions(prop.getProperty(XPATH_EXTENSTION_FUNCTIONS));

      this.matchAgainstQos = Boolean.valueOf(
            prop.getProperty(MATCH_AGAINST_QOS, ""+this.matchAgainstQos)
            ).booleanValue();
      
      this.xslContentTransformerFileName = prop.getProperty(XSL_CONTENT_TRANSFORMER_FILE_NAME, this.xslContentTransformerFileName);
      //this.xslQosTransformerFileName = prop.getProperty(XSL_QOS_TRANSFORMER_FILE_NAME, this.xslQosTransformerFileName);

      domCache = new LinkedList();

      // attempt to get the mime types from the init properties
      String someMimeTypes = prop.getProperty(XPATH_MIME_TYPES, "text/xml;image/svg+xml");
      StringTokenizer st = new StringTokenizer(someMimeTypes, ";");
      ArrayList list = new ArrayList(st.countTokens() + 1);
      while (st.hasMoreTokens()) {
          list.add(st.nextToken());
      }
      mimeTypes = (String[])list.toArray(new String[list.size()]);
   }

   /**
    * Return plugin type for Plugin loader
    * @return "GnuRegexFilter"
    */
   public String getType() {
      return (this.pluginInfo==null) ? "XPathFilter" : this.pluginInfo.getType();
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return (this.pluginInfo==null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "GnuRegexFilter"
    */
   public String getName() {
      return getType(); //"XPathFilter";
   }

   /**
    * Get the content MIME type for which this plugin applies,
    * currently "text/xml" and "image/svg+xml".
    * Is configurable with
    * <tt>engine.mime.xpath.types=text/xml;image/svg+xml;application/xml</tt>
    * @return "*" This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      //String[] mimeTypes = { "text/xml", "image/svg+xml" };
      return mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = { Constants.DEFAULT_CONTENT_MIME_EXTENDED, Constants.DEFAULT_CONTENT_MIME_EXTENDED }; // "1.0"
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
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      if (msgUnit == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal argument in xpath match() call");
      }
      
      try {
         // Access cached query ...
         DOMXPath expression;
         if (query.getPreparedQuery() == null) {
            try {
               expression = new DOMXPath(query.getQuery());
               query.setPreparedQuery(expression); 
            } catch (JaxenException e) {
               log.warning("Can't compile XPath filter expression '" + query + "':" + e.toString());
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Can't compile XPath filter expression '" + query + "'", e);
            }
         }
         else
            expression = (DOMXPath)query.getPreparedQuery();
         
         // Access cached xsl transformation
         XslTransformer xslContentTransformer = null;
         if (this.xslContentTransformerFileName != null) {
            xslContentTransformer = (XslTransformer)query.getTransformer();
            if (xslContentTransformer == null) {
               Map xslProps = new TreeMap(); // TODO: Where to get them from
               xslContentTransformer = new XslTransformer(glob, this.xslContentTransformerFileName, null, null, xslProps);
            }
         }
         
         String xml = getXml(msgUnit).trim(); // Content or QoS
         
         if (xml.length() == 0) {
            log.warning("Provided XML string is empty, query does not match.");
            return false;
         }
         
         Document doc = getDocument(msgUnit);
         
         if ( log.isLoggable(Level.FINEST))
            log.finest("Matching query " + query.getQuery() + " against document: " + getXml(msgUnit));
         
         boolean match = expression.booleanValueOf(doc);
         if (log.isLoggable(Level.FINE))
            log.fine("Query "+query.getQuery()+" did" + (match ? " match" : " not match"));
         
         if (match == true && xslContentTransformer != null) {
            String tmp = (this.matchAgainstQos) ? msgUnit.getContentStr() : xml;
            String ret = xslContentTransformer.doXSLTransformation(tmp);
            msgUnit.setContent(ret.getBytes());
         }
         
         return match;
      }
      catch (JaxenException e) {
         log.warning("Error in querying dom tree with query " + query + ": " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Error in querying dom tree with query " + query, e);
      }
      catch (Throwable e) {
         log.warning("Error in handling XPath filter with query='" + query + "' and xml='" + getXml(msgUnit) + "': " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Error in querying dom tree with query " + query, e);
      }
   }
   
   public void shutdown() {
   }

   /**
    * Load xpath extension functions from a semicolon separated list of classes.
    *
    * <p>
    * <b>List syntax:</b>
    * <code><pre>
    *  function := prefix ":" function-name ":" class-name
    *  extensionClassList := ( function (";" function)* )?
    * </pre></code>
    *
    * The prefix may be the empty string. The class must implement the <code>org.jaxen.Function</code> interface.
    * </p>
    * <p>
    * <b>Example string:</b>
    *
    * <code>engine.mime.xpath.extension_functions=:recursive-text:org.xmlBlaster.engine.mime.xpath.RecursiveTextFunction</code>
    * </p>
    *
    * @param extensionClassList semicolon separated list of function definitions
    * @throws XmlBlasterException if the syntax is incorrect, or the class could not be loaded
    */
    protected void loadXPathExtensionFunctions(String extensionClassList) throws XmlBlasterException {

       if (extensionClassList == null) {
           return;
       }

       StringTokenizer st = new StringTokenizer(extensionClassList, ";");
       while (st.hasMoreTokens()) {
           String t = st.nextToken();

           // prefix : func : class
           int c1 = t.indexOf(":");
           int c2 = t.lastIndexOf(":");

           if (c1 == -1 || c2 == -1 || c1 == c2) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Bad xpath extension function definition: \""
                                             + t + "\". Expected: prefix \":\" function-name \":\" class");
           }

           String prefix = t.substring(0, c1);
           String func = t.substring(c1+1, c2);
           String klass = t.substring(c2+1);

           if (func.length() == 0 || klass.length() == 0) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Bad xpath extension function definition: \""
                                             + t + "\". Expected: prefix \":\" function-name \":\" class");
           }

           try {
               
               Object o = Class.forName(klass).newInstance();
               if (!(o instanceof Function)) {
                   throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Extension function \""
                                                 + klass + "\" does not implement org.jaxen.Function");
               }

               ((SimpleFunctionContext)XPathFunctionContext.getInstance())
                   .registerFunction(("".equals(prefix)?null:prefix), func, (Function)o);

           } catch (Exception e) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME,  "Could not load extension function \""
                                             + klass + "\": " + e.getMessage());
           }
       }
   }

   /**
    * Get a dom document for message, from cache or create a new one.
    */
   
   private synchronized Document getDocument(MsgUnit msg) throws XmlBlasterException {
      /*
      log.trace(ME,"Number of times qued: " + msg.getEnqueueCounter());
      log.trace(ME,"Timestamp: " +msg.getRcvTimestamp());
      log.trace(ME,"Unique key: " +msg.getUniqueKey());
      log.trace(ME,"Key: " +msg.getXmlKey().toXml());
      log.trace(ME,"Message: "+msg.getMessageUnit().getContentStr());
      */
      Document doc = null;
      String key = msg.getKeyOid()+":"+msg.getQosData().getRcvTimestamp().getTimestamp();
      // try get document from cache
      int index = domCache.indexOf(new Entry(key,null));
      if ( index != -1) {
         if (log.isLoggable(Level.FINE))log.fine("Returning doc from cache with key: " +key);
         Entry e = (Entry)domCache.get(index);
         doc =  e.doc;
      } else {
         if (log.isLoggable(Level.FINE))log.fine("Constructing new doc from with key: " +key);
         doc = getDocument(getXml(msg));
         
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
    * Access the XML string (from QoS or content). 
    * @param msg
    * @return Is never null
    */
   private String getXml(MsgUnit msg) {
      if (this.matchAgainstQos)
         return msg.getQos();
      else
         return msg.getContentStr();
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
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Could not parse xml: " + reason);
      }  catch (javax.xml.parsers.ParserConfigurationException ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not setup parser " + ex);
      } catch (java.io.IOException ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Could not read xml " + ex);
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
      
   /**
    * Command line helper to test your XPath syntax. 
    * <p> 
    * Please pass on command line the XML of the message content,
    * you then can interactively test your XPath query.
    * Type 'q' to quit.
    * <p>
    * <tt>
    * export CLASSPATH=$CLASSPATH:$XMLBLASTER_HOME/lib/jaxen.jar
    * <br />
    * java org.xmlBlaster.engine.mime.xpath.XPathFilter -inFile [someFile.xml]
    * <br />
    * java org.xmlBlaster.engine.mime.xpath.XPathFilter -inFile [someFile.xml] -xslContentTransformerFileName [someFile.xsl]
    * <br />
    * Example:<br />
    * cd xmlBlaster/testsuite/data/xml<br/>
    * java org.xmlBlaster.engine.mime.xpath.XPathFilter -inFile Airport.xml -xslContentTransformerFileName transformToKeyValue.xsl
    * </tt> 
    * <p>
    * todo: Using http://jline.sourceforge.net/ for nicer command line input handling
    * <br />Example: 
    * java -cp /opt/download/jline-demo.jar:/opt/download/jline-0_9_5-demo.jar jline.example.Example simple
    * 
    * @param args -inFile [fileName.xml] OR -xml [the xml string]
    */
   public static void main(String[] args) {
      try {
         ServerScope scope = new ServerScope(args);
         Global glob = scope;
         XPathFilter filter = new XPathFilter();
         filter.initialize(scope);
         // check -matchAgainstQos and -xslContentTransformFileName command line settings 
         String xslFile = Args.getArg(args, "-"+XSL_CONTENT_TRANSFORMER_FILE_NAME, (String)null);
         boolean isQos = Args.getArg(args, "-"+MATCH_AGAINST_QOS, false);
         String xml = Args.getArg(args, "-xml", (String)null);
         if (xml == null) {
            String inFile = Args.getArg(args, "-inFile", (String)null);
            if (inFile == null || inFile.length() < 1) {
               System.out.println("\nUsage:  java org.xmlBlaster.engine.mime.xpath.XPathFilter -inFile [someFile]");
               System.exit(1);
            }
            xml = FileLocator.readAsciiFile(inFile);
         }
         String content = (!isQos) ? xml : "";
         String qos = (isQos) ? xml : "<qos/>";
         MsgUnit msgUnit = new MsgUnit("<key oid='Hello'/>", content, qos);
         msgUnit.getQosData().setRcvTimestamp(new Timestamp());
         SessionInfo sessionInfo = null;

         PluginInfo info = new PluginInfo(glob, null, "XPathFilter", "1.0");
         info.getParameters().put(MATCH_AGAINST_QOS, ""+isQos);
         if (xslFile != null)
            info.getParameters().put(XSL_CONTENT_TRANSFORMER_FILE_NAME, xslFile);
         filter.init(glob, info);
         
         BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
         
         System.out.println("The XML to query is");
         System.out.println("==================================");
         System.out.println(xml);
         System.out.println("==================================");
         System.out.println("Enter your xpath like '//a' or type 'q' to quit");
         String line = "";
         while (true) {
            System.out.println("");
            line = ""; // Nice to have: showing old query, this does not work - use jline
            System.out.print("xpath> " + line);
            line = in.readLine(); // Blocking in I/O
            if (line == null) continue;
            line = line.trim();
            if (line.length() < 1) continue;
            Query query = new Query(glob, line);
            if (line.toLowerCase().equals("q") || line.toLowerCase().equals("quit")) {
               System.out.println("Bye");
               System.exit(0);
            }
            try {
               boolean ret = filter.match(sessionInfo, msgUnit, query);
               //System.out.println("Query: " + query.getQuery());
               System.out.println("Match: " + ret);
               if (ret == true && xslFile != null) {
                  System.out.println("Transformed content: " + msgUnit.getContentStr());
               }
            }
            catch (Exception e) { // javap org.jaxen.XPathSyntaxException
               System.out.println(e.toString());
               e.printStackTrace();
            }
         }
      } catch (XmlBlasterException e) {
         log.severe(e.getMessage());
         if (!e.isResource())
            e.printStackTrace();
      }
      catch (IOException e) {
         log.severe(e.toString());
      }
   }
}
