/*------------------------------------------------------------------------------
Name:      I_SubscribeFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_SubscribeFilter.java,v 1.1 2002/03/14 18:34:02 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.Global;


/**
 * This interface hides the real protocol used to check messages. 
 * <p />
 * The interface may be used to filter subscribed messages.
 * Only messages where the match() method returns true
 * are sent via update() to the client
 * <p />
 * Note that you can manipulate the content of a message with your plugin
 * but changing the XmlKey or QoS is not allowed.
 * <p />
 * The plugin with your filter rules must implement this interface.
 * <p />
 * Steps to add a new plugin:
 * <ul>
 *    <li>Code the plugin.<br />
 *        Code a plugin which inherits from this interface.
 *        In the match() method code your specific filter rule.
 *        You get the MessageUnit object, which contains the content
 *        of a message. You look into the content and decide if the
 *        message matches your rule or not.
 *    </li>
 *    <li>Register the plugin.<br />
 *        Register the plugin in xmlBlaster.properties file.
 *        The plugin is registered MIME based. A message's MIME
 *        (contentMime='text/plain' contentMimeExtended='1.0')
 *        is used to get the correct plugin.
 *    </li>
 * </ul>
 *
 * @version $Revision: 1.1 $
 * @author ruff@swand.lake.de
 */
public interface I_SubscribeFilter
{
   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob);

   /** Get a human readable name of this filter implementation */
   public String getName();

   /** Get the content MIME type for which this plugin applies */
   public String getMime();

   /** Get the content MIME version number for which this plugin applies */
   public String getMimeExtended();

   /**
    * Check if the filter rule matches for this message. 
    * @return true If the filter matches this message, else false
    * @param msgUnit The message to check
    * @param query   The query string containing the filter rule on subscribe usually
    *                the client defines his own rule which is passed here.<br />
    *                null: If for a subscribe() no rule is given, your plugin
    *                      needs to have its own general rule or react how it likes.
    * @exception XmlBlasterException Is thrown on problems, for example if MIME type
    *            does not fit to message content
    */
   public boolean match(MessageUnit msgUnit, FilterRule query) throws XmlBlasterException;
}

