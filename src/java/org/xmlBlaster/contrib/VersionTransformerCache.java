/*------------------------------------------------------------------------------
Name:      ReplicationConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
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
   
   public void verifyTransformerName(String xslFile) {
      // TODO: check on startup if XSL file exists
   }

   /**
    * 
    * @param filename
    * @return
    */
   private synchronized Transformer getTransformerAlsoFromCache(String filename, String secondChoice, String thirdChoice, ClassLoader cl) {
      Transformer ret = (Transformer)this.transformers.get(filename);
      if (ret != null)
         return ret;

      if (this.checkedTransformers.contains(filename))
         return null;
      try {
         ret = newTransformer(filename, cl);
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
            ret = getTransformerAlsoFromCache(secondChoice, thirdChoice, null, cl);
            if (ret != null)
               return ret;
            else {
               if (thirdChoice != null) {
                  ret = getTransformerAlsoFromCache(thirdChoice, null, null, cl);
               }
            }
         }
      }
      if (ret != null)
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


   private static Transformer newTransformer(String systemId, String xslString, URIResolver uriResolver, Map params) throws Exception {
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
       
   private static Transformer newTransformer(String filename, ClassLoader cl) throws Exception {
      final String systemId = null;
      final URIResolver uriResolver = null;
      final Map map = null;
      String xslt = getXslStringFromFile(filename, cl);
      return newTransformer(systemId, xslt, uriResolver, map);
   }

   private byte[] doXSLTransformation(String filename, String secondChoice, String thirdChoice, byte[] in, ClassLoader cl) throws Exception {
      Transformer transformer = getTransformerAlsoFromCache(filename, secondChoice, thirdChoice, cl);
      if (transformer == null) {
         log.warning("Transformer for file '" + filename + "' not found (where second choice was '" + secondChoice + "' and third choice was '" + thirdChoice +  "', will return it without modification");
         return in;
      }
      else {
         StreamSource xmlStreamSource = new StreamSource(new ByteArrayInputStream(in));
         ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
         StreamResult resultStream = new StreamResult(baos);
         transformer.transform(xmlStreamSource, resultStream);
         return baos.toByteArray();
      }
   }
   
   public InputStream doXSLTransformation(String filename, InputStream in,
                  ClassLoader cl) throws Exception {
      // TODO: Add a cache!
      Transformer transformer = newTransformer(filename, cl);
      if (transformer == null) {
         log.warning("Transformer for file '" + filename + "' not found");
         return in;
      } else {
         StreamSource xmlStreamSource = new StreamSource(in);
         ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
         StreamResult resultStream = new StreamResult(baos);
         transformer.transform(xmlStreamSource, resultStream);
         ByteArrayInputStream bin = new ByteArrayInputStream(baos.toByteArray());
         return bin;
      }
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
      String strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(text, "/", "");
      // JMX does not like commas, but we can't introduce this change in 1.0.5
      // as the persistent queue names would change and this is not backward compatible
      //strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, ",", "_");
      strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, " ", "_");
      strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, ".", "_");
      strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, ":", "_");
      strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, "[", "_");
      strippedId = org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, "]", "_");
      return org.xmlBlaster.util.ReplaceVariable.replaceAll(strippedId, "\\", "");
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
   public byte[] transform(String replPrefix, String srcVersion, String destVersion, String destination, byte[] srcData, ClassLoader cl) throws Exception {
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
      String ret = replicationPrefix.substring(pos + ReplicationConstants.VERSION_TOKEN.length());
      
      pos = ret.lastIndexOf(ReplicationConstants.DUMP_POSTFIX);
      if (pos < 0)
         return ret.trim();
      ret = ret.substring(0, pos);
      return ret.trim();
   }
   
   public static String buildFilename(String replicationPrefix, String version) {
      return replicationPrefix + ReplicationConstants.VERSION_TOKEN + version + ReplicationConstants.DUMP_POSTFIX;
   }
   
}
