
   function connect(sessionName, password) {
	   doStopUpdates = false;
      var req = "<xmlBlaster>\n" +
       "  <connect>\n" +
       "    <qos>\n" +
       "      <securityService type='htpasswd' version='1.0'><![CDATA[\n" +
       "        <user>michele</user>\n" +
       "        <passwd>secret</passwd>\n" +
       "      ]]></securityService>\n" +
       "      <session name='michele/1' />\n" +
       "      <queue relating='connection'><address pingInterval='0' retries='-1' delay='2000' /></queue>\n" +
       "      <queue relating='callback' maxEntries='1000' maxEntriesCache='2000'><callback pingInterval='0' retries='-1' delay='1000' /></queue>\n" +
       "   </qos>\n" +
       "  </connect>\n" +
       "</xmlBlaster>\n";
	
     var parameters = "ActionType=xmlScript";
	  parameters += "&xmlScriptPlain=" + encodeURIComponent(req);
     processUpdates(url, parameters, false);
   }

   function subscribeAndStartListen(topic) {
     req = "<xmlBlaster>\n" +
       "  <subscribe><key oid='" + topic + "'></key><qos><persistent>false</persistent></qos></subscribe>\n" +
       "</xmlBlaster>\n";
	
     parameters = "ActionType=xmlScript";
	  parameters += "&xmlScriptPlain=" + encodeURIComponent(req);
     processUpdates(url, parameters, false);

     parameters = "ActionType=updatePollBlocking&onlyContent=true";
     processUpdates(url, parameters, true);
   }

   function stopUpdates(topic) {
      var req = "<xmlBlaster>\n" +
       "  <unSubscribe><key oid='" + topic + "'></key></unSubscribe>\n" +
       "  <wait delay='200' />\n" +
       "</xmlBlaster>\n";
	
      var parameters = "ActionType=xmlScript";
	   parameters += "&xmlScriptPlain=" + encodeURIComponent(req);
      processUpdates(url, parameters, false);
      doStopUpdates = true;
  }

   function processContent(content) {
      if (content == null)
         return;
      var name = content.tagName;
      if (name == 'modifyAttribute') {
          var id = content.getAttribute('id');
          var attrName = content.getAttribute('name');
          var attrNewValue = content.getAttribute('value');
          var el = otherWindow.document.getElementById(id);
          if (el != null) {
             el.setAttribute(attrName, attrNewValue);
          }
          else {
             alert("No id='" + id + "' found");
             otherWindow.status = "No id='" + id + "' found";
          }
      }
      //
      // will add an element to the element specified with the id. if replace is specified,
      // then the current element is replaced.
      //
      else if (name == 'addElement' || name == 'replaceElement') {
          var id = content.getAttribute('id');
          var replace = false;
          if (name == "replaceElement")
             replace = true;
          var el = document.getElementById(id);
          if (el != null) {
            var allChilds = content.childNodes;
            for (var i=0; i < allChilds.length; i++) {
               var tmpNode = allChilds.item(i);
               if (tmpNode.nodeType == 1) {
                  addRecursiveChilds(document, el, tmpNode, replace, null);
               }
            }
          }         
          else {
             alert("Element with id='" + id + "' not found in this document");
          }
      }
      else if (name == "removeElement") {
         // TODO implement
          var id = content.getAttribute('id');
          var elToDelete = document.getElementById(id);
          if (elToDelete != null) {
             var parent = elToDelete.parentNode;
             if (parent != null)
                parent.deleteChild(elToDelete);
          }
      }
      else if (name == "removeAttribute") {
         // TODO implement
      }
      
      else {          	
         alert("Operation '" + name + "' not known");
         otherWindow.status = "Operation '" + name + "' not known";
      }
   }
	
	function updateText(responseText, status, statusTxt) {
		alert("Response is not xml: " + responseText);
		// updateXML(null, responseText, status, statusTxt);
	}

	function updateXML(responseXML, responseText, status, statusTxt) {
	   if (responseText == "<void/>")
	      return;
		var data = responseXML.getElementsByTagName('xmlBlasterResponse');
		if (data != null && data.length > 0) {
		   data = data[0].getElementsByTagName('update');
		   if (data != null && data.length > 0) {
		      for (var j=0; j < data.length; j++) {
		         if (data[j].nodeType == 1) {
      		      var childs = data[j].childNodes;
      		      if (childs != null) {
    	      	      for (var i=0; i < childs.length; i++) {
      	      	      var content = childs.item(i);
   	   	            if (content.nodeType == 1)
                           processContent(content);
		               }
      		      }
		         }
		      }
		   }
		}
		else
		   alert("response '" + responseText + "' does not start with tag 'xmlBlasterResponse'");
	}
	
	function updateError(status, statusTxt) {
	   alert("updateError: statusTxt='" + statusTxt + "'");
	}
	
   
   // These should not be changed by the user
   var base = "../ajax";
   var doStopUpdates = false;
   var time = null;
   var url = base;
   var otherWindow = null;
  
   function processOneEvent(xmlHttpC, url, parameters, doPoll) {
      if (xmlHttpC.readyState == 4 || xmlHttpC.readyState == "complete") {
         //alert("Connect returned: " + xmlHttpC.responseText);
      }
      else {
         // alert("Connect failed: " + xmlHttpC.readyState);
         return;
      }
      var status = xmlHttpC.status;
      var statusTxt = xmlHttpC.statusTxt;
      if (xmlHttpC.responseXML != null) {
         updateXML(xmlHttpC.responseXML, xmlHttpC.responseText, status, statusTxt);
      }
      else if (xmlHttpC.responseText != null) {
         updateText(xmlHttpC.responseText, status, statusTxt);
      }
      else
         updateError(status, statusTxt);
      if (!doStopUpdates && doPoll) {
         var txt = "processUpdates(\'" + url + "\',\'" + parameters + "\',true)";
         // alert(txt);
         time = setTimeout(txt, 10);
      }
   }
  
   function processUpdates(url, parameters, doPoll) {
	   if (doStopUpdates) {
	      document.getElementById('request').value = "The updates are stopped";
	      return;
	   }

      var async = doPoll;
      var xmlHttpC = GetXmlHttpObject();
      if (async)  {
         xmlHttpC.onreadystatechange = function() {
            // alert("Starting async");
            processOneEvent(xmlHttpC, url, parameters, doPoll);
         }
      }        
     
      xmlHttpC.open("POST", url, async);
      xmlHttpC.setRequestHeader("content-type","application/x-www-form-urlencoded;charset=UTF-8");
      xmlHttpC.setRequestHeader("Content-length", parameters.length);
      xmlHttpC.setRequestHeader("Connection", "close");
      xmlHttpC.send(parameters);
     
      if (!async) {
         processOneEvent(xmlHttpC, url, parameters, doPoll);
      }
   }

   function GetXmlHttpObject() {
      var objXMLHttp = null;
      if (window.XMLHttpRequest)
         objXMLHttp = new XMLHttpRequest();
      else if (window.ActiveXObject)
         objXMLHttp = new ActiveXObject("Microsoft.XMLHTTP");
      if (objXMLHttp == null)
         alert("Your browser does not support Ajax");
      return objXMLHttp;
   }


 	/*
 	const unsigned short      ELEMENT_NODE                = 1
   const unsigned short      ATTRIBUTE_NODE              = 2
   const unsigned short      TEXT_NODE                   = 3
   const unsigned short      CDATA_SECTION_NODE          = 4
   const unsigned short      ENTITY_REFERENCE_NODE       = 5
   const unsigned short      ENTITY_NODE                 = 6
   const unsigned short      PROCESSING_INSTRUCTION_NODE = 7
   const unsigned short      COMMENT_NODE                = 8
   const unsigned short      DOCUMENT_NODE               = 9
   const unsigned short      DOCUMENT_TYPE_NODE          = 10
   const unsigned short      DOCUMENT_FRAGMENT_NODE      = 11
   const unsigned short      NOTATION_NODE               = 12
   */
	function addRecursiveChilds(doc, father, node, replace, contentAsText) {
	   if (node == null || father == null || doc == null)
	      return contentAsText;
	   // make a copy of the node
	   var child = null;
		if (node.nodeType == 1) { // element
			child = doc.createElement(node.tagName);
			if (contentAsText != null)
     	      contentAsText += "<" + child.tagName + " ";
		   // add the attributes since they are not detected as attribute nodes
		   var attributes = node.attributes;
		   for (var i=0; i < attributes.length; i++) {
		       var attr = attributes.item(i);
		       child.setAttribute(attr.name, attr.value);
		       if (contentAsText != null)
                contentAsText += attr.name + "='" + attr.value + "' ";
		   }
		   if (contentAsText != null)
		      contentAsText += ">\n";
		}
		else if (node.nodeType == 2) { // attribute
		   child = doc.createAttribute(node.nodeName);
		   child.value = node.nodeValue;
		   alert(node.nodeName + "='" + child.value + "'");
		}
		else if (node.nodeType == 3) {
		   child = doc.createTextNode(node.data);
		   if (contentAsText != null)
		      contentAsText += node.data;
		}
		else { // not implemented (do not add it)
		   if (contentAsText != null)
		      contentAsText = contentAsText + "</" + child.tagName + ">\n";
		   return contentAsText;   
		}
		if (node.hasChildNodes()) {
		   // add all childs recursively
		   var allChilds = node.childNodes;
		   var tmp = "";
		   for (var i=0; i < allChilds.length; i++) {
		       tmp += addRecursiveChilds(doc, child, allChilds.item(i), false, contentAsText);
		   }
		   if (contentAsText != null)
		      contentAsText += tmp;
		}
		if (!replace)
		   father.appendChild(child);
		else {
		   var grandParent = father.parentNode;
		   if (grandParent != null) {
		      dump("The father and the child " + father + " "  + child);
		      grandParent.replaceChild(child, father);
		   }
		   else
		      alert("The grandparent is null");
		}
		if (contentAsText != null)
         contentAsText = contentAsText + "</" + child.tagName + ">\n";
		return contentAsText;
	}


