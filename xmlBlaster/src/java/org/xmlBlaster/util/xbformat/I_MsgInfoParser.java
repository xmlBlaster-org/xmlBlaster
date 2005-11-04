/**
 * 
 */
package org.xmlBlaster.util.xbformat;

import java.io.IOException;
import java.io.InputStream;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author xmlblast
 *
 */
public interface I_MsgInfoParser {
   void init(Global glob, I_ProgressListener progressListener);
   MsgInfo parse(InputStream in) throws  IOException, IllegalArgumentException;
   byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException;
   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   String toLiteral(MsgInfo msgInfo) throws XmlBlasterException;
   String toLiteral(byte[] arr);
   /**
    * Get a specific extension for this format. 
    * @return For example XBFORMAT_EXTENSION = ".xbf";
    */
   String getExtension();
}
