package com.sitesolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.db.Dbmanager;
import com.thread.URLQueue;


public class WangyiSolver extends SiteSolver{

	private String sqlText = "";	//sql语句
	private String videoUrl = "";	//视频播放地址
	private String vTitle = "";		//视频标题
	private String vTag = "";		//视频标签
	private String vCommentContent = "";	//评论内容
	private int[] count = {0,0,0};		//点击率\评论数、收藏
	private String vDatatime = "";			//上传日期
	private String vUploadUserName = "";	//上传作者
	private Dbmanager dbmanager;
	private StringBuffer contentBuffer = null;
	public WangyiSolver(URL url,URLQueue pageQueue,URLQueue fileQueue){
		super(url, pageQueue, fileQueue);
		contentBuffer = super.getContent(m_url,"GBK");
		dbmanager = new Dbmanager();
	}
	
	//是否为有效的网易URL
	public boolean isTrueUrl(URL url)
	{
		boolean flag = false;
		String urlStr = url.toString().toLowerCase();
		if(urlStr.contains("v.163.com"))
			flag = true;
		return flag;
	}
	
	public void analyze()
	{
		//检验是否为网易资讯URL
		if (!isTrueUrl(m_url))
			return;
		
		//debug
		System.out.println("WangyiSolver start working...");
		
		if(contentBuffer == null)
		{
			System.out.println("WangyiSolver end work, can not get the web content.");
			return;
		}
		//如果网页为视频网页，分析之，退出程序
		if (analyzeFileUrl(m_url))
		{
			//debug
			System.out.println("WangyiSolve end work, with input URL a file URL.");
			System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
			return;
		}
			
		//网页不是视频播放网页，获取普通URL
		if(m_url.toString().equals("http://v.163.com/zixun/V7M3CBCH5/1/rank_1.html"))
		{
			for(int i=2;i < 6;i++)
			{
				try {
					URL url = new URL("http://v.163.com/zixun/V7M3CBCH5/1/rank_"+ i +".html");
					m_page_que.put(url);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		Vector<URL> pageUrls = super.getMatchedUrls(contentBuffer, "", 
				"http://v.163.com/zixun/\\S+.html", "");
		for(int i=0;i<pageUrls.size();i++)
			m_page_que.put(pageUrls.get(i));
		
		
		//debug
		System.out.println("WangYiSolver end work, with input URL a page URL.");
		System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
	}
	
	public boolean analyzeFileUrl(URL fileUrl)
	{
		//判断是否为视频播放URL
		if (super.checkUrl(fileUrl,"http://v.163.com/zixun/\\S+/rank_\\S+.html"))
			return false;
		
		//将信息插入数据库videoInf表
		sqlText = getVideoInf(fileUrl,contentBuffer);
		//将视频点击率、评论数记录插入videoInfUpdate表
		String sqlVideoInfUpdate = "insert into videoInfUpdate(vUrl,vClickRate,vComments,vUpdateDate) values('" 
			+ fileUrl.toString() + "'," + count[0] + "," + count[1]  + ",'" + super.getSystemTime() + "')";
		
		
		String checkSqlStr = "select vDownFlag,vCommentContent from videoInf where vUrl = '" + fileUrl.toString() + "'";
		ArrayList list = dbmanager.executeQuery(checkSqlStr);
		HashMap videoInfSet = new HashMap();
		if(list.size() != 1 && sqlText != "")
		{
			//该视频地址插入m_file_que
			m_file_que.put(fileUrl);
			dbmanager.executeUpdate(sqlText);
			dbmanager.executeUpdate(sqlVideoInfUpdate);
		}	
		else if(list.size() == 1){
			//如果记录已经存在，并且没有被下载则插入下载队列
			videoInfSet = (HashMap)list.get(0);
			if(videoInfSet.get("vDownFlag").toString().equals("0")){
				m_file_que.put(fileUrl);
				System.out.println("重新插入队列");
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
			System.out.println("minus-----------------|"+minus);
			if(minus > 0){
				//评论数有增加
				videoCommentStr = getCommentContent(contentBuffer, "threadCountPath=(.*?).js","b\":\"(.*?)\"",videoCommentStr,minus);
				String sqlUpdateCommentContent = "update videoInf set vCommentContent ='" + videoCommentStr +"' where vUrl='" + fileUrl.toString() + "'";
				dbmanager.executeUpdate(sqlUpdateCommentContent);
			}
			
			System.out.println("该记录已经存在,更新点击率和评论数");
			dbmanager.executeUpdate(sqlVideoInfUpdate);
			return true;
		}
		//返回真
		return true;
	}
	
	public String getVideoInf(URL url1,StringBuffer contentString)
	{
		
		videoUrl = url1.toString();	//视频播放地址		
		String sqlText = "";
		//视频主题
		vTitle = getVideoTitle(contentString, "<title>.*?</title>");
		//截取前面字段
		if(vTitle.length() > 8)
			vTitle = vTitle.substring(0, vTitle.length()-8);
		vTitle = vTitle.replaceAll("[\\pP]|[a-z]|[A-Z]|[0-9]|[：]|[\\|]|[~]|[～]", "");
		
		//上传日期
		vDatatime = getVideoDate(contentString, "<span id=\"pub_time\">(.*?)</span>");
		//标签
		vTag = getVideoTag(contentString, "<a class=\"cBlue\" href.*?>.*?</a>");
		if(vTag.equals("") || vTitle.equals(""))
			return sqlText;
		vCommentContent = getCommentContent(contentString, "threadCountPath=(.*?).js","b\":\"(.*?)\"","",-1);
		//点击率、评论数
		//无点击率
		//上传用户
		vUploadUserName = getUploadUsers(contentString, "<span id=\"source\">(.*?)</span>");
		sqlText = "insert into videoInf(vUrl,vTitle,vTitleResult,vTag,vTagResult,vCommentContent,vCommentContentResult,vDatatime,vUploadUser,vDownFlag,vComeFrom,vClass,vHot) values('" 
			+ videoUrl + "','" + vTitle + "','','" + vTag + "','','" + vCommentContent 
			+"','','"+ vDatatime +"','"+ vUploadUserName +"',0,'网易','',0)";
		return sqlText;
	}
	
	//得到评论内容
	public String getCommentContent(StringBuffer contentBuffer,String regex1,String regex2,String content,int minus)
	{
		int countFlag = 1;//评论计数
		
		String result = "";
		String commentId = getContent(contentBuffer, regex1);
		int lastIndex = commentId.lastIndexOf("/");
		String commentUrlStr = commentId.substring(0, lastIndex+1) + "v" + commentId.substring(lastIndex+1) + ".html";
		try {
			StringBuffer commentContent = getContent(new URL(commentUrlStr), "GBK");
			if(commentContent == null || commentContent.toString().equals(""))
				return content;
			String commentCountStr = getContent(commentContent, "hotVotePostCount\":(.*?),");
			count[1] = Integer.parseInt(commentCountStr);
			Pattern pattern = Pattern.compile(regex2);
			Matcher matcher = pattern.matcher(commentContent);
			while(matcher.find()){
				countFlag++;
				String temp = matcher.group(1).replaceAll("<.*?>", "").replaceAll("[\\pP]|[a-z]|[A-Z]|[\\pN]|[\\pS]", " ");
				temp = temp.replaceAll("\\s+", " ");
				if(temp != "" && !result.contains(temp) && !content.contains(temp) && temp.length() > 6){
					result += temp + "#";
				}
				
				if(minus != -1 && countFlag > minus)
					break;
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(minus == -1)
			return result;
		else if(minus != -1 && result != ""){
			return content + "||" + result;
		}
		else {
			return content;
		}
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
		userName = matcher.group(1);
		
		return userName;
	}
	
	//获得上传视频日期
	public String getVideoDate(StringBuffer contentString,String regex)
	{
		String datetime = getVideoTitle(contentString, regex);
		if(datetime == null)
			return null;
		return datetime;
	}
	
	//获得视频标签
	public String getVideoTag(StringBuffer contentString,String regex)
	{
		String tag = "";  
		List<String> list = new ArrayList<String>();  
		Pattern pattern = Pattern.compile(regex,Pattern.CANON_EQ);
		Matcher matcher = pattern.matcher(contentString);
		while(matcher.find())
		{
			list.add(matcher.group());
		}
		
		if(list.size() == 0)
			return tag;
		for(int i=0;i<list.size()-1;i++)
			tag += list.get(i).replaceAll("<.*?>", "").replaceAll("\\pP", "") + "#";
		tag = tag+list.get(list.size()-1).replaceAll("<.*?>", "").replaceAll("\\pP", "");
		
		return tag.replaceAll("[a-z]|[A-Z]|[0-9]|[：]|[\\|]|[\\pP]|[~]|[～]", "");
	}
}
