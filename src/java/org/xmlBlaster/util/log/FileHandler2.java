/*------------------------------------------------------------------------------
Name:      FileHandler2.java
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

/**
 * The only purpose of this class is to support a second
 * file handler configuration in logging.properties
 * <p>
 * java.util.logging is too limited in its configuration abilities!
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see http://forum.java.sun.com/thread.jspa?threadID=653537&messageID=3841562
 */
public class FileHandler2 extends java.util.logging.FileHandler {
   public FileHandler2() throws java.io.IOException {
      super();
   }
}
