/*------------------------------------------------------------------------------
Name:      I_AccessFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for access plugins
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.mime.Query;


/**
 * This interface hides the real protocol used to check messages. 
 * <p>
 * The interface may be used to filter messages on subscribe() or get() access.
 * Only messages where the match() method returns true
 * are sent via update() to the client
 * </p>
 * <p>
 * Note that you are not allowed to manipulate the content or XmlKey or QoS of a message with your plugin
 * as this would affect all other subscribers (you are working on a reference to the
 * original message).
 * </p>
 * <p>
 * The plugin with your filter rules must implement this interface.
 * </p>
 * Steps to add a new plugin:
 * <ul>
 *    <li>Code the plugin.<br />
 *        Code a plugin which inherits from this interface.
 *        In the match() method code your specific filter rule.
 *        You get passed the MsgUnit object, which contains the content
 *        of a message. You look into the content and decide if the
 *        message matches your rule or not.
 *    </li>
 *    <li>Register the plugin.<br />
 *        Register the plugin in xmlBlaster.properties file, for example<br />
 *        MimeAccessPlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=80000,VERBOSE=false
 *    </li>
 * </ul>
 *
 * @version $Revision: 1.10 $
 * @author xmlBlaster@marcelruff.info
 */
public interface I_AccessFilter
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
    * Check if the filter rule matches for this message. 
    * <p>
    * Note that you are not allowed to manipulate the content or XmlKey or QoS of a message with your plugin
    * as this would affect all other subscribers (you are working on a reference to the
    * original message).
    * You can find out the publisher name like msgUnit.getQosData().getSender()
    * </p>
    * @param receiver The session object describing the receiver, is never null.
    * @param msgUnit  The message to check, is never null.
    * @param query   The query containing the filter rule on subscribe/get usually
    *                the client defines his own rule which is passed here.<br />
    *                null: If for a subscribe() or get() no rule is given, your plugin
    *                      needs to have its own general rule or react how it likes.<br />
    *                Access the raw query string with query.getQuery(), you can parse it
    *                and store the prepared query with query.setPreparedQuery() - query.getPreparedQuery()
    *                to increase performance.
    * @return true If the filter matches this message, else false
    * @exception XmlBlasterException Is thrown on problems, for example if the MIME type
    *            does not fit to message content.<br />
    *            Take care throwing an exception, the message is not updated and an error is logged
    *            and the message is sent as dead letter.
    *            (see TopicHandler.java:1032).
    *            It is best to return 'false' instead and handle the situation yourself.
    */
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException;

   // TODO: have a match() variant for synchronous get() access:
   //   boolean match(SessionInfo session, MsgUnit msgUnit, Query query)
   // and one for subscriptions:
   //   boolean match(SessionInfo session, SubscriptionInfo sub, Query query)
   //        SessionInfo receiver = sub.getSessionInfo()
   //        MsgUnit w = sub.getMsgUnit();

   public void shutdown();
}

