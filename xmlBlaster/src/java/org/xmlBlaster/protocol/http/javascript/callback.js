/*------------------------------------------------------------------------------
Name:      callback.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript callback objects for xmlBlaster
Author:    konrad.krafft@doubleslash.de ruff@swand.lake.de
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
   Log.info("Leaving login...");
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
   Log.error("Publish implementation to xmlBlaster is missing");
}

function get(key, qos)
{
   Log.error("Get implementation to xmlBlaster is missing");
}

function erase(key, qos)
{
   Log.error("Erase implementation to xmlBlaster is missing");
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
   Log.info(str);
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
 * Call updateKey.init(key_literal); to start parsing the received key
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
      Log.warning('Key tag is missing in new arrvied message, received an unknown tag &lt;' + keyNode.name + '>');
      return;
   }
   for(attrib in keyNode.attributes) {
      // Log.info('Processing ' + attrib + '="' + keyNode.attributes[attrib] + '" ...');
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

   var qosNode;
   for( var i = 0; i < this.root.contents.length; i++ ) {
      qosNode = this.root.contents[i];
      if(qosNode.name == "qos")
         break;
   }
   if (qosNode.name != "qos") {
      Log.warning('Qos tag is missing in new arrvied message, received only unknown tags.');
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
 * example:
      var key = new PublishKeyWrapper();
      var messageWrapperLiteral = new MessageWrapperLiteral(key.toXml(), "Hello World..", "<qos></qos>");

 * @param key:String  The meta data
 * @param content:String The message itself (binary/octet????!!!)
 * @param qos:String     Some quality of service infos of this xmlBlaster update
*/
function MessageWrapperLiteral(key, content, qos)
{
   this.key = key;
   this.content = content;
   this.qos = qos;
}

/**
 * Create a message object, which contains the xmlBlaster message as DOM
 *
 * @param key:Dom  The meta data
 * @param content:String The message itself (binary/octet????!!!)
 * @param qos:Dom     Some quality of service infos of this xmlBlaster update
*/
function MessageWrapperDom(key, content, qos)
{
   this.key             = key;
   this.content         = content;
   this.qos             = qos;
}


var queueing     = false;
var listenerList = new Array();


function FrameMessageQueue( frameHandle )
{
   this.queueTime               = 500;
   this.retries                 = 0;
   this.ready                   = false;
   this.timeOutHandle           = null;
   this.frame                   = frameHandle;
   this.messageQueue            = new Array();
   this.queue                   = queue_;
}

function queue_( message )
{
   //Log.info("Queueing message "+message.key.oid+" in queue "+this.frame.name);
   this.messageQueue[this.messageQueue.length] = message;

   if( !queueing ) {
      this.sendMessagQueue(this.frame.name);
   }

   if( this.messageQueue.length < 10 ) {
      window.clearTimeout( this.timeOutHandle );
      var call = "sendMessageQueue('"+this.frame.name+"')";
      this.timeOutHandle = window.setTimeout( call, this.queueTime );
   }
}

function sendMessageQueue(queueName)
{
   var fmq;
   var i;
   //Select queue by name
   for(i = 0; i < listenerList.length; i++) {
      if( listenerList[i].frame.name == queueName ) {
         fmq = listenerList[i];
         break;
      }
   }

   if(i == listenerList.length) {
      Log.error("Queue '"+queueName+"' not found.");
      return;
   }

   if( fmq.ready ) {
      if( fmq.frame.update != null ) {
         fmq.frame.update( fmq.messageQueue );
         var str = "Update:<br />"; 
         for( var i = 0; i < fmq.messageQueue.length; i++ )
            str += fmq.messageQueue[i].key.oid + "<br />";
      	if(Log.DEBUG) Log.info("Queue["+fmq.frame.name+"]: "+str);
      } 
      else {
      	if(Log.DEBUG) Log.warning("Queue["+fmq.frame.name+"]: frame has no update function.");
      }
      fmq.messageQueue.length = 0;
      fmq.retries = 0;
      return;
   }
   else {
      if(Log.DEBUG) Log.warning("Frame "+fmq.frame.name+" is not ready. Try it again.");
      if( fmq.retries > 200 ) {                            //more than 200*100ms = 20 sec. not availible
         if(Log.DEBUG) Log.warning("Maximum number of retries reached for frame ["+fmq.frame.name+"].");
         fmq.messageQueue.length = 0;
         window.clearTimeout( fmq.timeOutHandle );
         removeUpdateListener( fmq.frame );
         return;
      }
      window.clearTimeout( fmq.timeOutHandle );
      fmq.timeOutHandle = window.setTimeout( "sendMessageQueue('"+queueName+"')", fmq.queueTime );
      fmq.retries++;
   }
}



