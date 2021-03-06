/*------------------------------------------------------------------------------
Name:      I_Selector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.lexical;

import java.util.Map;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ClientProperty;


/**
 * I_Selector
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_Selector {
   
   public boolean select(String query, Map<String, ClientProperty> clientProperties) throws XmlBlasterException;

}
