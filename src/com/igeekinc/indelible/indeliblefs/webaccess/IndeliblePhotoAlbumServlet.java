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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xml.serialize.Method;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletContextHandler.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.igeekinc.indelible.indeliblefs.exceptions.ObjectNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.security.AuthenticationFailureException;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.Album;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.Face;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.Image;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.ImageFace;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.IndeliblePhotoAlbumCore;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.LibraryVolumeInfo;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.iPhotoLibrary;
import com.igeekinc.indelible.oid.IndelibleFSObjectID;
import com.igeekinc.indelible.oid.ObjectIDFactory;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.ErrorLogMessage;

public class IndeliblePhotoAlbumServlet extends XMLOutputServlet
{
	private static final long	serialVersionUID	= 2658792475101516156L;
	IndeliblePhotoAlbumCore core;
	public IndeliblePhotoAlbumServlet() throws IOException, UnrecoverableKeyException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IllegalStateException, NoSuchProviderException, SignatureException, AuthenticationFailureException, InterruptedException, SAXException
	{
	}
	
    @Override
    public void init(ServletConfig config)
    throws ServletException
    {
        super.init(config);
		try
		{
			ServletContext context = config.getServletContext();
			if (context instanceof Context)
			{
				ServletContextHandler contextHandler = (ServletContextHandler)((Context)context).getContextHandler();
				core = new IndeliblePhotoAlbumCore(contextHandler);
				Document stylesheet =  XMLUtils.getDocument(IndelibleWebAccessServlet.class.getResourceAsStream("IndeliblePhotoAlbumStylesheet.xsl"));
				setStylesheet(stylesheet);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

    @Override
    protected void doWork(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, java.io.IOException, SAXException,
    javax.xml.transform.TransformerConfigurationException,
    javax.xml.transform.TransformerException
    {
        Document  servletOutput;
        synchronized(this)
        {
        	if (stylesheetFile != null)
        	{
        		if (stylesheetFile.lastModified() != sfLastModified)
        		{
        			reloadStylesheet();
        		}
        	}
        }
        resp.setContentType("text/html; charset="+encoding);
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
            doProcess(getStylesheet(), servletOutput, resp.getOutputStream());

            OutputFormat  defOF = new OutputFormat(Method.XML, "UTF-8", false);
            defOF.setOmitXMLDeclaration(true);
            defOF.setOmitDocumentType(true);
            defOF.setIndenting(true);
            XMLSerializer xmlSer = new XMLSerializer(System.err, defOF);

            xmlSer.serialize(servletOutput);
        }
    }
	@Override
	protected Document doRequest(HttpServletRequest req,
			HttpServletResponse resp) throws ServletException, IOException
	{
		DocumentImpl buildDoc = new DocumentImpl();
		Map<String, String[]>paramMap = req.getParameterMap();
		String [] keys = paramMap.keySet().toArray(new String[0]);
		if (keys.length < 1)
		{
			resp.sendError(200);
		}
		else
		{
			try
			{
				String command = keys[0].toLowerCase().trim();
				if (command.startsWith("large"))
				{
					if (command.equals("largeslideshow"))
					{
						Element startElem = buildDoc.createElement("largeslide");
						buildDoc.appendChild(startElem);
						listPhotos(req, resp, buildDoc, startElem);
					}
					if (command.equals("largeslideshowforperson"))
					{
						Element startElem = buildDoc.createElement("largefaceslide");
						buildDoc.appendChild(startElem);
						listPhotosForFace(req, resp, buildDoc, startElem);
					}
				}
				else
				{
					Element startElem = buildDoc.createElement("regular");
					buildDoc.appendChild(startElem);
					if (command.equals("listlibraries"))
					{
						listLibraries(req, resp, buildDoc, startElem);
					}
					if (command.equals("listalbums"))
						listAlbums(req, resp, buildDoc, startElem);
					if (command.equals("listphotos"))
						listPhotos(req, resp, buildDoc, startElem);
					if (command.equals("listfaces"))
						listFaces(req, resp, buildDoc, startElem);
					if (command.equals("getimage"))
					{
						getImage(req, resp, paramMap);
						return null;
					}
					if (command.equals("getthumb"))
					{
						getThumb(req, resp);
						return null;
					}
					if (command.equals("getthumbforface"))
					{
						getThumbForFace(req, resp);
						return null;
					}
					if (command.equals("getface"))
					{
						getFaceThumbImage(req, resp);
						return null;
					}
					if (command.equals("imagesforface"))
						imagesForFace(req, resp, buildDoc, startElem);
					if (command.equals("imageforface"))
					{
						getImageForFace(req, resp, paramMap);
						return null;
					}
					if (command.equals("zipfileforface"))
					{
						zipfileForFace(req, resp);
						return null;
					}
					if (command.equals("zipfileforalbum"))
					{
						zipfileForAlbum(req, resp);
						return null;
					}
					if (command.equals("listalbumsandfaces"))
						listAlbumsAndFaces(req, resp, buildDoc, startElem);
				}
			} catch (IndelibleWebAccessException e)
			{
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
				doError(resp, e);
			}
			catch(Throwable t)
			{
				doError(resp, new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, t));
			}
		}
		return buildDoc;
	}
	
	public void doError(HttpServletResponse resp, IndelibleWebAccessException exception)
    {
        resp.setStatus(500);
    }
	
	public void listLibraries(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException
	{
		Element rootElem = buildDoc.createElement("librariesList");
		startElem.appendChild(rootElem);
		for (LibraryVolumeInfo curVolumeInfo:core.getLibraryVolumeInfo())
		{
			Element volumeElement = buildDoc.createElement("library");
			XMLUtils.appendSingleValElement(buildDoc, volumeElement, "id", curVolumeInfo.getVolumeID().toString());
			String volumeName = curVolumeInfo.getVolumeName();
			if (volumeName != null)
			{
				XMLUtils.appendSingleValElement(buildDoc, volumeElement, "name", volumeName);
			}
			rootElem.appendChild(volumeElement);
		}
	}
	
    public void listAlbums(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        Element rootElem = buildDoc.createElement("albumslist");
        startElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "rsspath", "/rss/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "library", library.getLibraryName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "libraryPath", "/photos/"+retrieveVolumeID.toString());
        	addAlbumList(buildDoc, rootElem, library);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }

    public void listAlbumsAndFaces(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        Element rootElem = buildDoc.createElement("albumsandfaceslist");
        startElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "rsspath", "/rss/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "library", library.getLibraryName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "libraryPath", "/photos/"+retrieveVolumeID.toString());
        	addAlbumList(buildDoc, rootElem, library);
        	addFaceList(buildDoc, rootElem, library);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }
	private void addAlbumList(Document buildDoc, Element rootElem, iPhotoLibrary library)
	{
		String [] guids = library.getAlbumGUIDs();
		TreeMap<String, String>sorter = new TreeMap<String, String>();
		for (String curGUID:guids)
		{
			Album curAlbum = library.getAlbum(curGUID);
			if ((curAlbum.getAlbumType().equals("Regular") || curAlbum.getAlbumType().equals("Event")) && !curAlbum.getGUID().equals("lastImportAlbum") && curAlbum.getNumPhotos() > 0)
			{
				sorter.put(curAlbum.getAlbumName(), curGUID);
			}
		}
		for (Map.Entry<String, String>curEntry:sorter.entrySet())
		{
			String albumName = curEntry.getKey();
			String curGUID = curEntry.getValue();
			Element curAlbumElement = buildDoc.createElement("album");
			String encodedGUID = Base64.encode(curGUID);
			XMLUtils.appendSingleValElement(buildDoc, curAlbumElement, "guid", encodedGUID);
			XMLUtils.appendSingleValElement(buildDoc, curAlbumElement, "name", albumName);
			
			Album album = library.getAlbum(curGUID);
			Image firstImage = album.getImageAtIndex(0);
			XMLUtils.appendSingleValElement(buildDoc, curAlbumElement, "firstImageKey", firstImage.getKey());
			rootElem.appendChild(curAlbumElement);
		}
	}
    
    public void listPhotos(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		Element webElem = buildDoc.createElement("web");
        startElem.appendChild(webElem);
        Element rootElem = buildDoc.createElement("photoslist");
        webElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String albumToListGUID = Base64.decode(reqPath.getComponent(1));
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Album albumToList = library.getAlbum(albumToListGUID);
        	if (albumToList == null)
        		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
        	String encodedGUID = Base64.encode(albumToListGUID);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString()+"/"+encodedGUID);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "volumePath", "/photos/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "albumName", albumToList.getAlbumName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "library", library.getLibraryName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "libraryPath", "/photos/"+retrieveVolumeID.toString());
        	for (String curKey:albumToList.getKeys())
        	{
        	    Element imageElem;
        	    imageElem = buildDoc.createElement("image");
        	    rootElem.appendChild(imageElem);
        		Image curImage = albumToList.getImageForKey(curKey);
        		XMLUtils.appendSingleValElement(buildDoc, imageElem, "key", curKey);
        		String title;
        		
        		if (curImage.getCaption() != null)
        			title = curImage.getCaption();
        		else
        			title = curImage.getImagePath().getName();
        		XMLUtils.appendSingleValElement(buildDoc, imageElem, "title", title);
        		for (ImageFace curImageFace:curImage.getFaces())
        		{
        			Face curFace = library.getFaceByKey(curImageFace.getFaceKey());
        			Element faceElem = buildDoc.createElement("face");
        			imageElem.appendChild(faceElem);
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "name", curFace.getName());
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "faceKey", curFace.getKey());
        		}
        	}
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }

