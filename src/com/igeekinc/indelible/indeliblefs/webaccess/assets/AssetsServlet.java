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
 
package com.igeekinc.indelible.indeliblefs.webaccess.assets;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AssetsServlet extends HttpServlet
{
	private static final long serialVersionUID = -2730268859412095578L;
	HashMap<String, byte[]>assets;
	public AssetsServlet() throws IOException
	{
		assets = new HashMap<String, byte[]>();
		String [] assetNames = {"BasicCloudFolder.png", "CloudVolume.png", "BasicCloudFile.png", "TrashCan.png", "ReturnArrow.png", 
				"jquery.galleriffic.js", "jquery-1.3.2.js", "jquery.opacityrollover.js", "basic.css", "galleriffic-2.css",
				"supersized.css", "bg-black.png", "button-tray-down.png", "button-tray-up.png", "play.png", "pause.png", 
				"jquery.easing.min.js", "supersized.3.2.7.min.js",
				"supersized.shutter.css", "supersized.shutter.min.js",
				"progress.gif", "nav-bg.png", "bg-hover.png", "back.png", "forward.png", "nav-dot.png",
				"progress-back.png", "progress-bar.png", "thumb-back.png", "thumb-forward.png"};
		for (String curAssetName:assetNames)
		{
			InputStream resourceStream = getClass().getResourceAsStream(curAssetName);
			if (resourceStream != null)
			{
				InputStream assetInputStream = new BufferedInputStream(resourceStream);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte [] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = assetInputStream.read(buffer)) > 0)
				{
					baos.write(buffer, 0, bytesRead);
				}
				baos.close();
				byte [] assetBuffer = baos.toByteArray();
				assets.put(curAssetName, assetBuffer);
			}
			else
			{
				System.out.println("Could not load resource "+curAssetName);
			}
		}
	}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        String path=req.getPathInfo();
        if (path.lastIndexOf('/') >= 0)
        {
        	String assetName = path.substring(path.lastIndexOf('/') + 1);
        	byte [] assetBuffer = assets.get(assetName);
        	if (assetBuffer != null)
        	{
        		if (assetName.endsWith(".css"))
        			resp.setContentType("text/css");
        		if (assetName.endsWith(".js"))
        			resp.setContentType("text/javascript");
        		resp.getOutputStream().write(assetBuffer);
        		return;
        	}
        }
        resp.sendError(404);
    }
}
