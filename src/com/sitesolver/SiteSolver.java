package com.sitesolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.db.Dbmanager;
import com.thread.URLQueue;


	/**
	基础类，定义了网页分析器的基本操作
	本包内的所有类都应继承自此类
	*/
public class SiteSolver {
	
	protected URL m_url;
	protected StringBuffer m_content;
	protected URLQueue m_page_que;
	protected URLQueue m_file_que;
	private static Dbmanager dbmanager;

	public SiteSolver(URL url,URLQueue pageQueue,URLQueue fileQueue)
	{
		this.m_url = url;
		this.m_page_que = pageQueue;
		this.m_file_que = fileQueue;
		this.m_content = new StringBuffer();
		this.dbmanager = new Dbmanager();
	}
	
	 /** *
     * 字符串编码转换的实现方法
     * @param str    待转换的字符串
     * @param newCharset    目标编码
     */
    public String changeCharset(String str, String newCharset) throws UnsupportedEncodingException{
        if(str != null){
            //用默认字符编码解码字符串。与系统相关，中文windows默认为GB2312
            byte[] bs = str.getBytes();
            return new String(bs, newCharset);    //用新的字符编码生成字符串
        }
        return null;
    }
    
    //unicode转码成中文
    public String unescape(String src) {
		StringBuffer tmp = new StringBuffer();
		tmp.ensureCapacity(src.length());
		int lastPos = 0, pos = 0;
		char ch;
		while (lastPos < src.length()) {
			pos = src.indexOf("%", lastPos);
			if (pos == lastPos) 
			{
				//最后是一个%
				if(pos == src.length()-1)
					break;
				//将%u8fd9中后四位的字符以16进制转化成汉字
				if (src.charAt(pos + 1) == 'u') {
					//如果是%u执行以下
					ch = (char) Integer.parseInt(
							src.substring(pos + 2, pos + 6), 16);
					tmp.append(ch);
					lastPos = pos + 6;
				} else {
					tmp.append(unescape(src.substring(pos+1)));
					break;
				}
			} else {
				if (pos == -1) {
					tmp.append(src.substring(lastPos));
					lastPos = src.length();
				} else {
					tmp.append(src.substring(lastPos, pos));
					lastPos = pos;
				}
			}
		}
		return tmp.toString();
	}
	/**
	继承类应该重写该方法：分析网页内容，得到的URL放入对应向量
	要求为时间阻塞的，以保证执行完毕后可以确认分析结束
	*/
	public void analyze()
	{
		
	}
	
	
	/**
	静态方法：按给定URL和给定正则表达式，获得解析出的网页
	patternString--正则表达式
	postString--URL的前面添加
	prevString--URL的后面添加
	*/
	public static Vector<URL> getMatchedUrls(URL url,String prevString,String patternString,String postString)
	{
        return getMatchedUrls(getContent(url),prevString,patternString,postString);
	}
	
	/**
	静态方法：根据给定的URL得到网页内容
	*/
	public static StringBuffer getContent(URL url)
	{
        StringBuffer contentBuffer = new StringBuffer();
      
        int responseCode = -1;
        String sqlText = "";
        HttpURLConnection con = null;
		try {
			con = (HttpURLConnection)url.openConnection();
			con.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");//IE代理进行下载
			con.setConnectTimeout(60000);
			con.setReadTimeout(60000); 
			
			//获得网页返回信息码
			
			responseCode = con.getResponseCode();
			
			if(responseCode == -1)
				{
					System.out.println(url.toString() +" : connection is failure...");
					con.disconnect();
					return null;
				}
			
			System.out.println(url.toString() + " #get response code: " + responseCode);
			
			if (responseCode >= 400)	//请求失败
				{
					System.out.println("请求失败:get response code: "+ responseCode);
					con.disconnect();
					return null;
				}

            InputStream inStr = con.getInputStream();
			InputStreamReader istreamReader = new InputStreamReader(inStr);
			BufferedReader buffStr = new BufferedReader(istreamReader);
			
			String str = null;	
	        while((str = buffStr.readLine())!=null)
	        	contentBuffer.append(str);
	        inStr.close();
        } catch (IOException e) {
        	e.printStackTrace();
        	contentBuffer = null;
        	sqlText = getException(url,responseCode,e.getMessage());
        	dbmanager.executeUpdate(sqlText);
        	System.out.println("error: " +url.toString());
        }
        finally{
        	con.disconnect();
        }
        return contentBuffer;
        
	}
	