function setReady( frame, ready )
{
   for( i = 0; i < listenerList.length; i++) {
      if( listenerList[i].frame.name == frame.name ) {
         listenerList[i].ready = ready;
         return;
      }
   }
}


/*
 *
 */
function addUpdateListener( listenerFrame ) {
   if(listenerFrame.update==null) {
      return;
   }

   for( i = 0; i < listenerList.length; i++) {
      if( listenerList[i].frame.name == listenerFrame.name ) {
         return;
      }
   }

   var fmq = new FrameMessageQueue( listenerFrame );
   listenerList[listenerList.length] = fmq;
   return;
}


/*
 *
 */
function removeUpdateListener( listenerFrame ) {
   var i;
   var found = false;
   for( i = 0; i < listenerList.length; i++) {
      if( listenerList[i].frame.name == listenerFrame.name ) {
         break;
         found = true;
      }
   }

   if( !found )
      return;

   removeUpdateListenerAtPos( i );

   return;

}

/*
 *
 */
function removeUpdateListenerAtPos( index ) {
   if(index >= listenerList.length)
      return;

   if(listenerList.length==1 && index==0) {
      listenerList.length=0;
      return;
   }

   for( var j = index; j < listenerList.length-1; j++ ) {
      listenerList[j] = listenerList[j+1];
   }

   listenerList.length -= 1;

   return;
}


/*
 *
 */
function showListener()
{
   var str = "Listener:\n\n";
   for( var i = 0; i < listenerList.length; i++ ) {
      str += "-" + listenerList[i].frame.name + ", ready="+listenerList[i].ready+"\n";
   }
   alert( str );
}


/*
 *
 */
function fireMessageUpdateEvent( message )
{
   for( var i = 0; i < listenerList.length;  ) {
      if (listenerList[i].frame.closed ) {
         Log.warning("Frame has been closed, removing it ...");
         removeUpdateListenerAtPos( i );
         continue;
      }
      i++;
   }

   //showListener();

   for( var i = 0; i < listenerList.length; i++ ) {
      listenerList[i].queue( message );
   }

}


/*
 * This is update-Method which is called by the callback frame.
 * @param updateKey:String
 * @param content:String
 * @param updateQoS:String
 */
function update( updateKey, content, updateQoS)
{
    var updateKey_d     = unescape( updateKey.replace(/\+/g, " ") );
    var content_d       = unescape( content.replace(/\+/g, " ") );
    var updateQoS_d     = unescape( updateQoS.replace(/\+/g, " ") );

   var key = new UpdateKey(updateKey_d);
   var qos = new UpdateQos(updateQoS_d);

   if(Log.DEBUG) Log.info("Update coming in key.oid="+key.oid);
   if(key.contentMimeExtended.lastIndexOf("EXCEPTION") != -1) {
      alert("Exception:\n\n"+content_d );
   }
   else {
      var message = new MessageWrapperDom( key, content, qos );
      fireMessageUpdateEvent(message);
   }

}



function message(msg)
{
   var decoded = unescape( msg.replace(/\+/g, " ") );
   alert( decoded );
}
function error(msg)
{
   var decoded = unescape( msg.replace(/\+/g, " ") );
   alert( "Fehler!\n\n"+decoded );
}

