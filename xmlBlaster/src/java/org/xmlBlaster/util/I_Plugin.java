package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author  goetzger
 * @version $Revision: 1.3 $ (State: $State) (Date: $Date: 2002/03/15 12:54:56 $)
 */

public interface I_Plugin
{
   /**
    * This method is called by the PluginManager.
    * <p/>
    * Example how options are evaluated:
    * <pre>
    *   MimeSubscribePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20
    *
    *   options[0]=DEFAULT_MAX_LEN
    *   options[1]=200
    *   options[2]=DEFAULT_MAX_LEN
    *   options[3]=20
    * </pre>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws XmlBlasterException;

   public String getType();
   public String getVersion();
}
