<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--


http://localhost:9999/mbean?objectname=org.xmlBlaster:contribClass=contrib,*&attributes=true&operations=false&notifications=false&constructors=false&template=sqlStatements

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

<xsl:template name="replaceString">
   <xsl:param name="content" />
   <xsl:choose>
     <xsl:when test="contains($content, ',')">
   <option>
        <xsl:value-of select="substring-before($content, ',')"/>
   </option>
        <xsl:call-template name="replaceString">
           <xsl:with-param name="content" select="substring-after($content, ',')"/>
        </xsl:call-template>
     </xsl:when>
     <xsl:otherwise>
   <option>
       <xsl:value-of select="$content"/>
   </option>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>											     
<title>SQL Statements</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<xsl:element name="meta">
  <xsl:attribute name="http-equiv">refresh</xsl:attribute>
  <xsl:attribute name="content"><xsl:value-of select="$refresh.rate"/></xsl:attribute>
</xsl:element>

<link href="styles.css" rel="stylesheet" type="text/css"/>


<script language="JavaScript" type="text/javascript">

function removeSqlStatement(requestId) {
   var objName = '<xsl:value-of select="//MBean[@classname='org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin']/@objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=removeSqlStatement&amp;type0=java.lang.String&amp;value0=' +
      requestId + '&amp;destination=sqlStatements&amp;template=result' ;
   self.location.href= url;
}

function gotoReplications() {
   var url = '<xsl:value-of select="$destinationListUrl"/>';
   self.location.href= url;
}

function gotoStatement() {
   var url = '<xsl:value-of select="$doStatementUrl"/>';
   self.location.href= url;
}

function refresh() {
   var url = '<xsl:value-of select="$sqlStatementsUrl"/>';
   self.location.href= url;
}


</script>

</head>


<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Sql Statements<br/>
      <table width="650" align="center" class="external" summary="">
        <tr> 
          <td>
            <table class="inner" width="550" align="center" summary="" border="1">
    	  <tr>
    	    <th class="normal" title="Unique Id identifying this request" colspan="1">Statement ID</th>
    	    <th class="normal" title="Total number of received slave responses (both successful and failed)." colspan="1">Received</th>
    	    <th class="normal" title="Number of slaves on which the response is not the expected one." colspan="1">Failed</th>
    	    <th class="normal" title="Clears (removes) this statement response from memory." colspan="1">Clear response</th>
    	  </tr>						 
    
    <xsl:apply-templates/>

            </table>
            <table class="inner" width="550" align="center" summary="" border="0">
    	      <tr>
    	        <td align="center" colspan="1" class="normal"><button title="Click to refresh this page manually" class="common" onClick="refresh()">Refresh</button></td>
    	        <td align="center" colspan="1" class="normal"><button title="Click to go to the replications list page" class="common" onClick="gotoReplications()">Replications</button></td>
    	        <td align="center" colspan="1" class="normal"><button title="Click to go to the page where to submit SQL Statements" class="common" onClick="gotoStatement()">SQL</button></td>
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

<xsl:template match="MBean[@classname='org.xmlBlaster.contrib.replication.SqlStatement']">

   <xsl:param name="replPrefix" select="Attribute[@name='ReplicationPrefix']/@value"/>
   <xsl:param name="failed" select="Attribute[@name='Failed']/@value"/>
   <xsl:param name="received" select="Attribute[@name='Received']/@value"/>
   <xsl:param name="all" select="Attribute[@name='All']/@value"/>
   <xsl:param name="status" select="Attribute[@name='Status']/@value"/>
   <xsl:param name="requestId" select="Attribute[@name='RequestId']/@value"/>
   <xsl:param name="objectName" select="@objectname"/>

    	  <tr class="inner">
    	    <td colspan="1" class="normal" title="Click to get details on this replication">
	      <xsl:element name="a">
		 <xsl:attribute name="href">mbean?objectname=<xsl:value-of select="$objectName"/>&amp;template=statementDetails</xsl:attribute>
		 <xsl:value-of select="$requestId"/>
	      </xsl:element>
	    </td>


       	    <td align="center" colspan="1" class="normal" title=""><xsl:value-of select="$received"/> of <xsl:value-of select="$all"/></td>
       	    
	    <xsl:element name="td">
	      <xsl:attribute name="align">center</xsl:attribute>
	      <xsl:attribute name="colspan">1</xsl:attribute>
	      <xsl:attribute name="class"><xsl:value-of select="$status"/></xsl:attribute>
	      <xsl:attribute name="title"></xsl:attribute>
	      <xsl:value-of select="$failed"/> of <xsl:value-of select="$all"/>
	    </xsl:element>

            <td align="center" colspan="1" class="normal" title="">
               <xsl:element name="button">
                  <xsl:attribute name="class">small</xsl:attribute>
                  <xsl:attribute name="title">Click to remove the resources for this statement</xsl:attribute>
                  <xsl:attribute name="onClick">removeSqlStatement('<xsl:value-of select="$requestId"/>')</xsl:attribute>
                  Clear
               </xsl:element>
	    </td>
    	  </tr>

</xsl:template>      

<!-- end body -->

</xsl:stylesheet>
