/*------------------------------------------------------------------------------
Name:      MsgInfoParserFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.xmlBlaster.util.Global;

/**
 * Creates a parser instance to serialize xmlBlaster messages. 
 * For example the SOCKET xbf (xmlBlaster format) or the XmlScripting format.
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class MsgInfoParserFactory {
   private static MsgInfoParserFactory instance;
   
   public static MsgInfoParserFactory instance() {
      if (instance == null) {
         synchronized (MsgInfoParserFactory.class) {
            if (instance == null) {
               instance = new MsgInfoParserFactory();
            }
         }
      }
      return instance;
   }
   
   private MsgInfoParserFactory() {}

   public I_MsgInfoParser getMsgInfoParser(Global glob, I_ProgressListener progressListener) {
      I_MsgInfoParser msgInfoParser = new XbfParser();
      msgInfoParser.init(glob, progressListener);
      return msgInfoParser;
   }
}
