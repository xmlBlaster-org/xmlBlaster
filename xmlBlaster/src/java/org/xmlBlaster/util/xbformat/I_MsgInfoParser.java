/*------------------------------------------------------------------------------
Name:      I_MsgInfoParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Hides message serialization parsers
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import java.io.IOException;
import java.io.InputStream;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_PluginConfig;

/**
 * Interface to support different serialization formats of messages. 
 * Those serialized messages can, for example, 
 * be send over the SOCKET protocol or
 * as email attachments.
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public interface I_MsgInfoParser {
   /**
    * Is guaranteed to be called by the MsgInfoParserFactory after construction. 
    * @param glob
    * @param progressListener
    * @param pluginConfig Can be null
    */
   void init(Global glob, I_ProgressListener progressListener,
         I_PluginConfig pluginConfig) throws XmlBlasterException;
   
   /**
    * Parses a serialized message from input stream
    * @param in The raw, serialized data
    * @return The message read
    * @throws IOException
    * @throws IllegalArgumentException
    */
   MsgInfo parse(InputStream in) throws  IOException, IllegalArgumentException;
   
   /**
    * Dumps the given MsgInfo to a byte array
    * @param msgInfo
    * @return The serializd message
    * @throws XmlBlasterException
    */
   byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException;

   /**
    * Get the raw messages as a string, for tests and for dumping only. 
    * @return The stringified message, null bytes are replaced by '*'
    */
   String toLiteral(MsgInfo msgInfo) throws XmlBlasterException;

   /**
    * Get the raw messages as a string, for tests and for dumping only. 
    * @param arr The raw blob
    * @return The stringified message, null bytes are replaced by '*'
    */
   String toLiteral(byte[] arr);

   /**
    * Get a specific extension for this format. 
    * @return For example 
    *  XBFORMAT_MIMETYPE = "application/xmlBlaster-xbf" or
    *  XBFORMAT_MIMETYPE_ZLIB = "application/xmlBlaster-xbfz"
    */
   String getMimetype(boolean isCompressed);

   /**
    * Get a specific extension for this format. 
    * @return For example XBFORMAT_EXTENSION = ".xbf" or XBFORMAT_ZLIB_EXTENSION = ".xbfz"
    */
   String getExtension(boolean isCompressed);
}
