/*------------------------------------------------------------------------------
Name:      I_ReplaceContent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Callback interface to replace a byte[], useful for example when publishing in 
 * stream mode where you need to modify each chunk. 
 */
public interface I_ReplaceContent {
   /**
    * Replaces or modifies the oldcontent. The returned instance can be the same as 
    * passed as the oldContent or it can be a new instance. 
    * @return value
    */
   byte[] get(byte[] oldContent);
}

