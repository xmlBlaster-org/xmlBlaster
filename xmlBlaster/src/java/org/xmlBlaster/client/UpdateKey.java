/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with DOM
Version:   $Id: UpdateKey.java,v 1.1 1999/12/09 16:11:39 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier.
 * <p />
 * A typical <b>update</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * This is exactly the key how it was published from the data source.
 *
 * @see org.xmlBlaster.util.UpdateKeyBase
 * <p />
 * see xmlBlaster/src/dtd/UpdateKey.xml
 * <p />
 * see http://www.w3.org/TR/xpath
 */
public class UpdateKey extends org.xmlBlaster.util.XmlKeyBase
{
   private String ME = "UpdateKey";

   public UpdateKey(String xmlKey_literal) throws XmlBlasterException
   {
      super(xmlKey_literal, false);
   }
}
