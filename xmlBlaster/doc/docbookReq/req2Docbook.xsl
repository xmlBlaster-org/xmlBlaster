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
				   <tgroup cols="2">
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


<xsl:template match="see">
	<!-- role is type and userlevel is lang -->
	<note role="see">
		<para>
			<xsl:attribute name="role"><xsl:value-of select="@type"/></xsl:attribute>
			<xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
			<ulink url="./requirements/">The name of the link</ulink>
		</para>
	</note>
</xsl:template>

<!-- scans through all 'see' childs -->
<xsl:template name="seeTemplate">
	<xsl:for-each select="see">
		<row>
			<xsl:attribute name="role"><xsl:value-of select="@type"/></xsl:attribute>
			<xsl:attribute name="userlevel"><xsl:value-of select="@lang"/></xsl:attribute>
            <xsl:if test="@type='API'">
              <xsl:choose>
                <xsl:when test="@lang='CPP'">
					<entry><para><emphasis>API-CPP</emphasis> See API</para></entry>
					<entry>
						<para>
							<xsl:call-template name="tokenize">
								<xsl:with-param name="pat" select="."/>
								<xsl:with-param name="prefix" select="'../doxygen/cpp/html/class'"/>
								<xsl:with-param name="postfix" select="'.html'"/>
								<xsl:with-param name="withNamespace" select="'true'"/>
							</xsl:call-template>
						</para>
					</entry>
                </xsl:when>

                <xsl:otherwise>
					<entry><para><emphasis>API</emphasis> See API</para></entry>
					<entry><para><ulink><xsl:attribute name="url">../api/<xsl:value-of select="translate(.,'.','/')"/>.html</xsl:attribute>
	                    <xsl:value-of select="."/>
					</ulink></para></entry>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:if>

            <xsl:if test="@type='REQ'">
				<entry><para><emphasis>REQ</emphasis> See REQ</para></entry>
				<entry>
					<para>
						<ulink>
							<xsl:attribute name="url"><xsl:value-of select="."/>.html</xsl:attribute>
							<xsl:if test="@label=''"><xsl:value-of select="."/></xsl:if>
							<xsl:if test="@label!=''"><xsl:value-of select="@label"/></xsl:if>
						</ulink>
					</para>
				</entry>
            </xsl:if>

            <xsl:if test="@type='OTHER'">
				<entry><para><emphasis>OTHER</emphasis>See</para></entry>
				<entry><para><ulink><xsl:value-of select="."/></ulink></para></entry>
            </xsl:if>

            <xsl:if test="@type='INTERNET'">
				<entry><para><emphasis>INTERNET</emphasis>See</para></entry>
				<entry>
					<para>
						<ulink>
							<xsl:attribute name="url"><xsl:value-of select="."/></xsl:attribute>
							<!-- <xsl:attribute name="target">others</xsl:attribute> -->
							<xsl:if test="@label=''"><xsl:value-of select="."/></xsl:if>
							<xsl:if test="@label!=''"><xsl:value-of select="@label"/></xsl:if>
						</ulink>
					</para>
				</entry>
            </xsl:if>

            <xsl:if test="@type='LOCAL'">
				<entry><para><emphasis>LOCAL</emphasis>See</para></entry>
				<entry>
					<para>
						<ulink>
							<xsl:attribute name="url"><xsl:value-of select="."/></xsl:attribute>
							<xsl:value-of select="."/>
						</ulink>
					</para>
				</entry>
            </xsl:if>

            <xsl:if test="@type='CODE'">

				<entry><para><emphasis>CODE</emphasis>See CODE</para></entry>
				<entry>
					<para>
              			<xsl:if test="@lang='CPP'">
							<xsl:call-template name="tokenize">
								<xsl:with-param name="pat" select="."/>
								<xsl:with-param name="prefix" select="'../doxygen/cpp/html/'"/>
								<xsl:with-param name="postfix" select="'_8cpp-source.html'"/>
								<xsl:with-param name="withNamespace" select="'false'"/>
							</xsl:call-template>
               			</xsl:if>

						<xsl:if test="@lang='Java'">
							<ulink>
								<xsl:attribute name="url">../../src/java/<xsl:value-of select="translate(.,'.','/')"/>.java.html</xsl:attribute>
								<xsl:value-of select="."/>
							</ulink>
						</xsl:if>

						<xsl:if test="@lang='C'">
							<ulink>
								<xsl:attribute name="url">../../src/c/<xsl:value-of select="."/>.html</xsl:attribute>
								<xsl:value-of select="."/>
							</ulink>
						</xsl:if>

						<xsl:if test="@lang='PYTHON'">
							<ulink>
								<xsl:attribute name="url">../../src/python/<xsl:value-of select="."/>.html</xsl:attribute>
								<xsl:value-of select="."/>
							</ulink>
						</xsl:if>

						<xsl:if test="@lang='PERL'">
							<ulink>
								<xsl:attribute name="url">../../demo/perl/xmlrpc/<xsl:value-of select="."/>.html</xsl:attribute>
								<xsl:value-of select="."/>
							</ulink>
						</xsl:if>
					</para>
				</entry>
            </xsl:if>
         </row>
      </xsl:for-each>
</xsl:template>

<!-- These are added to tokenize a string i.e. to use it as translate but to be able to replace strings
     which are longer than just one character
-->

<xsl:template name="tokenize">
  <xsl:param name="pat"/><!-- String with record separators inserted -->
  <xsl:param name="myUrl"/>
  <xsl:param name="prefix"/>
  <xsl:param name="postfix"/>
  <xsl:param name="withNamespace"/>
  <xsl:choose>
    <!-- Doxygen makes from 'org::xmlBlaster::util::I_LogFactory' -> 'classorg_1_1xmlBlaster_1_1util_1_1I__LogFactory.html' -->
    <xsl:when test="contains($pat,'::')">
      <xsl:call-template name="tokenize">
        <xsl:with-param name="pat" select="substring-after($pat,'::')"/>
        <xsl:with-param name="myUrl" select="concat($myUrl,substring-before($pat,'::'),'_1_1')"/>
        <xsl:with-param name="prefix" select="$prefix"/>
        <xsl:with-param name="postfix" select="$postfix"/>
        <xsl:with-param name="withNamespace" select="$withNamespace"/>
      </xsl:call-template>
    </xsl:when>
    <xsl:when test="contains($pat,'I_')">
      <xsl:call-template name="tokenize">
        <xsl:with-param name="pat" select="substring-after($pat,'I_')"/>
        <xsl:with-param name="myUrl" select="concat($myUrl,substring-before($pat,'I_'),'I__')"/>
        <xsl:with-param name="prefix" select="$prefix"/>
        <xsl:with-param name="postfix" select="$postfix"/>
        <xsl:with-param name="withNamespace" select="$withNamespace"/>
      </xsl:call-template>
    </xsl:when>
    <xsl:otherwise>
      <xsl:choose>
         <xsl:when test="contains($withNamespace,'true')">
           <xsl:element name="ulink">
              <xsl:attribute name="url"><xsl:value-of select="concat($prefix,$myUrl,$pat,$postfix)"/>
              </xsl:attribute>
              <xsl:value-of select="$pat"/>
           </xsl:element>
         </xsl:when>
         <xsl:otherwise>
           <xsl:element name="ulink">
              <xsl:attribute name="url"><xsl:value-of select="concat($prefix,$pat,$postfix)"/>
              </xsl:attribute>
              <xsl:value-of select="$pat"/>
           </xsl:element>
         </xsl:otherwise>
       </xsl:choose>
    </xsl:otherwise>
  </xsl:choose>
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
