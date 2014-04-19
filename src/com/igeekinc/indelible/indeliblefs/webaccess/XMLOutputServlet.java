/*
 * Copyright 2002-2014 iGeek, Inc.
 * All Rights Reserved
 * @Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.@
 */
 
package com.igeekinc.indelible.indeliblefs.webaccess;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.URIResolver;

import org.apache.log4j.Logger;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
public abstract class XMLOutputServlet extends HttpServlet implements ErrorHandler
{
	private static final long	serialVersionUID	= -1752729635062658986L;
	int           foundErrors;
    DOMParser     parser;
    private Document      stylesheet;
    File          stylesheetFile=null;
    long          sfLastModified;
    String        encoding;
    ServletConfig myConfig;
    Logger        logger = Logger.getLogger(getClass());
    public XMLOutputServlet()
    {
        init();
    }

    public void init()
    {
        parser = new DOMParser();
    }

    @Override
    public void init(ServletConfig config)
    throws ServletException
    {
        super.init(config);
        myConfig = config;
        init();
    }

    protected String getContentType()
    {
    	return "text/html";
    }

    protected synchronized void doWork(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, java.io.IOException, SAXException,
    javax.xml.transform.TransformerConfigurationException,
    javax.xml.transform.TransformerException
    {
        Document  servletOutput;

        if (stylesheetFile != null)
        {
            if (stylesheetFile.lastModified() != sfLastModified)
            {
                reloadStylesheet();
            }
        }
        resp.setContentType(getContentType()+"; charset="+encoding);
        if (needsAuthentication(req))
        {
            if (!authenticate(req))
            {
                askPassword(resp);
                return;
            }
        }

        servletOutput = doRequest(req, resp);
        if (servletOutput != null)
        {
            doProcess(stylesheet, servletOutput, resp.getOutputStream());

            /*
            OutputFormat  defOF = new OutputFormat(Method.XML, "UTF-8", false);
            defOF.setOmitXMLDeclaration(true);
            defOF.setOmitDocumentType(true);
            defOF.setIndenting(true);
            XMLSerializer xmlSer = new XMLSerializer(System.err, defOF);

            xmlSer.serialize(servletOutput);
            */
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, java.io.IOException
    {
        try
        {
            doWork(req, resp);
        }
        catch (Exception e)
        {
            logger.warn("Caught exception in doWork", e);
            throw new ServletException(e.getMessage());
        }
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, java.io.IOException
    {
        try
        {
            doWork(req, resp);
        }
        catch (Exception e)
        {
            logger.warn("Caught exception in doWork", e);
            throw new ServletException(e.getMessage());
        }
    }

    protected void setStylesheet(String stylesheetFileName)
    throws IOException, SAXException
    {
        String realName = myConfig.getServletContext().getRealPath(stylesheetFileName);
        stylesheetFile = new File(realName);
        reloadStylesheet();
    }

    protected void setStylesheet(Document stylesheet)
    {
    	this.stylesheet = stylesheet;
    }
    
    public Document getStylesheet() throws IOException, SAXException
    {
        if (stylesheetFile != null)
        {
            if (stylesheetFile.lastModified() != sfLastModified)
            {
                reloadStylesheet();
            }
        }
    	return stylesheet;
    }
    
    protected void reloadStylesheet()
    throws IOException, SAXException
    {
        System.err.println("Reloading stylesheet "+stylesheetFile);
        FileInputStream   xslDoc = new FileInputStream(stylesheetFile);
        Document xsl = XMLUtils.getDocument(xslDoc);
        sfLastModified = stylesheetFile.lastModified();
        stylesheet = xsl;
    }

    void askPassword(HttpServletResponse response)
    {
        response.setStatus(response.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate",
        "BASIC realm=\"protected-area\"");
    }

    protected boolean needsAuthentication(HttpServletRequest req)
    {
        return false;
    }
    protected boolean authenticate(HttpServletRequest req)
    {
        String authhead=req.getHeader("Authorization");

        if(authhead!=null)
        {
            //*****Decode the authorisation String*****
            String usernpass=Base64.decode(authhead.substring(6));
            //*****Split the username from the password*****
            String user=usernpass.substring(0,usernpass.indexOf(":"));
            String password=usernpass.substring(usernpass.indexOf(":")+1);

            if (checkUser(user) && checkPassword(password))
                return true;
        }

        return false;
    }

    protected boolean checkUser(String user)
    {
        return false;
    }

    protected boolean checkPassword(String password)
    {
        return false;
    }
    protected void setEncoding(String encoding)
    {
        this.encoding = encoding;
    }

    abstract protected Document doRequest(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, java.io.IOException;

    protected synchronized Document doProcess(Document stylesheet, Document xml)
    throws java.io.IOException,
    java.net.MalformedURLException,
    org.xml.sax.SAXException,
    javax.xml.transform.TransformerConfigurationException,
    javax.xml.transform.TransformerException
    {
        Document  outputDoc = new DocumentImpl();

        // 1. Instantiate a TransformerFactory.
        javax.xml.transform.TransformerFactory tFactory =
            javax.xml.transform.TransformerFactory.newInstance();

        tFactory.setURIResolver(getResolver(tFactory.getURIResolver()));	// Use our URI resolver to find includes
        
        // 2. Use the TransformerFactory to process the stylesheet Source and
        //    generate a Transformer.
        javax.xml.transform.Transformer transformer = tFactory.newTransformer
        (new javax.xml.transform.dom.DOMSource(stylesheet));

        // 3. Use the Transformer to transform an XML Source and send the
        //    output to a Result object.
        transformer.transform
        (new javax.xml.transform.dom.DOMSource(xml), new javax.xml.transform.dom.DOMResult(outputDoc));


        return(outputDoc);
    }

    protected synchronized void doProcess(Document stylesheet, Document xml, OutputStream out)
    throws java.io.IOException,
    java.net.MalformedURLException,
    org.xml.sax.SAXException,
    javax.xml.transform.TransformerConfigurationException,
    javax.xml.transform.TransformerException

    {
        // 1. Instantiate a TransformerFactory.
        javax.xml.transform.TransformerFactory tFactory =
            javax.xml.transform.TransformerFactory.newInstance();
        
        //tFactory.setURIResolver(getResolver(tFactory.getURIResolver()));	// Use our URI resolver to find includes
        
        // 2. Use the TransformerFactory to process the stylesheet Source and
        //    generate a Transformer.
        javax.xml.transform.Transformer transformer = tFactory.newTransformer
        (new javax.xml.transform.dom.DOMSource(stylesheet));

        // 3. Use the Transformer to transform an XML Source and send the
        //    output to a Result object.
        transformer.transform
        (new javax.xml.transform.dom.DOMSource(xml), new javax.xml.transform.stream.StreamResult(out));




    }
    
    protected synchronized void doProcess(Document stylesheet, Document xml, Writer out)
    throws java.io.IOException,
    java.net.MalformedURLException,
    org.xml.sax.SAXException,
    javax.xml.transform.TransformerConfigurationException,
    javax.xml.transform.TransformerException
    {
        // 1. Instantiate a TransformerFactory.
        javax.xml.transform.TransformerFactory tFactory =
            javax.xml.transform.TransformerFactory.newInstance();

        tFactory.setURIResolver(getResolver(tFactory.getURIResolver()));	// Use our URI resolver to find includes
        // 2. Use the TransformerFactory to process the stylesheet Source and
        //    generate a Transformer.
        javax.xml.transform.Transformer transformer = tFactory.newTransformer
        (new javax.xml.transform.dom.DOMSource(stylesheet));

        // 3. Use the Transformer to transform an XML Source and send the
        //    output to a Result object.
        transformer.transform
        (new javax.xml.transform.dom.DOMSource(xml), new javax.xml.transform.stream.StreamResult(out));



    }
    
    protected URIResolver getResolver(URIResolver parentResolver)
    {
    	return new XMLServletURIResolver(parentResolver, getServletContext().getRealPath("stylesheets"));
    }
    //  Warning Event Handler
    public void warning (SAXParseException e)
    throws SAXException {
        logger.warn ("Warning:  "+e);
        foundErrors++;
    }

    //  Error Event Handler
    public void error (SAXParseException e)
    throws SAXException {
        logger.error ("Error:  "+e);
        foundErrors++;
    }

    //  Fatal Error Event Handler
    public void fatalError (SAXParseException e)
    throws SAXException {
        logger.fatal ("Fatal Error:  "+e);
        foundErrors++;
    }
}