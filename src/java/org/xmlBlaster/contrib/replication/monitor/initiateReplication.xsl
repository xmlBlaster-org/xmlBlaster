<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<xsl:include href="customize.xsl"/>

<xsl:variable name="isUser" select="/*/@user"/>
<xsl:variable name="isInitiator" select="/*/@initiator"/>
<xsl:variable name="isAdmin" select="/*/@admin"/>

<!-- we also need to avoid doubles -->
<xsl:param name="request.src"/>

<xsl:template name="replaceSource">
   <xsl:param name="content" />
   <xsl:param name="alreadyProcessed" />

   <xsl:choose>
     <xsl:when test="contains($content, ',')">
       <xsl:choose>
          <xsl:when test="contains(substring-before($content, ','),$request.src)">
             <xsl:choose>
                <xsl:when test="contains($alreadyProcessed, substring-before($content, ','))">
                </xsl:when>
                <xsl:otherwise>
                  <xsl:element name="option">
                    <xsl:attribute name="value"><xsl:value-of select="substring-before($content, ',')"/></xsl:attribute>
                    <xsl:value-of select="substring-before($content, ',')"/>
                  </xsl:element>
                </xsl:otherwise>
             </xsl:choose>
          </xsl:when>
       </xsl:choose>
       <xsl:call-template name="replaceSource">
          <xsl:with-param name="content" select="substring-after($content, ',')"/>
          <xsl:with-param name="alreadyProcessed" select="concat($alreadyProcessed, substring-before($content, ','))"/>
       </xsl:call-template>
     </xsl:when>
     <xsl:otherwise>
       <xsl:choose>
          <xsl:when test="contains($content,$request.src)">
             <xsl:choose>
                <xsl:when test="contains($alreadyProcessed, $content)">
                </xsl:when>
                <xsl:otherwise>
                  <xsl:element name="option">
                     <xsl:attribute name="value"><xsl:value-of select="$content"/></xsl:attribute>
                     <xsl:value-of select="$content"/>
                  </xsl:element>
                </xsl:otherwise>
             </xsl:choose>
          </xsl:when>
       </xsl:choose>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<!--
   We need to add the following:
    source: nothing since it is as it should be 
    dest: 'client/ReplWriter-' + ${dest} + '-AIS/session/1';
    for the version you must use what has been defined on the readers property files. For back-replication
    the version should not have any importance.

-->

<xsl:template name="replaceDest">
   <xsl:param name="content" />
   <xsl:param name="pos" />

   <xsl:param name="alreadyProcessed" />
   <xsl:variable name="txtName" select="substring-before($content, ',')"/>
   <xsl:choose>
     <xsl:when test="contains($content, ',')"> <!-- then we have several entries (writers) not only one -->
        <xsl:choose>
           <xsl:when test="$pos mod 2 = 0">
              <xsl:text disable-output-escaping='yes'>&lt;tr&gt;</xsl:text>
           </xsl:when>
        </xsl:choose>
          <td class="list">
            <xsl:element name="input">
              <xsl:attribute name="type">checkbox</xsl:attribute>
              <xsl:attribute name="class">value</xsl:attribute>
              <xsl:attribute name="onClick">setItem(<xsl:value-of select="$pos"/>)</xsl:attribute>
              <xsl:attribute name="id">item<xsl:value-of select="$pos"/></xsl:attribute>
              <xsl:attribute name="name">item<xsl:value-of select="$pos"/></xsl:attribute>
              <xsl:attribute name="value"><xsl:value-of select="substring-after($txtName,'client/')"/></xsl:attribute>
            </xsl:element>
            <xsl:value-of select="substring-after($txtName,'client/')"/>
          </td>
        <xsl:choose>
           <xsl:when test="$pos mod 2 = 1">
              <xsl:text disable-output-escaping='yes'>&lt;/tr&gt;</xsl:text>
           </xsl:when>
        </xsl:choose>
        <xsl:call-template name="replaceDest">
           <xsl:with-param name="pos" select="$pos + 1"/>
           <xsl:with-param name="content" select="substring-after($content, ',')"/>
          <xsl:with-param name="alreadyProcessed" select="concat($alreadyProcessed, substring-before($content, ','))"/>
        </xsl:call-template>
     </xsl:when>
     <xsl:otherwise>
        <xsl:choose>
	         <xsl:when test="string-length($alreadyProcessed) = 0 and contains($alreadyProcessed, $txtName) = 0">
           </xsl:when>
           <xsl:otherwise>
             <xsl:choose>
                <xsl:when test="$pos mod 2 = 0">
                   <xsl:text disable-output-escaping='yes'>&lt;tr&gt;</xsl:text>
                </xsl:when>
             </xsl:choose>
               <td class="list">
                 <xsl:element name="input">
                   <xsl:attribute name="type">checkbox</xsl:attribute>
                   <xsl:attribute name="class">value</xsl:attribute>
                   <xsl:attribute name="onClick">setItem(<xsl:value-of select="$pos"/>)</xsl:attribute>
                   <xsl:attribute name="id">item<xsl:value-of select="$pos"/></xsl:attribute>
                   <xsl:attribute name="name">item<xsl:value-of select="$pos"/></xsl:attribute>
                   <xsl:attribute name="value"><xsl:value-of select="substring-after($content,'client/')"/></xsl:attribute>
                 </xsl:element>
                 <xsl:value-of select="substring-after($content,'client/')"/>
               </td>
             <xsl:choose>
                <xsl:when test="$pos mod 2 = 1">
                   <xsl:text disable-output-escaping='yes'>&lt;/tr&gt;</xsl:text>
                </xsl:when>
             </xsl:choose>
           </xsl:otherwise>
        </xsl:choose>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>
