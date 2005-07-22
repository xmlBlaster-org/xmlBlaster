/*------------------------------------------------------------------------------
Name:      I_ReplaceVariable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Callback interface to replace a key from ReplaceVariable. 
 */
public interface I_ReplaceVariable {
   /**
    * Replaces key by value. 
    * @return value
    */
   String get(String key);
}

