/*------------------------------------------------------------------------------
Name:      I_DataConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.convert;

import java.io.OutputStream;
import java.sql.ResultSet;
import java.util.Map;

import org.xmlBlaster.contrib.I_Info;

/**
 * Interface which hides specific JDBC ResultSet to XML converters. 
 * <p>
 * Principally the output can be, depending on the plugin,
 * any string like XML, HTML, CSV etc.
 * For the generated format consult the implementing plugin.
 * </p>
 * <p>
 * Instances of classes implementing this interface can be reused by calling these tuples:
 * </p>
 * <ol>
 *  <li>setOutputStream(out)</li>
 *  <li>addInfo() - zero to n times</li>
 *  <li>done() to flush the output stream</li>
 * </ol>
 * <p>
 * Plugins are not expected to be thread save, please use separate instances
 * if used by multiple threads.
 * </p>
 *
 * @author Marcel Ruff
 */
public interface I_DataConverter
{
   /**
    * Possible settings for <tt>what</tt> argument of {@link #addInfo(ResultSet, int)}.
    * Deliver meta info and data itself
    */
   public static int ALL = 0;

   /**
    * Possible settings for <tt>what</tt> argument of {@link #addInfo(ResultSet, int)}.
    * Deliver meta info only
    */
   public static int META_ONLY = 1;
   
   /**
    * Possible settings for <tt>what</tt> argument of {@link #addInfo(ResultSet, int)}.
    * Deliver data only
    */
   public static int ROW_ONLY = 2;
   
   /**
    * The Base64 encoding marker string
    */
   public static String BASE64 = "base64";
   
   /**
    * Needs to be called after construction. 
    * @param info The configuration environment
    * @throws Exception
    */
   void init(I_Info info) throws Exception;
   
   /**
    * This has to be called before the first {@link #addInfo(Map)}
    * or  {@link #addInfo(ResultSet, int)} call.  
    * @param out The stream to dump the converted data to
    * @param command An optional command string or null
    * @param ident An optional identifier or null
    * @throws Exception of any type
    */
   void setOutputStream(OutputStream out, String command, String ident) throws Exception;

   /**
    * Add a map with attributes to the XML string. 
    * This is usually called by the {@link org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer} class. 
    * @param attributeMap A map containing key/values to dump
    * @throws Exception of any type
    */
   void addInfo(Map attributeMap) throws Exception;
   
   /**
    * Add another result set to the XML string.
    * @param rs The JDBC result set
    * @param what One of {@link #ALL}, {@link #META_ONLY} or 
    *        {@link #ROW_ONLY}. 
    *        {@link #META_ONLY} is useful for an empty table where rs access fails.
    * @throws Exception of any type 
    */
   void addInfo(ResultSet rs, int what) throws Exception;
   
   /**
    * After the last <tt>addInfo()</tt> call this method to complete the XML dump. 
    * The user needs to call {@link #setOutputStream} again to reuse
    * this instance.
    * @return Number of processed ResultSets
    * @throws Exception typically java.io.UnsupportedEncodingException, java.io.IOException
    */
   int done() throws Exception;
   
   /**
    * Cleanup resources.
    * @throws Exception of any type 
    */
   void shutdown() throws Exception;
}

