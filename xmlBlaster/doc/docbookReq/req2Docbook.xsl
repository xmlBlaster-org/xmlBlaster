<?xml version='1.0'?>
<!--
Name:      req2Docbook.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a docbook document from a requirement
See:       xmlBlaster/doc/requirements/requirement.dtd
Version:   $Id$
Author:    laghi@swissinfo.ancorg
-->

<!DOCTYPE xsl:stylesheet [
<!ENTITY nbsp   "&#160;">
]>


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                exclude-result-prefixes="#default">

<xsl:include href="./xhtml2Docbook.xsl"/>


<!--
<xsl:output method="xml" 
			doctype-system="http://www.oasis-open.org/docbook/xml/4.2/docbookx.dtd" 
            doctype-public = "-//OASIS//DTD DocBook XML V4.2//EN"
			indent="yes"
/>
-->


<xsl:output method="xml" indent="yes"/>

<!-- 			cdata-section-elements="example" -->

<!-- Create the XML Docbook output for one requirement -->
<xsl:template match="requirement">
<xsl:comment>
<![CDATA[ <!DOCTYPE article PUBLIC "-//OASIS//DTD DocBook XML V4.2//EN" "http://www.oasis-open.org/docbook/xml/4.2/docbookx.dtd"> ]]>
</xsl:comment>

   <article>
  	  <title><xsl:value-of select="@id"/></title>
      <subtitle><xsl:value-of select="topic"/></subtitle>

	  <articleinfo>
        <xsl:attribute name="condition"><xsl:value-of select="@type"/></xsl:attribute>
        <xsl:attribute name="role"><xsl:value-of select="@prio"/></xsl:attribute>
        <xsl:attribute name="userlevel"><xsl:value-of select="@status"/></xsl:attribute>

		<date><xsl:value-of select="date"/></date>
		<releaseinfo><xsl:value-of select="revision"/></releaseinfo>
        <xsl:apply-templates select="author"/>
        <xsl:apply-templates select="hacker"/>
	  </articleinfo>
	
        <xsl:if test="count(description) != 0">
            <section role="description">
            	<title>Description</title>
                 <xsl:apply-templates select="description"/>
            </section>
        </xsl:if>
	
        <xsl:if test="count(developerDescription) != 0">
            <section role="developerDescription">
            	<title>Developer Description</title>
                 <xsl:apply-templates select="developerDescription"/>
            </section>
        </xsl:if>
	
        <xsl:if test="count(example) != 0">
            <section role="examples">
            	<title>Examples</title>
               <xsl:apply-templates select="example"/>
            </section>
        </xsl:if>

        <xsl:if test="count(configuration) != 0">
           <section role="configuration">
      		<title>Configuration</title>
         	<xsl:apply-templates select="configuration"/>
   		</section>
        </xsl:if>

        <xsl:if test="count(testcase) != 0">
      		<section role="testcase">
            	<title>Test Cases</title>
      			<xsl:apply-templates select="testcase"/>
      		</section>
        </xsl:if>
		
      <xsl:if test="count(see) != 0">
   		<section role="see">
	   		<title>Links</title>
		   	<!-- here all 'see' tags are put together as a two column table -->
			   <table role="see"><title></title>
				   <tgroup cols="4">
					   <tbody>
						   <xsl:call-template name="seeTemplate"/>
   					</tbody>
	   			</tgroup>
		   	</table>
   		</section>
      </xsl:if>

      <xsl:if test="count(todo) != 0">
   		<section role="todo">
      		<title>Todos</title>
            <xsl:apply-templates select="todo"/>            
         </section>
      </xsl:if>
   
      <xsl:if test="count(done) != 0">
   		<section role="done">
      		<title>Done</title>
            <xsl:apply-templates select="done"/>
         </section>
      </xsl:if>
      
      <xsl:if test="count(changeRequest) != 0">
   		<section role="changeRequest">
      		<title>ChangeRequests</title>
            <xsl:apply-templates select="changeRequest"/>
         </section>
      </xsl:if>

   </article>
</xsl:template>


<!-- other sub templates here -->