<title>Initiate Replication</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
<link href="styles.css" rel="stylesheet" type="text/css"/>


<script language="JavaScript" type="text/javascript">

  function GetXmlHttpObject(handler) { 
     var objXMLHttp = null;
     if (window.XMLHttpRequest)
        objXMLHttp = new XMLHttpRequest();
     else if (window.ActiveXObject)
        objXMLHttp = new ActiveXObject("Microsoft.XMLHTTP");
     if (objXMLHttp == null)
        alert("Your browser does not support Ajax");
     return objXMLHttp;
  }

 
  function connect(url) {
     var xmlHttpC=GetXmlHttpObject();
     var async = false; 
     xmlHttpC.open("GET", url, async);
     xmlHttpC.setRequestHeader("content-type","application/x-www-form-urlencoded");
     xmlHttpC.send(null);
     if (xmlHttpC.readyState == 4 || xmlHttpC.readyState == "complete") {
        //alert("Connect returned: " + xmlHttpC.responseText);
     }
     else {
        alert("Connect failed: " + xmlHttpC.readyState);
     }

     // /MBeanOperation/Operation/@return;

     var str = '';
     if (xmlHttpC.responseText != null) {
        str += xmlHttpC.responseText;
     }
     return str;
  }


function stripString(name) {
   var ret = "";
   var i;
   var c;
   for (i=0; i &lt; name.length; i++) {
      c = name.charAt(i);
      if (c == '-') c='_';
      ret += c;
   }
   return ret;
}

var maxItem = 0;
var counter = 0;

// used in the html page
function setItem(val) {
  if (val > maxItem)
     maxItem = val;
}


function initiateReplication(objName) {
   var link = "";
   var source = document.getElementById("sources").value;
   var dest;
   var extraSource = "";
   var extraDest = "";
   var initialFilesLocation = "";

   <xsl:choose>
     <xsl:when test="$show.initialFilesLocation = 'yes'">
   var tmp = document.getElementById("initialFilesLocation").checked;
   if (tmp) {
     var initialFilesLocEl = document.getElementById("initialFiles");
     initialFilesLocation = initialFilesLocEl.value; // getAttribute("value");
   }
   else {
     initialFilesLocation = "";
   }

     </xsl:when>
     <xsl:otherwise>
   var extraSource = "";
   var extraDest = "";
     </xsl:otherwise>
   </xsl:choose>

   link = "destinationList";
   // this is the first url, the invocation to start the batch

   var initStatus = document.getElementById("initStatus");
   initStatus.firstChild.nodeValue = "INITIATING ...";

   var url = 'invoke?objectname=' + 
           objName + 
           '&amp;operation=collectInitialUpdates&amp;type0=java.lang.String&amp;value0=' +
           '<xsl:value-of select="$request.src"/>&amp;template=resultOtherWindow&amp;val=-1';

   var resp = '';
   resp = connect(url);

   for (var i=0; i &lt;= maxItem; i++) {
      resp = '';
      var origDest = document.getElementById("item" + i);
      if (origDest != null) {
         if (origDest.checked) {
            origDest = origDest.value;
            dest = "client/" + origDest;
            extraSource = "";
            extraDest = "";

            extraSource = stripString(extraSource);

            url = 'invoke?objectname=' + 
                   objName + 
                   '&amp;operation=initiateReplication&amp;type0=java.lang.String&amp;value0=' +
                   dest + 
                   '&amp;type1=java.lang.String&amp;value1=' + 
                   source + 
                   '&amp;type2=java.lang.String&amp;value2=' + 
                   extraDest + 
                   '&amp;type3=java.lang.String&amp;value3=' + 
                   extraSource +
                   '&amp;type4=java.lang.String&amp;value4=' + 
                   initialFilesLocation + 
                   '&amp;template=resultOtherWindow&amp;val=' + i;
            resp = connect(url);

            if (resp != null &amp;&amp; resp.length &gt; 0) {
               if (resp.indexOf('error:') == 0)
               alert(resp + " will not add this destination");
               resp = "";
            }
            else {
               counter++;
               initStatus.firstChild.nodeValue = "INITIATING replication " + counter;
            }
         }
      }
   }
   initStatus.firstChild.nodeValue = "INITIATED ALL REPLICATIONS";
   counter = 0;
   
   // this is the operation to start the initial update
   url = 'invoke?objectname=' + 
          objName + 
          '&amp;operation=startBatchUpdate&amp;type0=java.lang.String&amp;value0=' +
          '<xsl:value-of select="$request.src"/>' +
          '&amp;destination=' +
          link +
          '&amp;template=result';
   self.location.href = url;

}


