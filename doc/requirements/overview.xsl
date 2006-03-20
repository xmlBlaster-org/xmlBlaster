<?xml version='1.0'?>
<!--
Name:      html.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a html table with all requirements, to be used as a 'reference handbook'
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>


<!-- loop over all requirement files -->
<xsl:template match="/">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <meta http-equiv="Pragma" content="no-cache" />
      <meta http-equiv="Expires" content="Tue, 31 Dec 1997 23:59:59 GMT" />
      <link REL="stylesheet" type="text/css" href="../howto/xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated 2006-01-11
   </p>
   <p class="sitetitel">XmlBlaster Programmers Reference Overview</p>

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <thead>
         <tr>
         <th><b>Requirement - ID</b></th>
         <th>Status</th>
         <th>Info</th>
         <th>Topic</th>
         </tr>
      </thead>
      <xsl:for-each select="/files/url">
         <xsl:sort select="document(.)/requirement/@id" order="ascending"/>
         <xsl:sort select="document(.)/requirement/@status" order="ascending"/>
         <xsl:apply-templates select="document(.)/requirement"/>
 
      </xsl:for-each>
   </table>
   <p class="sideend">
   This page is generated from the requirement XML files in the xmlBlaster/doc/requirements directory of the distribution
   </p>
   </body>
   </html>
</xsl:template>


<!-- Create the HTML output for one requirement -->
<xsl:template match="requirement">
   <tr>
      <td class="reqId"><xsl:value-of select="@id"/></td>
      <td>
         <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
         <xsl:value-of select="@status"/>
      </td>
      <td>
         <a><xsl:attribute name="href"><xsl:value-of select="@id"/>.html</xsl:attribute>&gt;&gt;</a>
      </td>
      <td class="topic"><xsl:value-of select="topic"/></td>
   </tr>

</xsl:template>


<!-- Error handling -->
<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>


</xsl:stylesheet>