<xsl:template match="author">
	<author>
		<firstname><xsl:value-of select="text()"/></firstname>
		<surname></surname>
		<email></email>
	</author>
</xsl:template>

<xsl:template match="hacker">
	<collab><collabname><xsl:value-of select="text()"/></collabname></collab>
</xsl:template>

<xsl:template match="topic">
   <xsl:comment>the topic</xsl:comment>
   <para role="topic">
      <xsl:value-of select="text()"/>
   </para>
</xsl:template>

<xsl:template match="description">
   <para>
      <xsl:attribute name="role">description</xsl:attribute>
      <xsl:apply-templates/>
   </para>
</xsl:template>

<xsl:template match="serverDeveloperDescription">
   <para>
      <xsl:attribute name="role">serverDeveloperDescription</xsl:attribute>
   	  <xsl:value-of select="text()"/>
   </para>
</xsl:template>

<xsl:template match="date">
      <xsl:value-of select="text()"/>
</xsl:template>


<xsl:template match="example">
	<!-- the type disappears here, but if 'raw' was used, then 'literallayout' will be used here -->
	<para role="example">
	   <xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
      <xsl:if test="@type = 'RAW' or @type = ''">
 		<programlisting>
         <xsl:apply-templates />
      </programlisting>
   	</xsl:if>
      <xsl:if test="@type = 'HTML'">
         <xsl:apply-templates />
   	</xsl:if>
	</para>
</xsl:template>

<xsl:template match="configuration">
	<para>
	   <xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
	   <xsl:attribute name="role">conf-<xsl:value-of select="@where"/></xsl:attribute>
 		<xsl:apply-templates />
   </para>
</xsl:template>


<xsl:template match="testcase">
	<para role="testcase-title">
		<xsl:attribute name="userlevel"><xsl:value-of select="@status"/></xsl:attribute>
		<xsl:value-of select="name"/>
	</para>
	<para role="testcase">
		<xsl:attribute name="userlevel"><xsl:value-of select="@status"/></xsl:attribute>
		<xsl:apply-templates select="comment"/>
		<xsl:apply-templates select="test"/>
	</para>
</xsl:template>

<xsl:template match="comment">
	<para role="comment">
		<xsl:apply-templates/>
	</para>
</xsl:template>

<xsl:template match="test">
	<para role="test">
		<xsl:apply-templates/>
	</para>
</xsl:template>

<!-- 
<xsl:template match="see">
	<note role="see">
		<para>
			<xsl:attribute name="role"><xsl:value-of select="@type"/></xsl:attribute>
			<xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
			<ulink url="./requirements/">The name of the link</ulink>
		</para>
	</note>
</xsl:template>
-->

   <!-- scans through all 'see' childs -->
<xsl:template name="seeTemplate">
	<xsl:for-each select="see">
		<row>
         <entry><para><emphasis role="see">SEE</emphasis></para></entry>
         <entry><para><xsl:value-of select="@type"/></para></entry>
         <xsl:if test="@type='CODE' or @type='API'">
            <entry><para><xsl:value-of select="@lang"/></para></entry>
         </xsl:if>
         <xsl:if test="@type!='CODE' and @type!='API'">
            <entry><para></para></entry>
         </xsl:if>
         <entry>
            <para>
               <ulink role="see">
                  <xsl:attribute name="url"><xsl:value-of select="."/></xsl:attribute>
               	<xsl:attribute name="type"><xsl:value-of select="@type"/></xsl:attribute>
             		<xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
                  <xsl:if test="@label=''">
                     <xsl:value-of select="."/>
                  </xsl:if>
                  <xsl:if test="@label!=''">
                     <xsl:value-of select="@label"/>
                  </xsl:if>
               </ulink>   
            </para>
         </entry>
      </row>
   </xsl:for-each>
</xsl:template>


<xsl:template match="todo">
   <para role="todo">
      <xsl:apply-templates/>
   </para>   
</xsl:template>

<xsl:template match="done">
	<para role="done">
      <xsl:apply-templates/>
   </para>
</xsl:template>

<xsl:template match="changerequest">
	<para role="changerequest">
      <xsl:apply-templates/>
   </para>
</xsl:template>

</xsl:stylesheet>
