package com.sitesolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import com.db.Dbmanager;
import com.thread.URLQueue;



/**
输入一个新浪网的网页URL，获得其中包含的其他新浪网网页和下载文件URL
*/
public class SinaSolver extends SiteSolver {
	
	private String sqlText = "";	//sql语句
	private String videoUrl = "";	//视频播放地址
	private String vTitle = "";		//视频标题
	private String vTag = "";		//视频标签
	private String vCommentContent = "";	//评论内容
	private int[] count = new int[3];		//点击率\评论数、收藏
	private String vDatatime = "";			//上传日期
	private String vUploadUserName = "";	//上传作者
	private Dbmanager dbmanager;
	private StringBuffer contentBuffer = null;
	
	public SinaSolver(URL url,URLQueue pageQueue,URLQueue fileQueue) {
		super(url, pageQueue, fileQueue);
		contentBuffer = super.getContent(m_url,"UTF-8");
		dbmanager = new Dbmanager();
	}
	
	//是否为有效的新浪URL
	public boolean isTrueUrl(URL url)
	{
		boolean flag = false;
		String urlStr = url.toString().toLowerCase();
		if(urlStr.contains("news.sina.com.cn") || urlStr.contains("video.sina.com.cn"))
			flag = true;
		return flag;
	}
	
	public void analyze()
	{
		//检验是否为优酷新闻URL
		if (!isTrueUrl(m_url))
			return;
		
		//debug
		System.out.println("SinaSolver start working...");
		if(contentBuffer == null)
		{
			System.out.println("SinaSolve end work, can not get the web content.");
			return;
		}
		
		if(super.getVideoTitle(contentBuffer, "<title>.*?</title>").contains("访问页面不存在"))
		{
			System.out.println("SinaSolve end work, the page doesn't exist.");
			return;
		}
		
		//如果网页为视频网页，分析之，退出程序
		if (analyzeFileUrl(m_url))
		{
			//debug
			System.out.println("SinaSolve end work, with input URL a file URL.");
			System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
			return;
		}
		
		//否则，在网页中查找视频网页链接，加入page队列"http://video.sina.com.cn/p/news/\\S+/v/\\S+/\\S+.html"
		Vector<URL> videoUrls = super.getMatchedUrls(contentBuffer,
				"","http://video.sina.com.cn/p/news/\\S+/v/\\S+/\\S+.html","");
		for (int i=0; i < videoUrls.size(); ++i)
			m_page_que.put(videoUrls.get(i));
			
		//debug
		System.out.println("SinaSolver end work, with input URL a page URL.");
		System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
		
	}
	
