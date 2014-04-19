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

import java.util.Date;

public class CFAbsoluteTime extends Date
{
	public static final long kCFAbsoluteTimeEpochInJavaTime = 978307200L * 1000L;
	static long getJavaTime(double cfAbsoluteTime)
	{
		long cfTimeInMS = (long)(cfAbsoluteTime * 1000);
		long javaTime = cfTimeInMS + kCFAbsoluteTimeEpochInJavaTime;
		return javaTime;
	}
	
	public static Date getDate(double cfAbsoluteTime)
	{
		return new Date(getJavaTime(cfAbsoluteTime));
	}
	
	public CFAbsoluteTime(double cfAbsoluteTime)
	{
		super(getJavaTime(cfAbsoluteTime));
	}
}
