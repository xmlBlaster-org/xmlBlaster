package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author  goetzger
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2002/02/08 00:48:19 $)
 */

public interface I_Plugin
{
   /**
    * This method is called by the PluginManager.
    * <p/>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws XmlBlasterException;

   public String getType();
   public String getVersion();
}
