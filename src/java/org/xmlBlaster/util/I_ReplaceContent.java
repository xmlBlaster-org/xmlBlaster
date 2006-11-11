/*------------------------------------------------------------------------------
Name:      I_ReplaceContent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.Map;

/**
 * Callback interface to replace a byte[], useful for example when publishing in 
 * stream mode where you need to modify each chunk. 
 */
public interface I_ReplaceContent {
   /**
    * Replaces or modifies the oldcontent. The returned instance can be the same as 
    * passed as the oldContent or it can be a new instance.
    * @param oldContent
    * @param clientProperties the map can either be used as attributes or it can be modified in
    * this method.
    *  
    * @return value
    */
   byte[] replace(byte[] oldContent, Map clientProperties);
}

