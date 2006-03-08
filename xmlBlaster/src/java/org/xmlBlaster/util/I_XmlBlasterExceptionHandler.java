/**
 * 
 */
package org.xmlBlaster.util;

/**
 * You can register your implementation to do for example a immediate shutdown
 * if an XmlBlasterException is thrown.
 * @author marcel
 *
 */
public interface I_XmlBlasterExceptionHandler {
   /**
    * This will be called from XmlBlasterException constructor. 
    * @param e The new created exception
    */
   public void newException(XmlBlasterException e);
}
