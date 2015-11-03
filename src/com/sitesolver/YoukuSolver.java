package com.sitesolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.db.Dbmanager;
import com.thread.URLQueue;



/**
输入一个YouKu网的网页URL，获得其中包含的网页和下载文件URL
*/
public class YoukuSolver extends SiteSolver {
	
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
	
	public YoukuSolver(URL url,URLQueue pageQueue,URLQueue fileQueue) {
		super(url, pageQueue, fileQueue);
		contentBuffer = super.getContent(m_url,"UTF-8");
		dbmanager = new Dbmanager();
	}
	
	//是否为有效的优酷URL
	public boolean isTrueUrl(URL url)
	{
		boolean flag = false;
		String urlStr = url.toString().toLowerCase();
		if(urlStr.contains("news.youku.com") || urlStr.contains("v.youku.com"))
			flag = true;
		return flag;
	}
	
	
	public void analyze()
	{
		//检验是否为优酷新闻URL
		if (!isTrueUrl(m_url))
			return;
		
		//debug
		System.out.println("YoukuSolver start working...");
		
		if(contentBuffer == null)
		{
			System.out.println("YoukuSolver end work, can not get the web content.");
			return;
		}
		//如果网页为视频网页，分析之，退出程序
		if (analyzeFileUrl(m_url))
		{
			//debug
			System.out.println("YoukuSolve end work, with input URL a file URL.");
			System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
			return;
		}
		
		if(m_url.toString().equals("http://news.youku.com/"))
		{
			try {
				m_page_que.put(new URL("http://news.youku.com/focus/index"));
				m_page_que.put(new URL("http://news.youku.com/society/society"));
				m_page_que.put(new URL("http://news.youku.com/paike/index"));
				m_page_que.put(new URL("http://news.youku.com/jiankong/index"));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		
		//网页不是视频播放网页，获取普通URL
		Vector<URL> pageUrls = super.getMatchedUrls(contentBuffer, "", 
				"http://news.youku.com/\\S+.html", "");
		for(int i=0;i<pageUrls.size();i++)
			m_page_que.put(pageUrls.get(i));
		
		//否则，在网页中查找视频网页链接，加入page队列"http://v.youku.com/v_(playlist/|show/id_)\\S+.html"
		Vector<URL> videoUrls = super.getMatchedUrls(contentBuffer,
				"","http://v.youku.com/v_(playlist/|show/id_)\\S+.html","");
		for (int i=0; i < videoUrls.size(); ++i)
		{
			m_page_que.put(findUrl(videoUrls.get(i)));
		}	
		//debug
		System.out.println("YouKuSolver end work, with input URL a page URL.");
		System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
	}
	
	
	public boolean analyzeFileUrl(URL fileUrl)
	{
		
		//判断是否为v_playlist系列，转化为v_show
		if(super.checkUrl(fileUrl, "http://v.youku.com/v_playlist/\\S+.html"))
			{
				String regex = "<a charset=\"7-0\" href=\"(http://v.youku.com/v_show/id_\\S+.html)\".*?>";
				fileUrl = conversionUrl(contentBuffer,regex);
			}
		
		//判断是否为视频播放URL
		if (!super.checkUrl(fileUrl,"http://v.youku.com/v_show/id_\\S+.html"))
			return false;
		
		fileUrl = findUrl(fileUrl);
		
		//点击率和评论数
		count = getVideoClickRateOrComments(contentBuffer, "videoId\\s=\\s\'(.*?)\'", 
				"<div class=\"common\">(.*?)</div>");
		//将视频点击率、评论数记录插入videoInfUpdate表
		String sqlVideoInfUpdate = "insert into videoInfUpdate(vUrl,vClickRate,vComments,vUpdateDate) values('" 
			+ fileUrl.toString() + "'," + count[0] + "," + count[1]  + ",'" + super.getSystemTime() + "')";
		
		String checkSqlStr = "select vDownFlag,vCommentContent from videoInf where vUrl = '" + fileUrl.toString() + "'";
		ArrayList list = dbmanager.executeQuery(checkSqlStr);
		HashMap videoInfSet = new HashMap();
		if(list.size() != 1)
		{
			//将信息插入数据库videoInf表
			sqlText = getVideoInf(fileUrl,contentBuffer);
			System.out.println("videoInf:" + sqlText);
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
			System.out.println("minus:-------------||"+minus);
			if(minus > 0){
				//评论数有增加
				videoCommentStr = getCommentContent(contentBuffer, "<p id=\\\"content_.*?>(.*?)<",videoCommentStr,minus);
				String sqlUpdateCommentContent = "update videoInf set vCommentContent ='" + videoCommentStr +"' where vUrl='" + fileUrl.toString() + "'";
				dbmanager.executeUpdate(sqlUpdateCommentContent);
				System.out.println("sqlUpdateCommentContent:" + sqlUpdateCommentContent);
			}
			
			System.out.println("该记录已经存在,更新点击率和评论数");
			dbmanager.executeUpdate(sqlVideoInfUpdate);
			System.out.println("videoInfUpdate:" + sqlVideoInfUpdate);
			return true;
		}
		
		
		//读取网页内容，将其他视频播放地址插入m_page_que
		Vector<URL> sub2Urls = super.getMatchedUrls(contentBuffer,
				"","http://v.youku.com/v_(playlist/|show/id_)\\S+.html","");
		for(int i=0;i < sub2Urls.size();i++)
		{
			m_page_que.put(findUrl(sub2Urls.get(i)));
		}
		
		//返回真
		return true;
	}
	
	//截取http://v.youku.com/v_show/id_\\S+.html&rcontent=http://v.youku.com/v_show/id_\\S+.html
	public URL findUrl(URL fileUrl)
	{
		int endIndex = fileUrl.toString().indexOf('&');
		if(endIndex != -1)
		{
			try {
				fileUrl = new URL(fileUrl.toString().substring(0, endIndex));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return fileUrl;
	}
	
	//将http://v.youku.com/v_playlist/\\S+.html地址转化为http://v.youku.com/v_show/id_\\S+.html
	public URL conversionUrl(StringBuffer contentBuffer,String regex)
	{
		Pattern pattern = Pattern.compile(regex,Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(contentBuffer);
		
		Vector<URL> urls = new Vector<URL>();
        while (matcher.find())
        {
        	try {
        		urls.add(new URL(matcher.group(1)));
        	} catch (MalformedURLException e) {
        		e.printStackTrace();
        	}
        }
        
        URL fileUrl = urls.firstElement();
        int endIndex = fileUrl.toString().indexOf('&');
		if(endIndex != -1)
		{
			try {
				fileUrl = new URL(fileUrl.toString().substring(0, endIndex));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return fileUrl;
	}
	
	
	//获得视频上传日期
	public String getVideoDate(StringBuffer contentString,String patternString)
	{
		String dateStr = getVideoTitle(contentString,patternString);
		long num = Integer.parseInt(getVideoTitle(new StringBuffer(dateStr), "[0-9]"));
		long mm = System.currentTimeMillis();
		
		//计算上传时间
		if(dateStr.contains("分钟"))
			mm -= num*60*1000;
		else if(dateStr.contains("小时"))
			mm -= num*60*60*1000;
		else if(dateStr.contains("天"))
			mm -= num*60*60*1000*24;
		else if(dateStr.contains("月"))
			mm -= num*60*60*1000*24*30;
		else if(dateStr.contains("年")) {
        	mm -= num*60*60*1000*24*30*365;
		}
		
		String formatStr = "yyyy-MM-dd HH:mm:ss";
		Date timeData = new Date(mm);
		SimpleDateFormat sim = new SimpleDateFormat(formatStr);
		dateStr = sim.format(timeData);
		return dateStr;
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
			list.add(matcher.group());
		}
		
		if(list.size() == 0)
			return tag;
		tag = list.get(0);//获取第一条匹配的
		tag = tag.replaceAll("<.*?>", "").replaceAll("\\pP", "").replaceAll("\\s", "#").replaceAll("[a-z]|[A-Z]|[0-9]|[&gt;]|[~]|[～]", "");
		
		return tag;
		
	}
	
	//获得视频点击率、评论数
	public int[] getVideoClickRateOrComments(StringBuffer contentBuffer,String regex1,String regex2)
	{
		int num[] = {0,0,0};
		
        //页面源码
        String videoId = getContent(contentBuffer,regex1);
        URL newUrl = null;
        try {
			newUrl = new URL("http://v.youku.com/v_vpactionInfo/id/"+videoId+"/pm/1?__rt=1&__ro=info_stat");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StringBuffer newContent = getContent(newUrl,"UTF-8");
		
		if(newContent == null)
			return num;
		
        //获取播放数、评论数、收藏数
		StringBuffer containStr = new StringBuffer(getContent(newContent, regex2).trim().replaceAll("<.*?>", "").replaceAll("	", "#"));
        //根据":"分割字符串-----播放: 89,781#评论: 288#收藏: 65
        num[0] = Integer.parseInt(getContent(containStr, "播放:(.*?)#").replace(",", ""));//点击率
        num[1] = Integer.parseInt(getContent(containStr, "评论:(.*?)#").replace(",", ""));//评论
        num[2] = Integer.parseInt(getContent(containStr, "收藏:(.*?)#").replace(",", ""));//收藏
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
	
	
	//得到评论内容
	public String getCommentContent(StringBuffer contentBuffer,String regex,String content,int minus)
	{
		int countFlag = 1;//评论计数
		
		String result = "";//抓取新的评论内容
		String videoId = getContent(contentBuffer, "videoId\\s*=\\s*\'(.*?)\'");
		String videoId2 = getContent(contentBuffer, "videoId2\\s*=\\s*\'(.*?)\'");
		System.out.println("videoid:" + videoId + "|videoid2:" + videoId2);
		String statusUrlStr = "http://comments.youku.com/comments/~ajax/getStatus.html?__ap=%7B%22videoid%22%3A%22" 
			+ videoId + "%22%2C%22userid%22%3Anull%2C%22oldSid%22%3A-1%7D";
		try {
			
			URL statusUrl = new URL(statusUrlStr);
			StringBuffer statusBuffer = getContent(statusUrl,"UTF-8");
			if(statusBuffer == null)
				return content;
			String last_sid = getContent(statusBuffer, "last_sid\":\"(.*?)\"");
			String last_modify = getContent(statusBuffer, "last_modify\":\"(.*?)\"");
			if(!last_sid.equals("-1"))
			{
				System.out.println("last_sid:" + last_sid + "|last_modify:" + last_modify);
				for(int i = 1;i<=10;i++)
				{
	
					String vpcommentUrlStr = "http://comments.youku.com/comments/~ajax/vpcommentContent.html?__ap=%7B%22videoid%22%3A%22"
						+ videoId + "%22%2C%22sid%22%3A%22" + last_sid + "%22%2C%22last_modify%22%3A%22" + last_modify 
						+ "%22%2C%22page%22%3A" + i + "%2C%22version%22%3A%22v1.14%22%2C%22commentSid%22%3A%22%22%7D";
					URL vpcommentUrl = new URL(vpcommentUrlStr);
					StringBuffer vpcommentBuffer = getContent(vpcommentUrl,"UTF-8");
					if(vpcommentBuffer == null)
						return content + result;
					String total_size = null;
					total_size = getContent(vpcommentBuffer, "totalSize\":\"(.*?)\"");
					if(total_size == null)
						break;
					if(total_size.trim().equals("0"))
						break;
					Pattern pp = Pattern.compile(regex);
					Matcher mm = pp.matcher(vpcommentBuffer.toString().replaceAll("\\\\\"","\""));
					while (mm.find()) 
					{
						countFlag++;
						String temp = mm.group(1).replaceAll("\\\\u","%u").replaceAll("\\\\/", "");
						temp = unescape(temp).replaceAll("<.*?>", "").replaceAll("[\\pP]|[a-z]|[A-Z]|[\\pN]|[\\pS]", " ");
						//.replaceAll("(\\pP)\\1+", "$1");
						temp = temp.replaceAll("\\s+", " ");
						if(!temp.equals("") && !result.contains(temp) && !content.contains(temp) && temp.length() > 6)
							result += temp + "#";
						
						if(minus != -1 && countFlag > minus)
							break;
					}
					
					if(minus != -1 && countFlag > minus)
						break;
					
					if(result.length() > 16777214/2)
						{
							result = result.substring(0,16777214/2);
							break;
						}
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
		//截取前面字段
		if(vTitle.length() > 19)
			vTitle = vTitle.substring(0, vTitle.length()-19);
		vTitle = vTitle.replaceAll("[\\pP]|[a-z]|[A-Z]|[0-9]|[：]|[\\|]|[~]|[～]", "");
		
		//上传日期
		vDatatime = getVideoDate(contentString, "<span class=\"pub\">.*?</span>");
		//标签
		vTag = getVideoTag(contentString, "<div class=\"content\">.*?</div>");
		if(vTag.equals("") || vTitle.equals(""))
			return sqlText;
		vCommentContent = getCommentContent(contentString, "<p id=\\\"content_.*?>(.*?)<","",-1).replaceAll("[?]", "");
		//上传用户
		vUploadUserName = getUploadUsers(contentString, "<a.*?class=\'userName\'>(.*?)</a>");
		sqlText = "insert into videoInf(vUrl,vTitle,vTitleResult,vTag,vTagResult,vCommentContent,vCommentContentResult,vDatatime,vUploadUser,vDownFlag,vComeFrom,vClass,vHot) values('" 
			+ videoUrl + "','" + vTitle + "','','" + vTag + "','','" + vCommentContent 
			+"','','"+ vDatatime +"','"+ vUploadUserName +"',0,'优酷','',0)";
		return sqlText;
	}
	
}