	public static StringBuffer getContent(URL url,String charset)
	{
        StringBuffer contentBuffer = new StringBuffer();
        HttpURLConnection con = null;
        int responseCode = -1;
        String sqlText = "";
        
		try {
			//打开链接
			con = (HttpURLConnection)url.openConnection();
			//设置代理
			con.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");//IE代理进行下载
			con.setConnectTimeout(60000);
			con.setReadTimeout(60000); 
			
			//获得网页返回信息码
			responseCode = con.getResponseCode();
			
			if(responseCode == -1)
				{
					System.out.println(url.toString() +" : connection is failure...");
					con.disconnect();
					return null;
				}
			
			System.out.println(url.toString() + " #get response code: " + responseCode);
			if (responseCode >= 400)	//请求失败
				{
					System.out.println("请求失败:get response code: "+ responseCode);
					con.disconnect();
					return null;
				}
			//获取内容
            InputStream inStr = con.getInputStream();
			InputStreamReader istreamReader = new InputStreamReader(inStr,charset);
			BufferedReader buffStr = new BufferedReader(istreamReader);
			
			String str = null;	
	        while((str = buffStr.readLine())!=null)
	        	contentBuffer.append(str);
	        inStr.close();
	        
        } catch (IOException e) {
        	e.printStackTrace();
        	contentBuffer = null;
        	sqlText = getException(url,responseCode,e.getMessage());
        	dbmanager.executeUpdate(sqlText);
        	System.out.println("error: " +url.toString());
        } finally{
        	con.disconnect();
        }
        return contentBuffer;
        
	}
	
	public static String getException(URL url, int responseCode, String message)
	{
    	Date date = new Date();
    	SimpleDateFormat dn = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); 
    	String sqlText = "insert into errorUrl(vUrl,responseCode,exception,ex_datetime) values('"
    		+ url.toString() +"',"+ responseCode +",'"+ message +"','"+dn.format(date)+"')";
    	return sqlText;
	}
	
	public String getContent(StringBuffer contenBuffer,String regex)
	{
		if(contenBuffer == null)
			return null;
		 Pattern pattern = Pattern.compile(regex);
		 Matcher m = pattern.matcher(contenBuffer);
		 if(!m.find()){
			 return null;
		 }
        
		 return m.group(1);		
	}
	
	/**
	静态方法：从给定的文本内容中，根据给定的正则表达式，获得匹配的URL
	*/
	public static Vector<URL> getMatchedUrls(StringBuffer contentBuffer,String prevString,String patternString,String postString)
	{
		//CASE_INSENSITIVE不启用大小写匹配
        Pattern pattern = Pattern.compile(patternString,Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(contentBuffer);
        
        Vector<URL> urls = new Vector<URL>();
        while (matcher.find())
        {
        	try {
        		urls.add(new URL(prevString + matcher.group().trim() + postString));
        	} catch (MalformedURLException e) {
        		e.printStackTrace();
        	}
        }
        	
        return urls;
	}
	
	/**
	静态方法：从给定的文本内容中，根据给定的正则表达式，获得匹配的URL
		       主要用于获取视频分段后的多地址
	*/
	public static Vector<URL> getMatchedUrls_Group1(StringBuffer contentBuffer,String prevString,String patternString,String postString)
	{
		//CASE_INSENSITIVE不启用大小写匹配
        Pattern pattern = Pattern.compile(patternString,Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(contentBuffer);
        
        Vector<URL> urls = new Vector<URL>();
        while (matcher.find())
        {
        	try {
        		urls.add(new URL(prevString + matcher.group(1).trim() + postString));
        	} catch (MalformedURLException e) {
        		e.printStackTrace();
        	}
        }
        	
        return urls;
	}
	
	/**
	静态方法：判断给定的URL是否满足给定的正则表达式
	*/
	public static boolean checkUrl(URL url,String patternString)
	{
		Pattern pattern = Pattern.compile(patternString,Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(url.toString().trim());
		return matcher.find();
	}
	
	//获得视频标题
	public String getVideoTitle(StringBuffer contentString,String patternString)
	{
		String title = "";  
		List<String> list = new ArrayList<String>();  
		Pattern pattern = Pattern.compile(patternString,Pattern.CANON_EQ);
		Matcher matcher = pattern.matcher(contentString);
		while(matcher.find())
		{
			list.add(matcher.group());
		}
		
		for(int i=0;i<list.size();i++)
			title += list.get(i);
		return title.replaceAll("<.*?>", "");
	}
	
	//获取系统时间
	public static String getSystemTime(){
		Date datetime = new Date();
    	SimpleDateFormat sm=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	String datetimeStr = sm.format(datetime);
		return datetimeStr;
	}
	
}
