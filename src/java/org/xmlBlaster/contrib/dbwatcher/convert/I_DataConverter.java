/*------------------------------------------------------------------------------
Name:      I_DataConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.convert;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;

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
    * Used to pass the information from the converter to the publisher that this message
    * shall not be sent (i.e. published).
    */
   public static String IGNORE_MESSAGE = "_ignore_this_message";
   
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
    * @param ident the identity or optionally null.
    * @param event The ChangeEvent associated to this invocation. Is never null. 
    * 
    * @throws Exception of any type
    */
   void setOutputStream(OutputStream out, String command, String ident, ChangeEvent event) throws Exception;

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
   void addInfo(Connection conn, ResultSet rs, int what) throws Exception;
   
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

   /**
    * Gets the statement (if any) to be executed after processing one message.In case of the DbWatcher to 
    * be used to send/publish messages on detected changes, this can be used to delete entries in a queue.
    * In such cases, after having sent the message, this post statement is executed by the DbWatcher. If the
    * message could not be sent, this post statement is not invoked.
    * 
    * @return the String containing an sql statement to be executed shortly after the processed message
    * has been finished. In normal cases this means after having published the change message. 
    */
   String getPostStatement();
   
   /**
    * Returns the size of the message as it is at the current moment
    * 
    * @return
    */
   long getCurrentMessageSize();
}

