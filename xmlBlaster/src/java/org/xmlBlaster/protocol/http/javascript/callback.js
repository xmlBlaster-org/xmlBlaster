/*------------------------------------------------------------------------------
Name:      callback.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript callback objects for xmlBlaster
Author:    konrad.krafft@doubleslash.de ruff@swand.lake.de
------------------------------------------------------------------------------*/

// First define the usual xmlBlaster access methods
// @see xmlBlaster.idl


/**
 * Logout the nice way.
 */
function xmlBlasterLogout()
{
   top.location.href = "/servlet/BlasterHttpProxyServlet?ActionType=logout";
}

/**
 * @param message An instance of MessageWrapper
 */
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
   if (Log.INFO) Log.info(str);
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
   this.contentMimeExtended = "";   // value from attribute <key oid="" contentMimeExtended="...">

   //  Analyzing e.g. <key oid='1234' contentMime='text/plain'>...</key>

   this.root = top.Xparse(xml);     // The Javascript DOM tree
   var keyNode = this.root.contents[0];
   if (keyNode.name != "key") {
      Log.warning('Key tag is missing in new arrvied message, received an unknown tag &lt;' + keyNode.name + '>');
      return;
   }
   for(attrib in keyNode.attributes) {
      // Log.trace('Processing ' + attrib + '="' + keyNode.attributes[attrib] + '" ...');
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
/** Contains FrameMessageQueue objects, one for each frame */
var listenerList = new Array();


/*
 * Object holding necessary variables for a queue.
 * Every registered frame has one such object to handle
 * its private update queue.
 * <p />
 * This queue allows smooth frame updates, since only after 100 millis
 * updates are forwarded to the interested frames. This helps
 * to avoid a 'flackering' of the screen (too many window refresh).
 */
function FrameMessageQueue( frameHandle )
{
   this.queueTime     = 100; // How long should we collect until we update the frame
   this.retries       = 0;   // If frame is not ready after num retries, it will be deleted from the listener list
   this.ready         = false; // Is the frame ready to receive updates?
   this.timeOutHandle = null;  // The Timer handle from queueTime
   this.frame         = frameHandle; // The JS-Window object of the frame
   this.messageQueue  = new Array(); // array of MessageWrapperDom objects (key,content,qos)
   this.queue         = queue_; // method to enter a new message (=MessageWrapperDom object) to the queue
}

/*
 * @param message MessageWrapperDom object
 */
function queue_( message )
{
   if (Log.TRACE) Log.trace("Queueing message oid='"+message.key.oid+"' in queue "+this.frame.name + ", is msg-no=" + this.messageQueue.length);
   this.messageQueue[this.messageQueue.length] = message;

   if( !queueing ) {
      this.sendMessagQueue(this.frame.name);
   }

   if( this.messageQueue.length < 10 ) {
      if (this.timeOutHandle != null)
         window.clearTimeout( this.timeOutHandle );
      var call = "sendMessageQueue('"+this.frame.name+"')";
      this.timeOutHandle = window.setTimeout( call, this.queueTime );
   }
   else {
      //this is irrelevant, because the queue has to sent with size > 10 within 100msec
      //The timer is already set and the message queue continue filling.
   }
}

/*
 * Sending an update to exactly one frame.
 * @param queueName - The queue associated with the frame
 */
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
         if (Log.TRACE) Log.trace("Frame "+fmq.frame.name+" is ready, sending update ...");
         fmq.frame.update( fmq.messageQueue );
         if (Log.TRACE) {
            var str = "Update:<br />";
            for( var i = 0; i < fmq.messageQueue.length; i++ ) {
               str += fmq.messageQueue[i].key.oid;
               if (i<fmq.messageQueue.lengt-1) str += "<br />";
            }
            Log.trace("Queue["+fmq.frame.name+"]: "+str);
         }
      }
      else {
        Log.warning("Queue["+fmq.frame.name+"]: frame has no update function.");
      }
      fmq.messageQueue.length = 0;
      fmq.retries = 0;
      return;
   }
   else {
      Log.warning("Frame "+fmq.frame.name+" is not ready. Try it again.");
      if( fmq.retries > 200 ) {                            //more than 200*100ms = 20 sec. not availible
         Log.warning("Maximum number of retries reached for frame ["+fmq.frame.name+"].");
         fmq.messageQueue.length = 0;
         if (fmq.timeOutHandle != null)
            window.clearTimeout( fmq.timeOutHandle );
         removeUpdateListener( fmq.frame );
         return;
      }
      if (fmq.timeOutHandle != null)
         window.clearTimeout( fmq.timeOutHandle );
      fmq.timeOutHandle = window.setTimeout( "sendMessageQueue('"+queueName+"')", fmq.queueTime );
      fmq.retries++;
   }
}



