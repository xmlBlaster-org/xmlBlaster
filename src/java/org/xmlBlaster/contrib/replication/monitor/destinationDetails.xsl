<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--

-->


<xsl:include href="customize.xsl"/>

<!-- the variables used in this document: -->

<xsl:param name="request.referer"/>

<xsl:template match ='/'>
<html>
<head>                                                                                       
<title>Replication Details</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<xsl:element name="meta">
  <xsl:attribute name="http-equiv">refresh</xsl:attribute>
  <xsl:attribute name="content"><xsl:value-of select="$refresh.rate"/></xsl:attribute>
</xsl:element>

<link href="styles.css" rel="stylesheet" type="text/css"/>

<script language="JavaScript" type="text/javascript">

function gotoDestinationList() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var url = "";
   if (referer == "")
      url = '<xsl:value-of select="$destinationListUrl"/>';
   else
      url = '<xsl:value-of select="$destinationPrefix"/>' + '<xsl:value-of select="$request.referer"/>';
   // alert(url);
   self.location.href = url;
}

function refresh() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var url = 'mbean?objectname=<xsl:value-of select="$request.objectname"/>&amp;template=destinationDetails&amp;referer=' + referer;
   self.location.href = url;
}

function reInitiateReplication() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=reInitiateReplication&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href = url;
}

function killSession() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   // var url = 'invoke?objectname=' + objName + '&amp;operation=kill&amp;destination=destinationList&amp;template=result&amp;referer=' + referer;
   var url = 'invoke?objectname=' + objName + '&amp;operation=kill&amp;destination=<xsl:value-of select="$request.referer"/>&amp;template=result&amp;referer=' + referer;
   self.location.href = url;
}


function clearQueue() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=clearQueue&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href = url;
}

function removeFirst() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=removeQueueEntries&amp;type0=long&amp;value0=1&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href = url;
}

function cancelInitialUpdate() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=cancelInitialUpdate&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href = url;
}

function toggleDispatcher() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=toggleActive&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href= url;
}

function dumpEntry() {
   var referer = '<xsl:value-of select="$request.referer"/>';
   var objName = '<xsl:value-of select="$request.objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=dumpFirstEntry&amp;destination=destinationDetails&amp;template=result&amp;referer=' + referer;
   self.location.href= url;
}

</script>

</head>


<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Replication Details<br/>
      <table width="650" align="center" class="external" summary="">
        <tr> 
          <td>
            <table class="inner" width="450" align="center" summary="" border="1">
    
    <xsl:apply-templates/> 

              <tr>
                <td align="center" colspan="1" class="normal"><button class="danger" onClick="killSession()" title="Click to delete definitively this replication">Remove</button></td>
                <td align="center" colspan="1" class="normal"><button class="small" onClick="gotoDestinationList()" title="Click to go back to replication list">Back</button></td>
                <td align="center" colspan="1" class="normal"><button class="small" onClick="refresh()" title="Click to refresh this page manually">Refresh</button></td>
                <!-- <td align="center" colspan="1" class="normal"><button class="small" onClick="reInitiateReplication()" title="Click if you want to restart this replication (initial update)">Restart Repl</button></td> -->
                <td align="center" colspan="1" class="normal"><button class="small" onClick="dumpEntry()" title="You will dump the first entry of the queue on file">Dump</button></td>
              </tr>
            </table>
         </td>
        </tr>
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

