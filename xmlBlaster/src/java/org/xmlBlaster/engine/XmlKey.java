/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKey.java,v 1.7 1999/12/10 15:55:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier.
 * <p />
 * A typical <b>publish</b> key could look like this:<br />
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
 * A typical <b>subscribe</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' queryType='EXACT'>
 *     &lt;/key>
 * </pre>
 * <br />
 * In this example you would subscribe on message 4711.
 * <p />
 * A typical <b>subscribe</b> using XPath query syntax could look like this:<br />
 * <pre>
 *     &lt;key oid='' queryType='XPATH'>
 *        //DRIVER[@id='FileProof']
 *     &lt;/key>
 * </pre>
 * <br />
 * In this example you would subscribe on all DRIVERs which have the attribute 'FileProof'
 * <p />
 * More examples you find in xmlBlaster/src/dtd/XmlKey.xml
 * <p />
 *
 * @see org.xmlBlaster.util.XmlKeyBase
 * @see <a href="http://www.w3.org/TR/xpath">The W3C XPath specification</a>
 */
public class XmlKey extends org.xmlBlaster.util.XmlKeyBase
{
   private String ME = "XmlKey";

   public XmlKey(String xmlKey_literal) throws XmlBlasterException
   {
      super(xmlKey_literal);
   }
   public XmlKey(String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      super(xmlKey_literal, isPublish);
   }
}
