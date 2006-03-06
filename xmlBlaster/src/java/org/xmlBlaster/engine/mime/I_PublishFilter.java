/*------------------------------------------------------------------------------
Name:      I_PublishFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to plugin a publish filter
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.ServerScope;


/**
 * This interface hides the real implementation used to intercept published messages. 
 * <p />
 * The interface may be used to filter/check/manipulate messages arriving with publish().
 * Only messages where the intercept() method returns "OK" or "" are accepted and passed
 * to the xmlBlaster core for processing.
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
 *        In the intercept() method code your specific filter rule.
 *        You get passed the MsgUnit object, which contains the content
 *        of a message. You look into the content and decide if you
 *        accept or reject a message. You may manipulate accepted messages.
 *    </li>
 *    <li>Register the plugin.<br />
 *        Register the plugin in xmlBlaster.properties file, for example<br />
 *        MimePublishPlugin[PublishLenChecker][1.0]=org.xmlBlaster.engine.mime.demo.PublishLenChecker,DEFAULT_MAX_LEN=80000,VERBOSE=false
 *    </li>
 * </ul>
 *
 * @version $Revision: 1.4 $
 * @author xmlBlaster@marcelruff.info
 */
public interface I_PublishFilter
{
   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob);

   /** Get a human readable name of this filter implementation */
   public String getName();

   /**
    * Get the content MIME types for which this plugin applies, "*" is for all mime types
    * @return The supported mime types, for example  return { "text/plain", "text/xml", "application/mytext" };
    */
   public String[] getMimeTypes();

   /**
    * Get the content MIME version number for which this plugin applies. The returned String array length must
    * be the same as this of getMimeTypes(), the index corresponds to the index of getMimeTypes().<br />
    * For example "stable" is the extended mime type of "application/mytext" (see getMimeTypes()).
    * @return E.g. a string array like  { "1.0", "1.3", "stable" }
    */
   public String[] getMimeExtended();

   /**
    * Add your business code with your implementation of inercept(). 
    * <p />
    * You may manipulate the content of the message, but not the key and qos or other attributes
    * of the MsgUnit object.
    * @param publisher The subject object describing the publisher
    * @param msgUnit  The message to check
    * @return "" or "OK": The message is accepted<br />
    *         Any other string: The message is rejected and your string is passed back to the publisher.
    * @exception XmlBlasterException Is thrown on problems, for example if the MIME type
    *            does not fit to message content.<br />
    *            Take care throwing an exception, as the
    *            exception is routed back to the publisher.
    *            If the publish() had many messages (a MsgUnit[]), all other messages are lost
    *            as well.
    *            Probably it is best to return 'ERROR' instead and log the situation.
    */
   public String intercept(SubjectInfo publisher, MsgUnit msgUnit) throws XmlBlasterException;

   public void shutdown();
}