<xsl:template match="MBean[@classname='org.xmlBlaster.contrib.replication.ReplSlave']">

   <xsl:param name="sessionName" select="Attribute[@name='SessionName']/@value"/>
   <xsl:param name="beanName" select="$sessionName"/>
         <tr>
           <td colspan="4" class="normal" align="center" title="The name identifying this destination of the replication">
             <xsl:call-template name="modifySessionName">
               <xsl:with-param name="content" select="Attribute[@name='SessionName']/@value"/>
             </xsl:call-template>
           </td>
         </tr>
 
         <!-- Queue Entries Line -->
         <tr class="inner">
           <xsl:variable name="queueEntries" select="Attribute[@name='QueueEntries']/@value"/>

           <td colspan="1" class="normal" title="The queue holding the replicated data">Holdback Messages</td>
           <xsl:choose>
             <xsl:when test="$queueEntries > $queue.highwarn">
                <xsl:choose>
                  <xsl:when test="$queueEntries > $queue.highalarm">
           <td align="right" colspan="1" class="highalarm" title="alarm: too many entries in queue. Check connection and dispatcher.">
             <xsl:value-of select="$queueEntries"/>
           </td>
                  </xsl:when>
                  <xsl:otherwise>
           <td align="right" colspan="1" class="highwarn" title="warning: Some entries in queue.">
             <xsl:value-of select="$queueEntries"/>
           </td>
                  </xsl:otherwise>
                </xsl:choose>
             </xsl:when>
             <xsl:otherwise>
           <td align="right" colspan="1" class="number">
             <xsl:value-of select="$queueEntries"/>
           </td>
             </xsl:otherwise>
           </xsl:choose>
           <td align="center" colspan="1" class="normal">
                 <button class="small" onClick="clearQueue()" title="Click to clear/delete all entries from the Holdback message queue">Clear Queue</button>
           </td>
            <td align="center" colspan="1" class="normal">
                  <button class="small" onClick="removeFirst()" title="Click to remove only the first entry of the holdback message queue">Remove First</button>
            </td>
         </tr>

         <!-- Counter Line -->
          <tr class="inner">
            <td colspan="1" class="normal" title="Current counter of the replicated data">Count</td>
            
            <xsl:choose>
               <xsl:when test="Attribute[@name='TransactionSeq']/@value = '0'">
            <td align="right" colspan="1" class="hidden"><xsl:value-of select="Attribute[@name='TransactionSeq']/@value"/></td>
               </xsl:when>
               <xsl:otherwise>
            <td align="right" colspan="1" class="number"><xsl:value-of select="Attribute[@name='TransactionSeq']/@value"/></td>
               </xsl:otherwise>
            </xsl:choose>

            <td colspan="1" class="normal" title="Current Version of the replication data">Version</td>
            <td align="right" colspan="1" class="number"><xsl:value-of select="Attribute[@name='Version']/@value"/></td>

            <!-- <td colspan="2"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td> -->

          </tr>
         
         <!-- Status Of Initial Update Line -->
          <tr class="inner">
            <td colspan="1" class="normal" title="Status of initial Update">Status</td>
            <td align="center" colspan="1" class="normal">
               <xsl:variable name="replStatus" select="Attribute[@name='Status']/@value"/>
               <xsl:element name="img">
                  <xsl:attribute name="height">20</xsl:attribute>
                  <xsl:attribute name="src">./<xsl:value-of select="$replStatus"/>.png</xsl:attribute>
                  <xsl:attribute name="alt"><xsl:value-of select="$replStatus"/></xsl:attribute>
                  <xsl:attribute name="title"><xsl:value-of select="$replStatus"/></xsl:attribute>
               </xsl:element>
            </td>
            <td colspan="1"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
            <td align="center" colspan="1" class="normal">
                  <button class="small" onClick="cancelInitialUpdate()" title="Click to cancel an initiated replication (if the status is not running)">Cancel Update</button>
            </td>
          </tr>


         <!-- Status Of Active/Inactive Dispatcher Line -->
          <tr class="inner">
            <td colspan="1" class="normal" title="Status of the dispatcher: active or destactivated">Active / Standby</td>
            <xsl:choose>
              <xsl:when test="Attribute[@name='Active']/@value = 'true'">
            <td align="center" colspan="1" class="normal"><img height="20" src="./active.png" alt="active" title="active" /></td>
            <td colspan="1"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
            <td align="center" colspan="1" class="normal">
                  <button class="small" onClick="toggleDispatcher()" title="Click to pause: i.e. disactivate the dispatcher">Standby</button>
            </td>
                     </xsl:when>
              <xsl:otherwise>

            <td align="center" colspan="1" class="normal"><img height="20" src="./inactive.png" alt="inactive" title="inactive" /></td>
            <td colspan="1"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
            <td align="center" colspan="1" class="normal">
                  <button class="small" onClick="toggleDispatcher()" title="Click to continue: go from Standby to Active">Activate</button>
            </td>
              </xsl:otherwise>
            </xsl:choose>
          </tr>

         <!-- Status Of Connection Line -->
          <tr class="inner">
            <td colspan="1" class="normal" title="Status of the connection: connected (online) or disconnected (offline)">Connection</td>
            <td align="center" colspan="1" class="normal">
               <xsl:variable name="connectStatus" select="Attribute[@name='Connection']/@value"/>
               <xsl:element name="img">
                  <xsl:attribute name="height">20</xsl:attribute>
                  <xsl:attribute name="src">./<xsl:value-of select="$connectStatus"/>.png</xsl:attribute>
                  <xsl:attribute name="alt"><xsl:value-of select="$connectStatus"/></xsl:attribute>
                  <xsl:attribute name="title"><xsl:value-of select="$connectStatus"/></xsl:attribute>
               </xsl:element>
            </td>
            <td colspan="2"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
          </tr>

         <!-- Last Message -->
          <tr class="inner">
            <td colspan="1" class="normal" title="The last message (for example exceptions)">Last message</td>
            <td align="center" colspan="3" class="normal">
              <center><textarea id="sqlTxt" name="sqlTxt" cols="45" rows="2"><xsl:value-of select="Attribute[@name='LastMessage']/@value"/></textarea></center>
            </td>
          </tr>

</xsl:template>      

<!-- end body -->

</xsl:stylesheet>
