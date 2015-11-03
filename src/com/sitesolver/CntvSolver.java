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


public class CntvSolver extends SiteSolver {
	
	private String sqlText = "";	//sql语句
	private String videoUrl = "";	//视频播放地址
	private String vTitle = "";		//视频标题
	private String vTag = "";		//视频标签
	private String vCommentContent = "";	//评论内容
	private int[] count = new int[3];			//点击率\评论数、收藏
	private String vDatatime = "";	//上传日期
	private String vUploadUserName = "";//上传作者
	private Dbmanager dbmanager;
	private StringBuffer contentBuffer = null;
	
	public CntvSolver(URL url,URLQueue pageQueue,URLQueue fileQueue) {
		super(url, pageQueue, fileQueue);
		contentBuffer = super.getContent(m_url,"UTF-8");
		dbmanager = new Dbmanager();
	}
	
	//是否为有效的CNTV的URL
	public boolean isTrueUrl(URL url)
	{
		boolean flag = false;
		String urlStr = url.toString().toLowerCase();
		if(urlStr.contains("xiyou.cntv.cn/video/index-new-2") || urlStr.contains("xiyou.cntv.cn/v-"))
			flag = true;
		return flag;
	}
	
	
	public void analyze()
	{
		//检验是否为cntv新闻URL
		if (!isTrueUrl(m_url))
			return;
		
		//debug
		System.out.println("CntvSolver start working...");
		if(contentBuffer == null)
		{
			System.out.println("CntvSolver end work, can not get the web content.");
			return;
		}
		//如果网页为视频网页，分析之，退出程序
		if (analyzeFileUrl(m_url))
		{
			//debug
			System.out.println("CntvSolver end work, with input URL a file URL.");
			System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
			return;
		}
		
		//网页不是视频播放网页，获取普通URL
		Vector<URL> pageUrls = super.getMatchedUrls(contentBuffer, 
				"http://xiyou.cntv.cn", "/video/index-new-2\\S+.html", "");
		for(int i=0;i<pageUrls.size();i++)
			m_page_que.put(pageUrls.get(i));
		
		//否则，在网页中查找视频网页链接，加入page队列
		Vector<URL> videoUrls = super.getMatchedUrls(contentBuffer,
				"http://xiyou.cntv.cn","/v-\\S+.html","");
		for (int i=0; i < videoUrls.size(); ++i)
			m_page_que.put(videoUrls.get(i));
			
		//debug
		System.out.println("YouKuSolver end work, with input URL a page URL.");
		System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
	}
	
	
	public boolean analyzeFileUrl(URL fileUrl)
	{
		
		//判断是否为视频播放URL
		if (!super.checkUrl(fileUrl,"http://xiyou.cntv.cn/v-\\S+.html"))
			return false;
		
		//将视频点击率、评论数记录插入videoInfUpdate表
		count = getVideoClickRateOrComments(contentBuffer,"<div id=\"short_1\" class=\"shortinfo\">(.*?)</div>",
				"<span class=\"vnum\">(.*?)</span>");
		String sqlVideoInfUpdate = "insert into videoInfUpdate(vUrl,vClickRate,vComments,vUpdateDate) values('" 
			+ fileUrl.toString() + "'," + count[0] + "," + count[1]  + ",'" + super.getSystemTime() + "')";
		
		String checkSqlStr = "select vDownFlag,vCommentContent from videoInf where vUrl = '" + fileUrl.toString() + "'";
		ArrayList list = dbmanager.executeQuery(checkSqlStr);
		HashMap videoInfSet = new HashMap();
		if(list.size() != 1 )
		{
			//将信息插入数据库videoInf表
			sqlText = getVideoInf(fileUrl,contentBuffer);
			System.out.println("videoInf:" + sqlText);
			if(sqlText != ""){
				m_file_que.put(fileUrl);
				dbmanager.executeUpdate(sqlText);
				dbmanager.executeUpdate(sqlVideoInfUpdate);
			}
		}	
		else if(list.size() == 1){
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
				videoCommentStr = getCommentContent(fileUrl.toString(), "content\":\"(.*?)\"",videoCommentStr,minus);
				String sqlUpdateCommentContent = "update videoInf set vCommentContent ='" + videoCommentStr +"' where vUrl='" + fileUrl.toString() + "'";
				dbmanager.executeUpdate(sqlUpdateCommentContent);
			}
			
			
			System.out.println("该记录已经存在,更新点击率和评论数");
			dbmanager.executeUpdate(sqlVideoInfUpdate);
			return true;
		}
		
		//读取网页内容，将其他视频播放地址插入m_page_que
		String content = super.getContent(contentBuffer, "<div class=\"videos clearfix\">(.*?)</div>");
		Vector<URL> sub2Urls = super.getMatchedUrls(new StringBuffer(content),
				"http://xiyou.cntv.cn","/v-\\S+.html","");
		for(int i=0;i < sub2Urls.size();i++){
			m_page_que.put(sub2Urls.get(i));
		}
		