function cancel() {
   var url = '<xsl:value-of select="$destinationListUrl"/>';
   self.location.href= url;
}

function initialFilesChanged() {
   var source = document.getElementById("initialFilesLocation").checked;
   var dest = document.getElementById("initialFiles");
   if (source) {
      dest.removeAttribute("disabled");
   }
   else {
      dest.setAttribute("disabled", "true");
   }
}


</script>

</head>

<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Initiate Replication (<xsl:value-of select="$request.src"/>)<br/>
      <table width="650" align="center" class="external" summary="">

    <xsl:apply-templates/>

       </table>
     </div>
    <xsl:call-template name="footer"/>
   </center>

</body>
</html>
</xsl:template>

<!-- 
<xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text>
-->

<xsl:template match='MBean'>
        <tr> 
          <td>
            <table class="inner" width="350" align="center" summary="" border="1">
              <tr>
                <td colspan="1" class="normal" title="The Source of the Replication. (replication.prefix)">Source</td>
                <td colspan="1" class="value">
                    <select class="values" id="sources" name="sources" size="1">
   <xsl:call-template name="replaceSource">
     <xsl:with-param name="content" select="Attribute[@name='Replications']/@value"/>
     <xsl:with-param name="alreadyProcessed" select="first"/>
   </xsl:call-template>
                    </select>
                 </td>
              </tr>
              <tr>
                <td colspan="1" class="normal" title="The destinations of the Replication.">Destinations</td>
                <td colspan="1" class="value">
                  <table class="list" width="280" align="center" summary="" border="0">
                     <xsl:call-template name="replaceDest">
                       <xsl:with-param name="pos" select="0"/>
                       <xsl:with-param name="content" select="Attribute[@name='Slaves']/@value"/>
                       <xsl:with-param name="alreadyProcessed" select="first"/>
                     </xsl:call-template>
                  </table> 
                </td>
              </tr>

   <xsl:choose>
     <xsl:when test="$show.initialFilesLocation = 'yes'">
              <tr>
                <td colspan="1" class="value">
                  <input type="checkbox" name="initialFilesLocation" id="initialFilesLocation" onclick="initialFilesChanged()" value="">store initial data</input>
                </td>  
                <td colspan="1" class="value">
                  <xsl:element name="input">
                    <xsl:attribute name="type">text</xsl:attribute>
                    <xsl:attribute name="disabled">true</xsl:attribute>
                    <xsl:attribute name="name">initialFiles</xsl:attribute>
                    <xsl:attribute name="id">initialFiles</xsl:attribute>
                    <xsl:attribute name="size">35</xsl:attribute>
                    <xsl:attribute name="value"><xsl:value-of select="Attribute[@name='InitialFilesLocation']/@value"/></xsl:attribute>
                  </xsl:element>   
                </td>
              </tr>
     </xsl:when>
   </xsl:choose>
              <tr>
                <td colspan="1" align="center"><button class="small" title="Click to return to the replication list" onClick="cancel()">Cancel</button></td>

           <xsl:choose>
              <xsl:when test="$isInitiator = 'true'">
                <td colspan="1" align="center">
   <xsl:element name="button">
      <xsl:attribute name="class">wide</xsl:attribute>
      <xsl:attribute name="Title">Click to start (initiate) the choosen replication combination</xsl:attribute>
      <xsl:attribute name="onClick">initiateReplication('<xsl:value-of select="@objectname"/>')</xsl:attribute>
      Initiate Repl.
   </xsl:element>
                </td>
              </xsl:when>
              <xsl:otherwise>
            <td align="center" colspan="1" class="normal"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
              </xsl:otherwise>
           </xsl:choose>

              </tr>

              <tr>
                <td colspan="2" class="value">
                  <h2><div id='initStatus' name='initStatus'><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></div></h2>
                </td>  
              </tr>   

              </table>
            </td>
          </tr>

</xsl:template>      

<!-- end body -->

</xsl:stylesheet>

