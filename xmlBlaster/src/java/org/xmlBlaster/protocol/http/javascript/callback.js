/*------------------------------------------------------------------------------
Name:      callback.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript callback objects for xmlBlaster
Author:    ruff@swand.lake.de
Version:   $Id: callback.js,v 1.2 2000/02/21 11:30:25 ruff Exp $
------------------------------------------------------------------------------*/

// First define the usual xmlBlaster access methods
// @see xmlBlaster.idl

var loginName = "";
var isLoggedIn = false;

/**
 * Called from controlFrame when the user clicks on Login button
 */
function login(login, passwd)
{
   loginName = login;
   top.target = "callbackFrame";
   top.location.href = "/servlet/Callback?ActionType=login&loginName=" + loginName + "&passwd=" + passwd;
   log.info("Leaving login...");
   return true;
}


function logout()
{
   loginName = "";
   isLoggedIn = false;
   // self.onerror = default???;
   self.close();
   self = null;
}

// @param message An instance of MessageWrapper
function publish(message)
{
   log.error("Publish implementation to xmlBlaster is missing");
}

function get(xmlKey, qos)
{
   log.error("Get implementation to xmlBlaster is missing");
}

function erase(xmlKey, qos)
{
   log.error("Erase implementation to xmlBlaster is missing");
}


/**
 * Constructor for a XmlKey helper object.
 * If you have own meta data, add it with the method wrap:
 * Example: var key = new top.PublishKeyWrapper(null, "text/xml", null);
 *          key.wrap("<Name id='Joe' />");
 * @param oid:String The unique message identifier, is optional and will be generated if null
 * @param contentMime:String The MIME type of the content e.g. "text/xml" or "image/gif"
 * @param contentMimeExtended:String Use it for whatever, e.g. the version number or parser
 *        infos for your content set to null if not needed
 */
function PublishKeyWrapper(oid, contentMime, contentMimeExtended)
{
   if (oid == null)
      this.oid = '';
   else
      this.oid = oid;

   if (contentMime == null)
      this.contentMime = "text/plain";
   else
      this.contentMime = contentMime;

   this.contentMimeExtended = contentMimeExtended;
   this.wrap = PublishKeyWrapperWrap;
   this.toXml = PublishKeyWrapperToXml;
}
function PublishKeyWrapperToXml()
{
   var str='';
   str += "<key oid='" + this.oid + "'";
   str += " contentMime='" + this.contentMime + "'";
   if (this.contentMimeExtended != null)
      str += " contentMimeExtended='" + this.contentMimeExtended + "'";
   str += ">\n";
   str += this.clientTags;
   str += "\n</key>";
   log.info(str);
   return str;
}
function PublishKeyWrapperWrap(tags)
{
   if (tags == null)
      this.tags = '';
   else
      this.tags = tags;
}


/**
 * This class encapsulates the Message meta data and unique identifier of a received message.
 * A typical <b>update</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * This is exactly the key how it was published from the data source.
 * Call updateKey.init(xmlKey_literal); to start parsing the received key
 */
function UpdateKey(xml)
{
   this.oid = null;                 // value from attribute <key oid="...">
   this.contentMime = null;         // value from attribute <key oid="" contentMime="...">
   this.contentMimeExtended = null; // value from attribute <key oid="" contentMimeExtended="...">

   //  Analyzing e.g. <key oid='1234' contentMime='text/plain'>...</key>

   this.root = top.Xparse(xml);     // The Javascript DOM tree
   var keyNode = this.root.contents[0];
   if (keyNode.name != "key") {
      log.warning('Key tag is missing in new arrvied message, received an unknown tag &lt;' + keyNode.name + '>');
      return;
   }
   for(attrib in keyNode.attributes) {
      // log.info('Processing ' + attrib + '="' + keyNode.attributes[attrib] + '" ...');
      if (attrib == "oid")
         this.oid = keyNode.attributes[attrib];
      else if (attrib == "contentMime")
         this.contentMime = keyNode.attributes[attrib];
      else if (attrib == "contentMimeExtended")
         this.contentMimeExtended = keyNode.attributes[attrib];
   }
}
function UpdateQos(xml)
{
   this.sender= null;               // Who sent the message (his login name)?

   this.root = top.Xparse(xml);     // The Javascript DOM tree
   var qosNode = this.root.contents[0];
   if (qosNode.name != "qos") {
      log.warning('Qos tag is missing in new arrvied message, received an unknown tag &lt;' + qosNode.name + '>');
      return;
   }

   //  Analyzing <qos><sender>Fritz</sender></qos>
   for (var i = 0; i < qosNode.contents.length; i++) {
      var tag = qosNode.contents[i];
      if (qosNode.contents[i].type == "element") {
         var name = _strip(tag.name);
         if (name.length < 1)
            continue;
         if (name == "sender")
            this.sender = tag.contents[0].value;
      }
   }
}


/**
 * Create a message object, which contains the xmlBlaster message as string literals
 *
 * @param xmlKey:String  The meta data
 * @param content:String The message itself (binary/octet????!!!)
 * @param qos:String     Some quality of service infos of this xmlBlaster update
*/
function MessageWrapper(xmlKey, content, qos)
{
   if (xmlKey != null) {
      this.xmlKey = xmlKey;
   }
   else {
      var key = new PublishKeyWrapper();
      this.xmlKey = key.toXml();
   }

   this.content = content;

   if (qos != null) {
      this.qos = qos;
   }
   else {
      this.qos = "<qos></qos>";
   }
}

//----------------------------------------------------------------------------------------------
// Create a message object, which contains the new received xmlBlaster message
// @param xmlKey:UpdateKey  The meta data
// @param content:String    The message itself (blob/binary/octet???!!!)
// @param qos:UpdateQos     Some quality of service infos of this xmlBlaster update
//----------------------------------------------------------------------------------------------
function UpdateMessageWrapper(xmlKey, content, qos)
{
   if (xmlKey == null) {
      log.error("Please specify an UpdateKey object");
      return;
   }
   this.xmlKey = xmlKey;

   this.content = content;

   if (qos == null) {
      log.error("Please specify an UpdateQos object");
      return;
   }
   this.qos = qos;
}

//----------------------------------------------------------------------------------------------
// add/remove Callback Listener
// every frame can register itself to receive the callbacks
//----------------------------------------------------------------------------------------------
var listenerList = new Array();

function addUpdateListener(listenerFrame)
{
   if (listenerFrame.update == null) {
      log.error("Frame has no method update()");
      return;
   }
   listenerList[listenerList.length] = listenerFrame;
   log.info("Added listener frame");
   return;
}

function removeUpdateListener(listenerFrame)
{
   if (listenerList == null) {
      log.error("listenerList is empty");
      return;
   }
   var tmpArr = new Array(listenerList.length);
   for (var ii=0; ii < listenerList.length; ++ii) {
      if (listenerList[ii] == listenerFrame)
         continue;
      tmpArr[ii] = listenerList[ii];
   }
   log.info("Removed listener frame");
   return;
}

function fireMessageUpdateEvent(message)
{
   for (var ii=0; ii < listenerList.length; ++ii) {
      listenerList[ii].update(message);
   }
}

//----------------------------------------------------------------------------------------------
// This is called by the updateFrame, delivering an object containing
// the xmlKey, content, qos
//----------------------------------------------------------------------------------------------
function update(message)
{
   var key = new UpdateKey(xml);
   // var qos = new UpdateKey(xml);

   log.info("Received update message, dispatching it to " + listenerList.length + " frames");
   for (var ii=0; ii < listenerList.length; ++ii) {
      listenerList[ii].update(message);
   }
   return 0;
}



