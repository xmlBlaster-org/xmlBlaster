/*------------------------------------------------------------------------------
Name:      ReplicationConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jutils.text.StringHelper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;


/**
 * Holds Transformers cached.
 * 
 * @author Michele Laghi
 */
public class VersionTransformerCache {

   private static Logger log = Logger.getLogger(VersionTransformerCache.class.getName());

   private Map transformers;
   private Set checkedTransformers;
   
   public VersionTransformerCache() {
      this.transformers = new HashMap();
      this.checkedTransformers = new HashSet();
   }

   /**
    * 
    * @param filename
    * @return
    */
   synchronized Transformer getCachedTransformer(String filename, String secondChoice, String thirdChoice, ClassLoader cl) {
      Transformer ret = (Transformer)this.transformers.get(filename);
      if (ret != null)
         return ret;

      if (this.checkedTransformers.contains(filename))
         return null;
      try {
         ret = getTransformer(filename, cl);
      }
      catch (Throwable ex) {
         if (secondChoice == null && thirdChoice == null)
            log.severe("The search for file '" + filename + "' resulted in an exception. " + InfoHelper.getStackTraceAsString(ex));
      }
      this.checkedTransformers.add(filename);
      if (ret != null)
         this.transformers.put(filename, ret);
      else {
         if (secondChoice != null) {
            ret = getCachedTransformer(secondChoice, thirdChoice, null, cl);
            if (ret != null)
               return ret;
            else {
               if (thirdChoice != null) {
                  ret = getCachedTransformer(thirdChoice, null, null, cl);
               }
            }
         }
      }
      this.transformers.put(filename, ret);
      return ret;
   }
   
   /**
    * Note that this method searches for the stripped name in the classpath.
    * 
    * @param filenamePrefix
    * @return
    * @throws Exception
    */
   private static String getXslStringFromFile(String filenamePrefix, ClassLoader extraCl) throws Exception {
      String filename = getStrippedString(filenamePrefix) + ".xsl";
      ClassLoader cl = VersionTransformerCache.class.getClassLoader();
      
      InputStream xslInputStream = cl.getResourceAsStream(filename);
      if(xslInputStream == null) {
          String txt = "The xsl resource '" + filename
              + "' could not be found in the specific classpath: trying the system class loader";
         log.severe(txt);
          xslInputStream = ClassLoader.getSystemResourceAsStream(filename);
          if(xslInputStream == null) {
             if (extraCl != null)
                xslInputStream = extraCl.getResourceAsStream(filename);

             if (xslInputStream == null) {
                txt = "VersionTransformerCache.getXslStringFromFile: The xsl resource '" + filename
                + "' could not be found in the system classpath: check your classpath";
                throw new Exception(txt);
             }
          }
      }

      InputStreamReader reader = new InputStreamReader(xslInputStream);
      BufferedReader br = new BufferedReader(reader);
      StringBuffer buf = new StringBuffer();
      String line = "";

      while((line = br.readLine()) != null)
         buf.append(line).append("\n");
      return buf.toString();
   }


   private static Transformer getTransformer(String systemId, String xslString, URIResolver uriResolver, Map params) throws Exception {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      if (uriResolver != null)
         transformerFactory.setURIResolver(uriResolver);
      StreamSource xslStreamSource = null;
      if(systemId != null)
          xslStreamSource = new StreamSource(new StringReader(xslString), systemId);
      else
          xslStreamSource = new StreamSource(new StringReader(xslString));

      Transformer transformer = transformerFactory.newTransformer(xslStreamSource);
      if(params != null) {
          Iterator iter = params.entrySet().iterator();
          while(iter.hasNext()) {
              Map.Entry entry = (Map.Entry)iter.next();
              transformer.setParameter((String)entry.getKey(), (String)entry.getValue());
          }
      }
      return transformer;
   }
       
   private static Transformer getTransformer(String filename, ClassLoader cl) throws Exception {
      final String systemId = null;
      final URIResolver uriResolver = null;
      final Map map = null;
      String xslt = getXslStringFromFile(filename, cl);
      return getTransformer(systemId, xslt, uriResolver, map);
   }

