<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--
Name:      embedSource.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   used to embedd source into requirements
Version:   $Id$
Author:    laghi@swissinfo.org

Example of an xml output:

<MBeanOperation>
   <Operation objectname="org.xmlBlaster:nodeClass=node,node=&quot;replXbl&quot;,contribClass=contrib,contrib=&quot;replication&quot;" 
              operation="invoke" 
              result="success" 
              return="initiateReplication invoked for slave 'client/ReplWriter-DEE_V_01-AIS/1' and on replication 'ndb' did fail since your status is 'TRANSITION'" 
              returnclass="java.lang.String"/>
</MBeanOperation>


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns:html="http://www.w3.org/1999/xhtml"
                exclude-result-prefixes="#default">
<xsl:output method="xml"/>
  <xsl:template match="@*|*|text()|processing-instruction()|comment()">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()|processing-instruction()|comment()"/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
-->


<xsl:include href="customize.xsl"/>

<xsl:param name="request.destination"/>

<xsl:template name="setDestination">
   <xsl:choose>
     <xsl:when test="$request.destination = 'destinationDetails'">mbean?objectname=<xsl:value-of select="$request.objectname"/>&amp;template=destinationDetails</xsl:when>
     <!-- <xsl:otherwise><xsl:value-of select="$destinationListUrl"/></xsl:otherwise> -->
     <xsl:otherwise>
       <xsl:value-of select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,*&amp;attributes=true&amp;operations=false&amp;notifications=false&amp;constructors=false&amp;template='"/><xsl:value-of select="$request.destination"/>
     </xsl:otherwise>



   </xsl:choose>
</xsl:template>

<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>
<title>Initiate Replication</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<xsl:choose>
  <xsl:when test="starts-with(/MBeanOperation/Operation/attribute::return,'error:') or /MBeanOperation/Operation/attribute::result='error'">
  </xsl:when>
  <xsl:otherwise>
<xsl:element name="meta">
  <xsl:attribute name="http-equiv">refresh</xsl:attribute>
  <xsl:attribute name="content"><xsl:value-of select="0"/>;url=<xsl:call-template name="setDestination"/></xsl:attribute>
</xsl:element>
  </xsl:otherwise>
</xsl:choose>


<link href="styles.css" rel="stylesheet" type="text/css"/>

<script language="JavaScript" type="text/javascript">

function gotoDestination() {
   var url = '<xsl:call-template name="setDestination"/>';
   self.location.href= url;
}


</script>

</head>

<body>
  <center>
    <div class="header">
      <p align="center">Operation Result</p>
    </div>
    <div class="middle">
      <table width="650" align="center" class="external" summary="">

    <xsl:apply-templates/>

       </table>
     </div>
     <div class="footer">
        <a href="http://www.xmlBlaster.org">xmlBlaster.org</a>
     </div>
   </center>

</body>
</html>
</xsl:template>

<xsl:template match='Operation'>
        <xsl:choose>
          <xsl:when test="starts-with(@return,'error:')">
        <tr>         
           <td class="error"><xsl:value-of select="@return"/></td>
        </tr>
          </xsl:when>
          <xsl:otherwise>
        <tr>         
           <td class="normal"><xsl:value-of select="@return"/></td>
        </tr>
          </xsl:otherwise>
        </xsl:choose>


        <xsl:choose>
          <xsl:when test="@result = 'error'">
        <tr>         
           <td class="error"><xsl:value-of select="@errorMsg"/></td>
        </tr>
          </xsl:when>
        </xsl:choose>


        <tr>
          <td colspan="1" align="center"><button class="small" title="Click to return to the replication list" onClick="gotoDestination()"><xsl:value-of select="$request.destination"/></button></td>
        </tr>
</xsl:template>      

<!-- end body -->

</xsl:stylesheet>



