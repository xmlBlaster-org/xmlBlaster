package org.xmlBlaster.util.plugin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author  goetzger
 * @version $Revision: 1.1 $ (State: $State) (Date: $Date: 2002/08/26 09:01:32 $)
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
    *   options[2]=DEFAULT_MIN_LEN
    *   options[3]=20
    * </pre>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException;

   public String getType();
   public String getVersion();
}
