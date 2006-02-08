<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--
<Server pattern="org.xmlBlaster:contribClass=contrib,*">

<MBean classname="org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Replications" strinit="true" type="java.lang.String" value="aicm,ndb"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Slaves" strinit="true" type="java.lang.String" value="client/ReplWriter-DEE_V_01-AIS/1,client/ReplWriter-NDB-AIS/1"/>
</MBean>
<MBean classname="org.xmlBlaster.contrib.replication.ReplSlave" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication/client/ReplWriter-DEE_V_01-AIS/1"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Active" strinit="true" type="boolean" value="true"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Connected" strinit="true" type="boolean" value="false"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="MaxReplKey" strinit="true" type="long" value="4167"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="MinReplKey" strinit="true" type="long" value="0"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="QueueEntries" strinit="true" type="long" value="23"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="true" name="SqlResponse" strinit="true" type="java.lang.String" value="null"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Status" strinit="true" type="java.lang.String" value="NORMAL"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Topic" strinit="true" type="java.lang.String" value="replTopic"/>
</MBean>
</Server>

-->


<xsl:include href="customize.xsl"/>

<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>											     
<title>Replication List</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<xsl:element name="meta">
  <xsl:attribute name="http-equiv">refresh</xsl:attribute>
  <xsl:attribute name="content"><xsl:value-of select="$refresh.rate"/></xsl:attribute>
</xsl:element>

<link href="styles.css" rel="stylesheet" type="text/css"/>


<script language="JavaScript" type="text/javascript">

function gotoInitiate() {
   var url = '<xsl:value-of select="$initiateReplicationUrl"/>';
   self.location.href= url;
}

function gotoStatements() {
   var url = '<xsl:value-of select="$sqlStatementsUrl"/>';
   self.location.href= url;
}

function refresh() {
   var url = '<xsl:value-of select="$destinationListUrl"/>';
   self.location.href= url;
}


</script>

</head>


<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Replication Destinations<br/>
      <table width="650" align="center" class="external" summary="">
        <tr> 
          <td>
            <table class="inner" width="550" align="center" summary="" border="1">
    	  <tr>
    	    <th class="normal" title="Name of the destination of the replication" colspan="3">Destination Name</th>
    	    <th class="normal" title="Amount of entries in the queue. These entries have not been delivered yet" colspan="1">Holdback Messages</th>
    	    <th class="normal" title="Current counter for this replication. This is a monoton increasing positive integer" colspan="1">Counter</th>
    	    <th class="normal" title="Status of the replication initiation." colspan="1">Status</th>
    	    <th class="normal" title="Status of the dispacher, can either be active or disactivated." colspan="1">Active / Standby</th>
    	    <th class="normal" title="Status of the connection to the destination, either connected or disconnected." colspan="1">Connection</th>
    	  </tr>
    
    <xsl:apply-templates/>

            </table>
            <table width="450" align="center" border="0" summary="">
    	      <tr>
    	        <td align="center" colspan="4" class="normal"><button title="Click to refresh this page manually" class="common" onClick="refresh()">Refresh</button></td>
    	        <td align="center" colspan="4" class="normal"><button title="Click to go to the statements page" class="common" onClick="gotoStatements()">Statements</button></td>
    	        <td align="center" colspan="4" class="normal"><button title="Click to initiate one more replications" class="common" onClick="gotoInitiate()">Initate Repl.</button></td>
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

   <!-- <xsl:param name="replKeyStartsWith">DEMO</xsl:param> -->

   <!-- <xsl:param name="tmp1" select="Attribute[@name='ReplPrefix']/@value"/> -->
   <xsl:param name="sessionName" select="Attribute[@name='SessionName']/@value"/>
   <xsl:param name="queueEntries" select="Attribute[@name='QueueEntries']/@value"/>
   <xsl:param name="objectName" select="@objectname"/>
   <xsl:param name="maxReplKey" select="Attribute[@name='MaxReplKey']/@value"/>
   <xsl:param name="replStatus" select="Attribute[@name='Status']/@value"/>
   <xsl:param name="activeStatus" select="Attribute[@name='Active']/@value"/>
   <xsl:param name="connectedStatus" select="Attribute[@name='Connected']/@value"/>

