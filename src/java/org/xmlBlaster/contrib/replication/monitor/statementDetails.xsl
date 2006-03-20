<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<xsl:include href="customize.xsl"/>

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

function gotoStatement() {
   var url = '<xsl:value-of select="$doStatementUrl"/>';
   self.location.href= url;
}

function refresh(url) {
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

    <xsl:apply-templates/>

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

	<MBean classname="org.xmlBlaster.contrib.replication.SqlStatement" description="Information on the management interface of the MBean" objectname="org.xmlBlaster:nodeClass=node,node="xmlBlaster_192_168_0_21_3412",contribClass=contrib,contrib="replication/DEMO_/1139353056036000000"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="All" strinit="true" type="int" value="1"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Failed" strinit="true" type="int" value="0"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="FailedList" strinit="true" type="java.lang.String" value=""/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Received" strinit="true" type="int" value="1"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="ReceivedList" strinit="true" type="java.lang.String" value="client/DemoWriter/1"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="ReplicationPrefix" strinit="true" type="java.lang.String" value="DEMO_"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="RequestId" strinit="true" type="java.lang.String" value="1139353056036000000"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Response" strinit="true" type="java.lang.String" value="<?xml version='1.0' encoding='UTF-8' ?> <sql>  <desc>   <command>statement</command>   <ident>query</ident>  </desc>  <row num='0'>   <col name='COUNT(*)'>424</col>  </row> </sql>"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="SlaveList" strinit="true" type="java.lang.String" value="client/DemoWriter/1"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Statement" strinit="true" type="java.lang.String" value="select count(*) from all_triggers"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Status" strinit="true" type="java.lang.String" value="ok"/>
-
	<Constructor description="Public constructor of the MBean" name="org.xmlBlaster.contrib.replication.SqlStatement">
<Parameter description="" id="0" name="p1" strinit="true" type="java.lang.String"/>
<Parameter description="" id="1" name="p2" strinit="true" type="java.lang.String"/>
<Parameter description="" id="2" name="p3" strinit="true" type="java.lang.String"/>
<Parameter description="" id="3" name="p4" strinit="false" type="java.util.List"/>
</Constructor>
-
	<Operation description="Operation exposed for management" impact="unknown" name="getSlaveResponse" return="java.lang.String">
<Parameter description="" id="0" name="p1" strinit="true" type="java.lang.String"/>
</Operation>
</MBean>

-->

<xsl:template name="internalLoop">
   <xsl:param name="single" />
      <tr>
         <xsl:element name="td">
           <xsl:choose>
             <xsl:when test="contains(Attribute[@name='FailedList']/@value, $single)">
	       <xsl:attribute name="class">failed</xsl:attribute>
               <xsl:attribute name="title">This client has a response which is not the same as the reference response</xsl:attribute>
	     </xsl:when>
             <xsl:otherwise>
                <xsl:choose>
                   <xsl:when test="contains(Attribute[@name='ReceivedList']/@value, $single)">
           	      <xsl:attribute name="class">ok</xsl:attribute>
                      <xsl:attribute name="title">This client has sent received a successful response (same as reference)</xsl:attribute>
		   </xsl:when>
                   <xsl:otherwise>
           	      <xsl:attribute name="class">waiting</xsl:attribute>
                      <xsl:attribute name="title">This client has not sent a response yet</xsl:attribute>
                   </xsl:otherwise>
                </xsl:choose>
             </xsl:otherwise>
           </xsl:choose>
	   <xsl:attribute name="colspan">2</xsl:attribute>
           <xsl:value-of select="$single"/> 
	 </xsl:element>
      </tr>
</xsl:template>


<xsl:template name="replaceString">
   <xsl:param name="content" />
   <xsl:choose>
     <xsl:when test="contains($content, ',')">
       <xsl:variable name="single" select="substring-before($content, ',')"/>
           <xsl:call-template name="internalLoop">
              <xsl:with-param name="single" select="$single"/>
           </xsl:call-template>

           <xsl:call-template name="replaceString">
              <xsl:with-param name="content" select="substring-after($content, ',')"/>
           </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="single" select="$content"/>
           <xsl:call-template name="internalLoop">
              <xsl:with-param name="single" select="$single"/>
           </xsl:call-template>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<xsl:template match="MBean[@classname='org.xmlBlaster.contrib.replication.SqlStatement']">

   <xsl:param name="replPrefix" select="Attribute[@name='ReplicationPrefix']/@value"/>
   <xsl:param name="failed" select="Attribute[@name='Failed']/@value"/>
   <xsl:param name="received" select="Attribute[@name='Received']/@value"/>
   <xsl:param name="all" select="Attribute[@name='All']/@value"/>
   <xsl:param name="status" select="Attribute[@name='Status']/@value"/>
   <xsl:param name="requestId" select="Attribute[@name='RequestId']/@value"/>
   <xsl:param name="response" select="Attribute[@name='Response']/@value"/>
   <xsl:param name="sql" select="Attribute[@name='Statement']/@value"/>
   <xsl:param name="objectName" select="@objectname"/>

            <table class="inner" width="550" align="center" summary="" border="1">
              <tr>
  	        <td class="normal" title="" colspan="4"><center>Submitted Statement:</center></td>
              </tr>
              <tr>
  	        <td class="normal" title="" colspan="4"><center><textarea id="sqlTxt" name="sqlTxt" cols="70" rows="3"><xsl:value-of select="$sql"/></textarea></center></td>
              </tr>

              <tr>
  	        <td class="normal" title="" colspan="4"><center>Reference Response (given by the Master):</center></td>
              </tr>
              <tr>
  	        <td class="normal" title="" colspan="4"><center><textarea id="sqlTxt" name="sqlTxt" cols="70" rows="6"><xsl:value-of select="$response"/></textarea></center></td>
              </tr>

    	      <tr>
    	        <td align="center" colspan="2" class="normal">
		  <xsl:element name="button">
		    <xsl:attribute name="title">Click to manually refresh this page</xsl:attribute>
		    <xsl:attribute name="class">common</xsl:attribute>
		    <xsl:attribute name="onClick">refresh('mbean?objectname=<xsl:value-of select="$objectName"/>&amp;template=statementDetails')</xsl:attribute>Refresh
		  </xsl:element>
		</td>
    	        <td align="center" colspan="2" class="normal"><button title="Click to go back to the statements page" class="common" onClick="back()">Back</button></td>
    	      </tr>
	    </table>


            <table class="inner" width="550" align="center" summary="" border="1">
               <tr>
                 <th class="normal" title="" colspan="1">Slaves</th>
                 <th class="normal" title="The status of this slave" colspan="1">
		 </th>
               </tr>
	       <tr>
                  <xsl:call-template name="replaceString">
                    <xsl:with-param name="content" select="Attribute[@name='SlaveList']/@value"/>
                  </xsl:call-template>
	       </tr>
	    </table>


</xsl:template>      

<!-- end body -->

</xsl:stylesheet>