	public boolean analyzeFileUrl(URL fileUrl)
	{
		
		//判断是否为视频播放URL
		if (!super.checkUrl(fileUrl,"http://video.sina.com.cn/p/news/\\S+/v/\\S+/\\S+.html"))
			return false;
		
		//点击率、评论数
		count = getVideoClickRateOrComments(contentBuffer, "vid :'(.*?)'", "newsid:'(.*?)'");
		//将视频点击率、评论数记录插入videoInfUpdate表
		String sqlVideoInfUpdate = "insert into videoInfUpdate(vUrl,vClickRate,vComments,vUpdateDate) values('" 
			+ fileUrl.toString() + "'," + count[0] + "," + count[1]  + ",'" + super.getSystemTime() + "')";
		
		String checkSqlStr = "select vDownFlag,vCommentContent from videoInf where vUrl = '" + fileUrl.toString() + "'";
		ArrayList list = dbmanager.executeQuery(checkSqlStr);
		HashMap videoInfSet = new HashMap();
		if(list.size() != 1)
		{
			//将信息插入数据库videoInf表:所以说这里除了问题,需要修改getVideoInf函数
			sqlText = getVideoInf(fileUrl,contentBuffer);
			if(sqlText != ""){
				//该视频地址插入m_file_que
				m_file_que.put(fileUrl);
				dbmanager.executeUpdate(sqlText);
				dbmanager.executeUpdate(sqlVideoInfUpdate);
			}
		}	
		else if(list.size() == 1 ){
			//如果记录已经存在，并且没有被下载则插入下载队列
			videoInfSet = (HashMap)list.get(0);
			if(videoInfSet.get("vDownFlag").toString().equals("0")){
				m_file_que.put(fileUrl);
			}
			
			//增量更新评论内容
			String videoCommentStr = videoInfSet.get("vCommentContent").toString();
			//获取之前的评论数
			String sqlCommentCount = "SELECT vComments from videoinfupdate where vUrl='"+ fileUrl.toString() +"' ORDER BY vUpdateDate DESC";
			list.clear();
			list = dbmanager.executeQuery(sqlCommentCount);
			videoInfSet.clear();
			videoInfSet = (HashMap)list.get(0);
			int CommentCount = Integer.parseInt(videoInfSet.get("vComments").toString());
			int minus = count[1] - CommentCount;
			if(minus > 0){
				//评论数有增加
				videoCommentStr = getCommentContent(contentBuffer, "text\": \"(.*?)\"",videoCommentStr,minus);
				String sqlUpdateCommentContent = "update videoInf set vCommentContent ='" + videoCommentStr +"' where vUrl='" + fileUrl.toString() + "'";
				dbmanager.executeUpdate(sqlUpdateCommentContent);
			}
			
			System.out.println("该记录已经存在,更新点击率和评论数");
			dbmanager.executeUpdate(sqlVideoInfUpdate);
			return true;
		}
		
		//读取网页内容，将其他视频播放地址插入m_page_que
		Vector<URL> sub2Urls = super.getMatchedUrls(contentBuffer,
				"","http://video.sina.com.cn/p/news/\\S+/v/\\S+/\\S+.html","");
		for(int i=0;i < sub2Urls.size();i++)
		{
			m_page_que.put(sub2Urls.get(i));
		}
		
		//返回真
		return true;
	}
	