<!--
   <xsl:param name="sessionName"/>
   <xsl:param name="queueEntries"/>
   <xsl:param name="objectName"/>
   <xsl:param name="maxReplKey"/>
   <xsl:param name="replStatus"/>
   <xsl:param name="activeStatus"/>
   <xsl:param name="connectedStatus""/>
-->

    	  <tr class="inner">
    	    <td colspan="3" class="normal" title="Click to get details on this replication">
	      <xsl:element name="a">
		 <xsl:attribute name="href">mbean?objectname=<xsl:value-of select="$objectName"/>&amp;template=destinationDetails</xsl:attribute>
		 <xsl:call-template name="modifySessionName">
                    <xsl:with-param name="content" select="$sessionName"/>
		 </xsl:call-template>
	      </xsl:element>
	    </td>
  
            <xsl:choose>
              <xsl:when test="$queueEntries > $queue.highwarn">
                 <xsl:choose>
                   <xsl:when test="$queueEntries > $queue.highalarm">
       	    <td align="center" colspan="1" class="highalarm" title="alarm: too many entries in queue. Check connection and dispatcher.">
              <xsl:value-of select="$queueEntries"/>
	    </td>
                   </xsl:when>
                   <xsl:otherwise>
       	    <td align="center" colspan="1" class="highwarn" title="warning: Some entries in queue.">
              <xsl:value-of select="$queueEntries"/>
	    </td>
		   </xsl:otherwise>
                 </xsl:choose>
              </xsl:when>
              <xsl:otherwise>
       	    <td align="center" colspan="1" class="number">
              <xsl:value-of select="$queueEntries"/>
	    </td>
              </xsl:otherwise>
            </xsl:choose>

	    <xsl:choose>
	       <xsl:when test="$maxReplKey = '0'">
    	    <td align="right" colspan="1" class="hidden"><xsl:value-of select="$maxReplKey"/></td>
	       </xsl:when>
	       <xsl:otherwise>
    	    <td align="right" colspan="1" class="number"><xsl:value-of select="$maxReplKey"/></td>
	       </xsl:otherwise>
	    </xsl:choose>


    	    <td align="center" colspan="1" class="normal">
	       <xsl:element name="img">
	          <xsl:attribute name="height">18</xsl:attribute>
	          <xsl:attribute name="src">./<xsl:value-of select="$replStatus"/>.png</xsl:attribute>
	          <xsl:attribute name="alt"><xsl:value-of select="$replStatus"/></xsl:attribute>
	          <xsl:attribute name="title"><xsl:value-of select="$replStatus"/></xsl:attribute>
	       </xsl:element>
	    </td>
  
            <xsl:choose>
              <xsl:when test="$activeStatus = 'true'">
       	    <td align="center" colspan="1" class="normal"><img height="18" src="./active.png" alt="active" title="active" /></td>
              </xsl:when>
              <xsl:otherwise>
       	    <td align="center" colspan="1" class="normal"><img height="18" src="./inactive.png" alt="inactive" title="inactive" /></td>
              </xsl:otherwise>
            </xsl:choose>
            <xsl:choose>
              <xsl:when test="$connectedStatus = 'true'">
       	    <td align="center" colspan="1" class="normal"><img height="18" src="./connected.png" alt="connected" title="connected" /></td>
              </xsl:when>
              <xsl:otherwise>
       	    <td align="center" colspan="1" class="normal"><img height="18" src="./disconnected.png" alt="disconnected" title="disconnected" /></td>
              </xsl:otherwise>
            </xsl:choose>
    	  </tr>

</xsl:template>      

<!-- end body -->

</xsl:stylesheet>
