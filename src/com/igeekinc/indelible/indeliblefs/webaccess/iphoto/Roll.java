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
 
package com.igeekinc.indelible.indeliblefs.webaccess.iphoto;

import java.util.ArrayList;

import com.dd.plist.NSDictionary;

public class Roll
{
	public static final String kRollIDKey = "RollID";
	public static final String kProjectUUIDKey = "ProjectUuid";
	public static final String kRollNameKey = "RollName";
	public static final String kRollDateAsTimerIntervalKey = "RollDateAsTimerInterval";
	//public static final String 
	private iPhotoLibrary parent;
	private int rollID;
	private String projectUUID;
	private String rollName;
	private double rollDateAsTimerInterval;
	private String keyPhotoKey;
	private int photoCount;
	private ArrayList<String>keyList;
	
	public Roll(iPhotoLibrary parentLibrary, NSDictionary rollDictionary)
	{

	}
}
