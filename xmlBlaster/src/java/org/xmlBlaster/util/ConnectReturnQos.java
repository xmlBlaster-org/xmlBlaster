package org.xmlBlaster.util;

import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.util.Log;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
//import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This class wraps the result of <code>org.xmlBlaster.authentication.authenticate.connect(...)</code>.
 * <p />
 * Please see documentation at ConnectQos which implements all features.
 * @see org.xmlBlaster.util.ConnectQos
 */
public class ConnectReturnQos extends ConnectQos {
   public static final String ME = "ConnectReturnQos";

   public ConnectReturnQos(String xmlQos_literal) throws XmlBlasterException
   {
      super(xmlQos_literal);
   }
}
