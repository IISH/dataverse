<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ OAI4Solr exposes your Solr indexes using an OAI2 protocol handler.
  ~
  ~     Copyright (c) 2011-2014  International Institute of Social History
  ~
  ~     This program is free software: you can redistribute it and/or modify
  ~     it under the terms of the GNU General Public License as published by
  ~     the Free Software Foundation, either version 3 of the License, or
  ~     (at your option) any later version.
  ~
  ~     This program is distributed in the hope that it will be useful,
  ~     but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~     GNU General Public License for more details.
  ~
  ~     You should have received a copy of the GNU General Public License
  ~     along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="1.0"
                xmlns:ddi="http://www.icpsr.umich.edu/DDI"
                exclude-result-prefixes="ddi"
        >

    <xsl:import href="oai.xsl"/>

    <!-- siteurl_api: proxy url with the api action that ends with a / E.g.: 'http://localhost/api/metadata/dataset/' -->
    <xsl:variable name="siteurl_api" select="//str[@name='siteurl_api']/text()"/>
    <xsl:variable name="key" select="//str[@name='key']/text()"/>

    <xsl:template name="header">
        <header>
            <identifier>
                oai:localhost:<xsl:value-of select="$doc//str[@name='dsPersistentId']"/>
            </identifier>
            <datestamp>
                <xsl:value-of select="$doc//date[@name='dateSort']"/>
            </datestamp>
            <xsl:for-each select="$doc//arr[@name='dvSubject']/str">
                <setSpec>
                    <xsl:value-of select="."/>
                </setSpec>
            </xsl:for-each>
        </header>
    </xsl:template>

    <xsl:template name="metadata">
        <xsl:variable name="ddi_document"
                      select="document(concat($siteurl_api, $doc//long[@name='entityId'], '?key=', $key))"/>
        <metadata>
            <ddi:codeBook xmlns:ddi="http://www.icpsr.umich.edu/DDI" version="2.0">
                <xsl:apply-templates select="$ddi_document/node()/*" mode="ddi"/>
            </ddi:codeBook>
        </metadata>
    </xsl:template>

    <xsl:template match="node()|@*" mode="ddi">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*" mode="ddi"/>
        </xsl:copy>
    </xsl:template>

    <!-- Use these to template matched to prepend the ddi prefix -->
    <xsl:template match="*" mode="ddi">
        <xsl:element name="ddi:{name()}">
            <xsl:apply-templates select="node()|@*" mode="ddi"/>
        </xsl:element>
    </xsl:template>

    <xsl:template name="about"/>

</xsl:stylesheet>