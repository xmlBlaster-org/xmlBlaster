/*------------------------------------------------------------------------------
Name:      I_AttributeTransformer.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.convert;

import java.util.Map;
import java.sql.ResultSet;

import org.xmlBlaster.contrib.I_Info;


/**
 * Supports adding of customized attributes (key/values) to the xml dump. 
 * <p>
 * Example:
 * </p>
 * <pre>
<?xml version='1.0' encoding='UTF-8' ?>
&lt;sql>
 &lt;desc>
  &lt;command>INSERT&lt;/command>
  &lt;ident>AFTN_CIRCUIT_STATE&lt;/ident>
  &lt;colname type='DATE' nullable='0'>DATUM&lt;/colname>
  &lt;colname type='NUMBER' precision='11' nullable='0'>CPU&lt;/colname>
 &lt;/desc>
 &lt;row num='0'>
  &lt;col name='DATUM'>2005-01-05 15:41:36.0&lt;/col>
  &lt;col name='CPU'>238089&lt;/col>
  <font color="red">&lt;attr name='SUBNET_ID'>TCP&lt;/attr>
  &lt;attr name='CIRCUIT'>AAAAAAAAAAAAAAAZ&lt;/attr>
  &lt;attr name='CIRCUIT_STATE'>OPERATIVE&lt;/attr></font>
 &lt;/row>
&lt;/sql>
 *  </pre>
 * <p>
 * The above <tt>attr</tt> tags are created by all <tt>Map</tt> entries
 * returned with the {@link #transform(ResultSet rs, int rowCount)} method.
 * It is up to your plugin to create arbitrary map entries which you want
 * to add.
 * </p>
 *
 * @author Marcel Ruff
 */
public interface I_AttributeTransformer {
   /**
    * Is called after construction. 
    * @param info The configuration environment
    * @throws Exception
    */
   void init(I_Info info) throws Exception;
   
   /**
    * Customized transformation to key/values from a given JDBC result set. 
    * <p>
    * This is called from <tt>I_DataConverter</tt> for each row instance 'rs',
    * you may not call <tt>rs.next()</tt>.
    * </p>
    * @param rs The SQL result
    * @param rowCount -1 when called for the <tt>&lt;desc></tt> section else
    *         it is the current row count of the ResultSet beginning with 0
    * @return If not null your attributes are added to the XML dump,
    *         if null no attributes are added
    * @throws Exception if the plugin desires so
    */
   Map transform(ResultSet rs, int rowCount) throws Exception;

   /**
    * Cleanup resources. 
    * @throws Exception
    */
   void shutdown() throws Exception;
}
