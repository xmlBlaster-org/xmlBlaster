<?xml version='1.0'?>
<!--
Name:      detail.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a detailed html view on one requirement
Version:   $Id: detail.xsl,v 1.1 2000/03/24 22:18:58 ruff Exp $
Author:    ruff@swand.lake.de
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>

<xsl:template match="requirement">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <link REL="stylesheet" type="text/css" href="../xmlBlaster.css" />
      <title>xmlBlaster - Requirement <xsl:value-of select="@id"/></title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date: 2000/03/24 22:18:58 $ $Author: ruff $
   </p>
   <p class="sitetitel"><xsl:value-of select="@id"/></p>

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <tr>
         <td class="reqId">Requirement Id</td>
         <td><xsl:value-of select="@id"/></td>
      </tr>
      <tr>
         <td class="reqId">Type</td>
         <td><xsl:value-of select="@type"/></td>
      </tr>
      <tr>
         <td class="reqId">Priority</td>
         <td><xsl:value-of select="@prio"/></td>
      </tr>
      <tr>
         <td class="reqId">Status</td>
         <td>
         	<xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
            <xsl:value-of select="@status"/>
         </td>
      </tr>
      <tr>
         <td class="reqId">Description</td>
         <td><xsl:value-of select="description"/></td>
      </tr>
      <tr>
         <td class="reqId">Example</td>
         <td><pre><xsl:value-of select="example"/></pre></td>
      </tr>
      <xsl:for-each select="see">
         <tr>
            <td class="reqId">See</td>
            <td>
               <xsl:if test="@type='API'">
               <a>
         			<xsl:attribute name="href">../api/<xsl:value-of select="."/></xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </xsl:if>
               <xsl:if test="@type!='API'">
               <a>
                  <xsl:value-of select="."/>
               </a>
               </xsl:if>
            </td>
         </tr>
      </xsl:for-each>
      <xsl:for-each select="testcase">
         <xsl:if test="@status='CLOSED'">
            <tr>
               <td class="reqId">Testcase</td>
               <td>
                  <xsl:if test="test/@tool='SUITE'">
                  <a>
            			<xsl:attribute name="href">../../<xsl:value-of select="test"/>.html</xsl:attribute>
                     <xsl:value-of select="test"/>
                  </a>
                  </xsl:if>
                  <xsl:if test="test/@tool!='SUITE'">
                  <a>
                     <xsl:value-of select="test"/>
                  </a>
                  </xsl:if>
               </td>
            </tr>
         </xsl:if>
      </xsl:for-each>
   </table>

   <p class="sideend">
   This page is generated from the requirement XML file xmlBlaster/doc/requirements/<xsl:value-of select="@id"/>.xml
   </p>
   </body>
   </html>
</xsl:template>



<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>


</xsl:stylesheet>