/*
 * Setting a frame to be ready.
 */
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
 * This allows a frame to register itself, the frame
 * will be notified with the 'update()' method when new
 * messages arrive from the servlet/xmlBlaster.
 *
 * @param listenerFrame - The window handle of the frame
 */
function addUpdateListener( listenerFrame )
{
   if (Log.TRACE) Log.trace("Adding frame '" + listenerFrame.name + "' as update-listener");
   if(listenerFrame.update==null) {
      return;
   }

   for( i = 0; i < listenerList.length;) {
      if (listenerList[i].frame.closed ) {
         if (Log.INFO) Log.info("Frame '" + listenerList[i].frame.name + "' has been closed, removing it ...");
         removeUpdateListenerAtPos( i );
         continue;
      }
      i++;
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
 * If a frame doesn't want any updates any more.
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
 * If a frame doesn't want any updates any more.
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


/**
 * For debugging.
 * @return All current listener frames
 */
function getListeners()
{
   var str = "Currently registered frame listeners:<br />";
   for( var i = 0; i < listenerList.length; i++ ) {
      str += listenerList[i].frame.name + ", ready="+listenerList[i].ready;
      if (i < listenerList.length-1) str += "<br />";
   }
   return str;
}

function reloadListener()
{
   for( var i = 0; i < listenerList.length; i++ ) {
      listenerList[i].frame.document.location.reload();
   }

}


/*
 * Notify all frames about this update.
 * @param message MessageWrapperDom object
 */
function fireMessageUpdateEvent( message )
{
   for( var i = 0; i < listenerList.length;  ) {
      if (listenerList[i].frame.closed ) {
         if (Log.INFO) Log.info("Frame '" + listenerList[i].frame.name + "' has been closed, removing it. No message fired.");
         removeUpdateListenerAtPos( i );
         continue;
      }
      i++;
   }

   if (Log.TRACE) Log.trace(getListeners());

   for( var i = 0; i < listenerList.length; i++ ) {
      listenerList[i].queue( message );
   }
}


/*
 * This is update-Method which is called by the callback frame.
 * This message comes from the Servlet through the persistent http
 * connection.
 * @param updateKey:String
 * @param content:String
 * @param updateQoS:String
 */
function update( updateKey, content, updateQoS)
{
   var type = typeof updateKey;
   if (type != "string") {
      Log.error("Wrong type '" + type + "' of update xmlKey, callback ignored");
      return;
   }
   if (Log.TRACE) Log.trace("Update coming in, updateKey="+updateKey.toString());

   var updateKey_d = unescape( updateKey.replace(/\+/g, " ") );
   var content_d   = unescape( content.replace(/\+/g, " ") );
   var updateQoS_d = unescape( updateQoS.replace(/\+/g, " ") );

   var key = new UpdateKey(updateKey_d);
   var qos = new UpdateQos(updateQoS_d);

   if (Log.TRACE) Log.trace("Update coming in key.oid="+key.oid);
   if(key.contentMimeExtended.lastIndexOf("EXCEPTION") != -1) {
      alert("Exception:\n\n"+content_d );
   }
   else {
      var message = new MessageWrapperDom( key, content, qos );
      fireMessageUpdateEvent(message);
   }
}


/*
 * Popup messages from servlet
 */
function message(msg)
{
   var decoded = unescape( msg.replace(/\+/g, " ") );
   if (Log.INFO) Log.info(decoded);
   alert( decoded );
}


/*
 * Popup error messages from servlet
 */
function error(msg)
{
   var decoded = unescape( msg.replace(/\+/g, " ") );
   Log.error(decoded);
   alert( "Error\n\n"+decoded );
}