		//返回真
		return true;
	}
	
	
	
	//获得视频上传日期
	public String getVideoDate(StringBuffer contentString,String patternString)
	{
		String dateStr = "";
		Pattern pattern = Pattern.compile(patternString);
		Matcher matcher = pattern.matcher(contentString);
		while(matcher.find())
		{
			dateStr = matcher.group(1);
		}
		
		return dateStr.trim();
	}
	
	//获得视频标签
	public String getVideoTag(StringBuffer contentString,String patternString)
	{
		String tag = "";
		List<String> list = new ArrayList<String>();  
		Pattern pattern = Pattern.compile(patternString,Pattern.CANON_EQ);
		Matcher matcher = pattern.matcher(contentString);
		while(matcher.find())
		{
			String temp = matcher.group();
			temp = temp.replaceAll("<.*?>", "");
			list.add(temp);
		}
		if(list.size() == 0)
			return tag;
		else {
			for(int i=0;i<list.size()-1;i++)
				tag += list.get(i).replaceAll("\\pP", "") + "#";
			tag = tag + list.get(list.size()-1).replaceAll("\\pP", "");
		}
		return tag.replaceAll("[a-z]|[A-Z]|[0-9]|[：]|[～]|[~]", "");
		
	}
	
	//获得视频点击率、评论数
	public int[] getVideoClickRateOrComments(StringBuffer contentBuffer,String patternString,String regex2)
	{
		int num[] = {0,0,0};
		
        //页面源码
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(contentBuffer);
        if(!matcher.find())
        {
            return null;
        }
        //获取播放数、评论数、收藏数
        int i=0;
        String containStr = matcher.group(1);
        Pattern pattern2 = Pattern.compile(regex2);
        Matcher matcher2 = pattern2.matcher(containStr);
        while(matcher2.find())
        {
        	num[i] = Integer.parseInt(matcher2.group(1).replace(",",""));
        	i++;
        	
        }
		return num;
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
	
	public String getCommentContent(String videoUrlString,String regex,String content,int minus)
	{
		int countFlag = 1;//评论计数
		
		String result = "";
		try {
			for(int i = 1;i<=10;i++)
			{
				int begin = videoUrlString.lastIndexOf("/")+3;
				int end = videoUrlString.lastIndexOf(".");
				String videoId = videoUrlString.substring(begin,end);
				String vpcommentUrlStr = "http://comment.cntv.cn/comment/getjson/varname/json?channel=xiyou&itemid=video_" +
							videoId + "&page=" + i;
				URL vpcommentUrl = new URL(vpcommentUrlStr);
				StringBuffer vpcommentBuffer = getContent(vpcommentUrl);
				if(vpcommentBuffer == null)
					return content+result;
				String ret = getContent(vpcommentBuffer, "ret\":\"(.*?)\"");
				//该页面没有评论内容
				if(ret == null || ret.trim().equals("fail")  )
					break;
				
				Pattern pp = Pattern.compile(regex);
				Matcher mm = pp.matcher(vpcommentBuffer.toString().replaceAll("\\\\\"","\""));
				while (mm.find()) 
				{
					countFlag++;
					
					String temp = mm.group(1).replaceAll("\\\\u","%u").replaceAll("\\\\/", "");
					temp = unescape(temp).replaceAll("&nbsp;", "").replaceAll("<.*?>", "").replaceAll("[\\pP]|[a-z]|[A-Z]|[\\pN]|[\\pS]", " ");
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
	
	public String getVideoInf(URL url1,StringBuffer contentString)
	{
		
		videoUrl = url1.toString();	//视频播放地址
		String sqlText = "";
		//视频主题
		vTitle = getVideoTitle(contentString, "<title>.*?</title>");
		if(vTitle.length() > 14)//截取前面字段
			vTitle = vTitle.substring(0, vTitle.length()-14);
		vTitle = vTitle.replaceAll("[\\pP]|[a-z]|[A-Z]|[0-9]|[：]|[\\|]|[～]|[~]", "");
		//上传日期
		vDatatime = getVideoDate(contentString, "<span class=\"fsize11\">(.*?)</span>");
		//标签
		vTag = getVideoTag(contentString, "<b class=\"p5\">.*?</b>");
		if(vTag.equals("") || vTitle.equals(""))
			return sqlText;
		vCommentContent = getCommentContent(videoUrl, "content\":\"(.*?)\"","",-1);
		//上传用户
		vUploadUserName = getUploadUsers(contentString, "<h3 class=\"pb5\"><a.*?>(.*?)</a></h3>");
		sqlText = "insert into videoInf(vUrl,vTitle,vTitleResult,vTag,vTagResult,vCommentContent,vCommentContentResult,vDatatime,vUploadUser,vDownFlag,vComeFrom,vClass,vHot) values('" 
			+ videoUrl + "','" + vTitle + "','','" + vTag + "','','" + vCommentContent 
			+"','','"+ vDatatime +"','"+ vUploadUserName +"',0,'CNTV','',0)";
		
		return sqlText;
	}
	
}