    public void listPhotosForFace(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		Element webElem = buildDoc.createElement("web");
        startElem.appendChild(webElem);
        Element rootElem = buildDoc.createElement("photoslist");
        webElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        String faceKey = reqPath.getComponent(1);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(faceKey);
        	
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString()+"/"+faceKey);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "volumePath", "/photos/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "albumName", retrieveFace.getName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "library", library.getLibraryName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "libraryPath", "/photos/"+retrieveVolumeID.toString());
        	Image [] imagesForFace = library.getImagesForFace(retrieveFace);
        	for (Image curImage:imagesForFace)
        	{
        	    Element imageElem;
        	    imageElem = buildDoc.createElement("image");
        	    rootElem.appendChild(imageElem);
        		XMLUtils.appendSingleValElement(buildDoc, imageElem, "key", Base64.encode(curImage.getGUID()));
        		String title;
        		
        		if (curImage.getCaption() != null)
        			title = curImage.getCaption();
        		else
        			title = curImage.getImagePath().getName();
        		XMLUtils.appendSingleValElement(buildDoc, imageElem, "title", title);
        		for (ImageFace curImageFace:curImage.getFaces())
        		{
        			Face curFace = library.getFaceByKey(curImageFace.getFaceKey());
        			Element faceElem = buildDoc.createElement("face");
        			imageElem.appendChild(faceElem);
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "name", curFace.getName());
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "faceKey", curFace.getKey());
        		}
        	}
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }
    public void zipfileForAlbum(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String albumToListGUID = Base64.decode(reqPath.getComponent(1));
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Album albumToList = library.getAlbum(albumToListGUID);
        	if (albumToList == null)
        		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
        	resp.setContentType("application/zip");
        	resp.setHeader("Content-Disposition", "attachment; filename=\"" + albumToList.getAlbumName() + ".zip\"");
        	ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(resp.getOutputStream()));
        	byte [] buffer = new byte[1024*1024];
        	for (String curKey:albumToList.getKeys())
        	{
        		Image curImage = albumToList.getImageForKey(curKey);
        		ZipEntry curImageEntry = new ZipEntry(curImage.getImagePath().getName());
        		outputStream.putNextEntry(curImageEntry);
        		InputStream imageStream = library.getStreamForImage(curImage);
        		int length;
        		while ((length = imageStream.read(buffer)) > 0)
        			outputStream.write(buffer, 0, length);
        		outputStream.closeEntry();
        		imageStream.close();
        	}
        	outputStream.close();
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }
    public void listFaces(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        Element rootElem = buildDoc.createElement("facelist");
        startElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "rsspath", "/rss/"+retrieveVolumeID.toString());
        	addFaceList(buildDoc, rootElem, library);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }

	private void addFaceList(Document buildDoc, Element rootElem, iPhotoLibrary library)
	{
		Face [] faces = library.getFaces();
		Arrays.sort(faces, new Comparator<Face>()
		{

			@Override
			public int compare(Face arg0, Face arg1)
			{
				return arg0.getName().compareTo(arg1.getName());
			}
		});
		for (Face curFace:faces)
		{
			Element curFaceElement = buildDoc.createElement("face");
			XMLUtils.appendSingleValElement(buildDoc, curFaceElement, "name", curFace.getName());
			XMLUtils.appendSingleValElement(buildDoc, curFaceElement, "key", curFace.getKey());
			rootElem.appendChild(curFaceElement);
		}
	}
    
    public void imagesForFace(HttpServletRequest req, HttpServletResponse resp, Document buildDoc, Element startElem) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        Element rootElem = buildDoc.createElement("faceimagelist");
        startElem.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        String faceKey = reqPath.getComponent(1);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(faceKey);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "name", retrieveFace.getName());
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "faceKey", faceKey);
        	Image [] imagesForFace = library.getImagesForFace(retrieveFace);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", "/photos/"+retrieveVolumeID.toString()+"/"+faceKey);
        	XMLUtils.appendSingleValElement(buildDoc, rootElem, "volumePath", "/photos/"+retrieveVolumeID.toString());
        	for (Image curImage:imagesForFace)
        	{
        		Element imageElem;
         	    imageElem = buildDoc.createElement("image");
         	    rootElem.appendChild(imageElem);
         		XMLUtils.appendSingleValElement(buildDoc, imageElem, "key", Base64.encode(curImage.getGUID()));
        		
        		for (ImageFace curImageFace:curImage.getFaces())
        		{
        			Face curFace = library.getFaceByKey(curImageFace.getFaceKey());
        			Element faceElem = buildDoc.createElement("face");
        			imageElem.appendChild(faceElem);
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "name", curFace.getName());
        			XMLUtils.appendSingleValElement(buildDoc, faceElem, "faceKey", curFace.getKey());
        		}
        	}
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }
    
    public void zipfileForFace(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        String faceKey = reqPath.getComponent(1);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(faceKey);
        	Image [] imagesForFace = library.getImagesForFace(retrieveFace);
        	resp.setContentType("application/zip");
        	resp.setHeader("Content-Disposition", "attachment; filename=\"" + retrieveFace.getName() + ".zip\"");
        	ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(resp.getOutputStream()));
        	byte [] buffer = new byte[1024*1024];
        	for (Image curImage:imagesForFace)
        	{
        		ZipEntry curImageEntry = new ZipEntry(curImage.getImagePath().getName());
        		outputStream.putNextEntry(curImageEntry);
        		InputStream imageStream = library.getStreamForImage(curImage);
        		int length;
        		while ((length = imageStream.read(buffer)) > 0)
        			outputStream.write(buffer, 0, length);
        		outputStream.closeEntry();
        		imageStream.close();
        	}
        	outputStream.close();
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
    }
    public void getThumb(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String albumToRetrieveFromGUID = Base64.decode(reqPath.getComponent(1));
		String retrieveKey = reqPath.getComponent(2);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Album albumToRetrieveFrom = library.getAlbum(albumToRetrieveFromGUID);
        	if (albumToRetrieveFrom == null)
        		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
        	Image retrieveImage = albumToRetrieveFrom.getImageForKey(retrieveKey);
        	String fileType = retrieveImage.getThumbMIMEType();
        	InputStream thumbStream = library.getStreamForImageThumb(retrieveImage);
        	resp.setContentType("image/"+fileType);
        	logger.error(new ErrorLogMessage("getthumb called for {0}, type={1}", new Object[]{retrieveKey, fileType}));
        	if (thumbStream != null)
        	{
                OutputStream outStream = resp.getOutputStream();
                byte [] outBuffer = new byte[resp.getBufferSize()];
                int bytesRead;
                while ((bytesRead = thumbStream.read(outBuffer)) > 0)
                    outStream.write(outBuffer, 0, bytesRead);
            }
        	thumbStream.close();
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } 
        return;
    }
    
    public void getFaceThumbImage(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
    	String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String retrieveKey = reqPath.getComponent(1);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(retrieveKey);
        	Image retrieveImage = library.getImageForGUID(retrieveFace.getKeyImageGUID());
        	String fileType = retrieveImage.getThumbMIMEType();
        	InputStream thumbStream = library.getStreamForFaceImage(retrieveFace);
        	resp.setContentType("image/"+fileType);
        	logger.error(new ErrorLogMessage("getface called for {0}, type={1}", new Object[]{retrieveKey, fileType}));
        	if (thumbStream != null)
        	{
                OutputStream outStream = resp.getOutputStream();
                byte [] outBuffer = new byte[resp.getBufferSize()];
                int bytesRead;
                while ((bytesRead = thumbStream.read(outBuffer)) > 0)
                    outStream.write(outBuffer, 0, bytesRead);
            }
        	thumbStream.close();
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } 
        return;
    }
    
    public void getImageForFace(HttpServletRequest req, HttpServletResponse resp, Map<String, String []>paramMap) throws RemoteException, IndelibleWebAccessException
    {
    	String path=req.getPathInfo();
        String []formatParamsValues=paramMap.get("format");
        int height=-1, width=-1;	// height/width to output at.  -1 indicates no preference
        if (formatParamsValues != null)
        {
        	for (String curFormatParams:formatParamsValues)
        	{
        		StringTokenizer tokenizer = new StringTokenizer(curFormatParams, ",");
        		while (tokenizer.hasMoreTokens())
        		{
        			String curFormatParam = tokenizer.nextToken();
        			if (curFormatParam.startsWith("width="))
        			{
        				String widthString = curFormatParam.substring("width=".length());
        				try
        				{
        					width = Integer.parseInt(widthString);
        					if (width < 0)
        						width = -1;
        				}
        				catch (NumberFormatException e)
        				{
        					logger.error("Width format "+curFormatParam+" was unparsable, defaulting to native size");
        				}
        			}
        			if (curFormatParam.startsWith("height="))
        			{
        				String heightString = curFormatParam.substring("height=".length());
        				try
        				{
        					height = Integer.parseInt(heightString);
        					if (height < 0)
        						height = -1;
        				}
        				catch (NumberFormatException e)
        				{
        					logger.error("Height format "+curFormatParam+" was unparsable, defaulting to native size");
        				}
        			}
        		}
        	}
        }
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String retrieveKey = reqPath.getComponent(1);
		String photoGUID = Base64.decode(reqPath.getComponent(2));
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(retrieveKey);
        	Image [] faceImages = library.getImagesForFace(retrieveFace);
        	Image retrieveImage = null;
        	for (Image curImage:faceImages)
        	{
        		if (curImage.getGUID().equals(photoGUID))
        		{
        			retrieveImage = curImage;
        			break;
        		}
        	}
        	if (retrieveImage != null)
        	{
        		String fileType = retrieveImage.getThumbMIMEType();
        		InputStream imageStream = library.getStreamForResizedImage(retrieveImage, height, width);
        		resp.setContentType("image/"+fileType);
        		logger.error(new ErrorLogMessage("getimageforface called for {0}, type={1}", new Object[]{retrieveKey, fileType}));
        		if (imageStream != null)
        		{
        			OutputStream outStream = resp.getOutputStream();
        			byte [] outBuffer = new byte[resp.getBufferSize()];
        			int bytesRead;
        			while ((bytesRead = imageStream.read(outBuffer)) > 0)
        				outStream.write(outBuffer, 0, bytesRead);
        		}
        		imageStream.close();
        	}
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } 
        return;
    }
    
    public void getThumbForFace(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
    	String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String retrieveKey = reqPath.getComponent(1);
		String photoGUID = Base64.decode(reqPath.getComponent(2));
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Face retrieveFace = library.getFaceByKey(retrieveKey);
        	Image [] faceImages = library.getImagesForFace(retrieveFace);
        	Image retrieveImage = null;
        	for (Image curImage:faceImages)
        	{
        		if (curImage.getGUID().equals(photoGUID))
        		{
        			retrieveImage = curImage;
        			break;
        		}
        	}
        	if (retrieveImage != null)
        	{
        		String fileType = retrieveImage.getThumbMIMEType();
            	InputStream thumbStream = library.getStreamForImageThumb(retrieveImage);
            	resp.setContentType("image/"+fileType);
            	logger.error(new ErrorLogMessage("getthumb called for {0}, type={1}", new Object[]{retrieveKey, fileType}));
            	if (thumbStream != null)
            	{
                    OutputStream outStream = resp.getOutputStream();
                    byte [] outBuffer = new byte[resp.getBufferSize()];
                    int bytesRead;
                    while ((bytesRead = thumbStream.read(outBuffer)) > 0)
                        outStream.write(outBuffer, 0, bytesRead);
                }
            	thumbStream.close();
        	}
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } 
        return;
    }
    public void getImage(HttpServletRequest req, HttpServletResponse resp, Map<String, String []>paramMap)
    throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        String []formatParamsValues=paramMap.get("format");
        int height=-1, width=-1;	// height/width to output at.  -1 indicates no preference
        if (formatParamsValues != null)
        {
        	for (String curFormatParams:formatParamsValues)
        	{
        		StringTokenizer tokenizer = new StringTokenizer(curFormatParams, ",");
        		while (tokenizer.hasMoreTokens())
        		{
        			String curFormatParam = tokenizer.nextToken();
        			if (curFormatParam.startsWith("width="))
        			{
        				String widthString = curFormatParam.substring("width=".length());
        				try
        				{
        					width = Integer.parseInt(widthString);
        					if (width < 0)
        						width = -1;
        				}
        				catch (NumberFormatException e)
        				{
        					logger.error("Width format "+curFormatParam+" was unparsable, defaulting to native size");
        				}
        			}
        			if (curFormatParam.startsWith("height="))
        			{
        				String heightString = curFormatParam.substring("height=".length());
        				try
        				{
        					height = Integer.parseInt(heightString);
        					if (height < 0)
        						height = -1;
        				}
        				catch (NumberFormatException e)
        				{
        					logger.error("Height format "+curFormatParam+" was unparsable, defaulting to native size");
        				}
        			}
        		}
        	}
        }
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String albumToRetrieveFromGUID = Base64.decode(reqPath.getComponent(1));
		String retrieveKey = reqPath.getComponent(2);
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	Album albumToRetrieveFrom = library.getAlbum(albumToRetrieveFromGUID);
        	if (albumToRetrieveFrom == null)
        		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
        	Image retrieveImage = albumToRetrieveFrom.getImageForKey(retrieveKey);
        	String fileType = retrieveImage.getImageMIMEType();
        	InputStream imageStream = library.getStreamForResizedImage(retrieveImage, height, width);
        	resp.setContentType("image/"+fileType);
        	if (imageStream != null)
        	{
                OutputStream outStream = resp.getOutputStream();
                byte [] outBuffer = new byte[resp.getBufferSize()];
                int bytesRead;
                while ((bytesRead = imageStream.read(outBuffer)) > 0)
                    outStream.write(outBuffer, 0, bytesRead);
            }
        	imageStream.close();
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } 
        return;
    }
}