   private String doXSLTransformation(String filename, String secondChoice, String thirdChoice, String xmlLiteral, ClassLoader cl) throws Exception {
      Transformer transformer = getCachedTransformer(filename, secondChoice, thirdChoice, cl);
      StreamSource xmlStreamSource = new StreamSource(new StringReader(xmlLiteral));
      StringWriter stringWriter = new StringWriter();
      StreamResult resultStream = new StreamResult(stringWriter);
      transformer.transform(xmlStreamSource, resultStream);
      return stringWriter.toString();
   }
   
   public synchronized void clearCache() {
      this.checkedTransformers.clear();
      this.transformers.clear();
   }
   
   /**
    * Taken from the Global. 
    * 
    * @param text
    * @return
    */
   public static final String getStrippedString(String text) {
      if (text == null) return null;
      String strippedId = StringHelper.replaceAll(text, "/", "");
      // JMX does not like commas, but we can't introduce this change in 1.0.5
      // as the persistent queue names would change and this is not backward compatible
      //strippedId = StringHelper.replaceAll(strippedId, ",", "_");
      strippedId = StringHelper.replaceAll(strippedId, " ", "_");
      strippedId = StringHelper.replaceAll(strippedId, ".", "_");
      strippedId = StringHelper.replaceAll(strippedId, ":", "_");
      strippedId = StringHelper.replaceAll(strippedId, "[", "_");
      strippedId = StringHelper.replaceAll(strippedId, "]", "_");
      return StringHelper.replaceAll(strippedId, "\\", "");
   }
   
   /**
    * Performs an xslt transformation according to the parameters passed and the stylesheet associated to the combination
    * of such parameters.
    * 
    * @param replPrefix The replication prefix used.
    * @param srcVersion The version is actual on the source
    * @param destVersion The version which is wanted on the destination.
    * @param destination The Session name of the destination (can also be a subject name)
    * @param srcData The source string to be transformed.
    * @param cl can be null. Used to find the xsl resources (this is an additional class loader to use)
    * @return
    * @throws Exception
    */
   public String transform(String replPrefix, String srcVersion, String destVersion, String destination, String srcData, ClassLoader cl) throws Exception {
      String thirdChoice = replPrefix + "_" + srcVersion + "_" + destVersion;
      int pos = destination.lastIndexOf("/");
      String secondChoice = null;
      String firstChoice = null;
      if (pos < 0) {
         secondChoice = thirdChoice + "_" + destination;
         firstChoice = secondChoice;
      }
      else {
         String pre = destination.substring(0, pos);
         secondChoice = thirdChoice + "_" + pre;
         firstChoice = thirdChoice + "_" + destination;
      }
      return doXSLTransformation(firstChoice, secondChoice, thirdChoice, srcData, cl);
   }

   /**
    * Returns the prefix of the complete name. It only returns null if the input string was null.
    * @param replicationPrefix
    * @return
    */
   public static String stripReplicationPrefix(String replicationPrefix) {
      if (replicationPrefix == null)
         return null;
      int pos = replicationPrefix.lastIndexOf(ReplicationConstants.VERSION_TOKEN);
      if (pos < 0)
         return replicationPrefix;
      String ret = replicationPrefix.substring(0, pos);
      return ret.trim();
   }
   
   /**
    * Can return null if no version token (_Ver_) was found.
    * @param replicationPrefix
    * @return
    */
   public static String stripReplicationVersion(String replicationPrefix) {
      int pos = replicationPrefix.lastIndexOf(ReplicationConstants.VERSION_TOKEN);
      if (pos < 0)
         return null;
      String ret = replicationPrefix.substring(pos+1);
      
      pos = ret.lastIndexOf(ReplicationConstants.DUMP_POSTFIX);
      if (pos < 0)
         return ret.trim();
      ret = ret.substring(0, pos);
      return ret.trim();
   }
   
}