	//获得视频上传日期
	public String getVideoDate(StringBuffer contentString,String regex)
	{
		Date date = new Date();
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddhhmmss");
        SimpleDateFormat dn = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");    
		String dateStr = getContent(contentString, regex);
		if(dateStr == null)
        	return dn.format(date);
		dateStr = getVideoTitle(new StringBuffer(dateStr), "[0-9]");
        if(dateStr.equals(""))
        	return dn.format(date);
		try {
			date = df.parse(dateStr+"00");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   
		return dn.format(date);
	}
	
	public int[] getVideoClickRateOrComments(StringBuffer contentBuffer,String regex1,String regex2)
	{
		int num[] = {0,0,0};
		//点击量
		String vid = getContent(contentBuffer, regex1);
		String clickRateStr = "http://count.kandian.com/getCount.php?vids="+ vid +"-"+ vid +"&action=flash";
		try {
			URL clickRateUrl = new URL(clickRateStr);
			String clickRateCount = getContent(getContent(clickRateUrl, "UTF-8"),":\"(.*?)\"");
			if(clickRateCount != null)
				num[0] = Integer.parseInt(clickRateCount.replaceAll(",", ""));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//评论数
		String newsid = getContent(contentBuffer, regex2);
		String commentStr = "http://comment5.news.sina.com.cn/cmnt/info_wb?channel=sh&newsid="+newsid+"&callback=";
		try {
			URL commentUrl = new URL(commentStr);
			String commentCount = getContent(getContent(commentUrl, "UTF-8"),"\"total_number\":(.*?),");
			if(commentCount != null)
				num[1] = Integer.parseInt(commentCount.replaceAll(",", "").trim());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return num;
	}
	
	
	public String getCommentContent(StringBuffer contentBuffer,String regex,String content,int minus)
	{
		int countFlag = 1;//评论计数
		
		String result = "";
		String channel = getContent(contentBuffer, "channel:\'(.*?)\'");
		String newsid = getContent(contentBuffer, "newsid:\'(.*?)\'");
		System.out.println("channel:" + channel + "|newsid:" + newsid);
		try {
			for(int i = 1;i<=10;i++)
			{
				String vpcommentUrlStr = "http://comment5.news.sina.com.cn/cmnt/info_wb?channel=" +
						channel + "&newsid=" + newsid + "&page=" + i + "&callback=";
				URL vpcommentUrl = new URL(vpcommentUrlStr);
				StringBuffer vpcommentBuffer = getContent(vpcommentUrl);
				if(vpcommentBuffer == null)
					return content+result;
				String total_number = null;
				total_number = getContent(vpcommentBuffer, "total_number\":\\s(.*?),");
				if(total_number == null || total_number.trim().equals("0"))
					break;
				
				Pattern pp = Pattern.compile(regex);
				Matcher mm = pp.matcher(vpcommentBuffer.toString().replaceAll("\\\\\"","\""));
				while (mm.find()) 
				{
					countFlag++;
					
					String temp = mm.group(1).replaceAll("\\\\u","%u").replaceAll("\\\\/", "");
					temp = unescape(temp).replaceAll("<.*?>", "").replaceAll("[\\pP]|[a-z]|[A-Z]|[\\pN]|[\\pS]", " ");
					temp = temp.replaceAll("\\s+", " ");
					if(!temp.equals("") && !result.contains(temp) && !content.contains(temp) && temp.length() > 6)
						result += temp + "#";
					
					if(minus != -1 && countFlag > minus)
						break;
				}

				if(minus != -1 && countFlag > minus)
					break;
				
				if(result.length() > 16777214/2){
					result = result.substring(0,16777214/2);
					break;
				}
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(minus == -1)
			return result;
		else {
			return content + "||" + result;
		}
	}
	
	public String getVideoInf(URL url1,StringBuffer contentBuffer)
	{
		
		videoUrl = url1.toString();	//视频播放地址
		String sqlText = "";
		//视频主题
		vTitle = getVideoTitle(contentBuffer, "<title>.*?</title>").replaceAll("[\\pP]|[a-z]|[A-Z]|[0-9]|[：]|[～]|[~]", "").replaceAll("\\|", " ");
		//上传日期
		vDatatime = getVideoDate(contentBuffer, "</a>&nbsp;&nbsp;(.*?)&nbsp");
		//标签
		vTag = getVideoTag(contentBuffer, "<p class=\"tags\">(.*?)<p>");
		if(vTag.equals("") || vTitle.equals(""))
			return sqlText;
		
		vCommentContent = getCommentContent(contentBuffer, "text\": \"(.*?)\"","",-1);
		//上传用户
		vUploadUserName = getUploadUsers(contentBuffer, "<span id=\"videoChannel\">(.*?)</span>");
		sqlText = "insert into videoInf(vUrl,vTitle,vTitleResult,vTag,vTagResult,vCommentContent,vCommentContentResult,vDatatime,vUploadUser,vDownFlag,vComeFrom,vClass,vHot) values('" 
			+ videoUrl + "','" + vTitle + "','','" + vTag + "','','" + vCommentContent 
			+"','','"+ vDatatime +"','"+ vUploadUserName +"',0,'新浪','',0)";
		return sqlText;
	}
	
	
	
	//获得上传用户
	public String getUploadUsers(StringBuffer contentBuffer,String regex)
	{
		String userName = "匿名";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(contentBuffer);
		while(!matcher.find())
		{
			return userName;
		}
		userName = matcher.group(1).replaceAll("<.*?>", "");
		
		return userName;
	}
	
	//获得视频标签: 张博更新 已经无法获取新浪的tag
	public String getVideoTag(StringBuffer contentString,String regex)
	{
		String temp = "";
		temp = getContent(contentString, regex);
		if(temp == null)
			return "";
		temp = temp.trim();
		Pattern pattern = Pattern.compile("<a.*?>.*?</a>");
		Matcher matcher = pattern.matcher(temp);
		List<String> list = new ArrayList<String>();  
		while(matcher.find())
		{
			list.add(matcher.group());
		}
		
		String tag = "";
		if(list.size() == 0)
			return tag;
		for(int i=0;i<list.size();i++)
			tag += list.get(i).replaceAll("<.*?>", "").replaceAll("\\pP", "")+"#";
		
		return tag.replaceAll("[a-z]|[A-Z]|[0-9]|[：]|[～]|[~]", "");
		
	}
	
}
