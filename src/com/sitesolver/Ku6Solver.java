package com.sitesolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.db.Dbmanager;
import com.thread.URLQueue;


/**
输入一个ku6网的网页URL，获得其中包含的其他土豆网网页和下载文件URL
*/
public class Ku6Solver extends SiteSolver {
	
	private String sqlText = "";	//sql语句
	private String videoUrl = "";	//视频播放地址
	private String vTitle = "";		//视频标题
	private String vTag = "";		//视频标签
	private String vCommentContent = "";	//评论内容
	private int[] count = new int[3];//点击率\评论数、收藏
	private String vDatatime = "";	//上传日期
	private String vUploadUserName = "";//上传作者
	private Dbmanager dbmanager;
	private StringBuffer contentBuffer = null;
	
	public Ku6Solver(URL url,URLQueue pageQueue,URLQueue fileQueue) {
		super(url, pageQueue, fileQueue);
		contentBuffer = super.getContent(m_url,"GBK");
		dbmanager = new Dbmanager();
	}
	
	//是否为有效的酷6的URL
	public boolean isTrueUrl(URL url)
	{
		boolean flag = false;
		String urlStr = url.toString().toLowerCase();
		if(urlStr.contains("news.ku6.com") || urlStr.contains("v.ku6.com"))
			flag = true;
		return flag;
	}
	
	public void analyze()
	{
		//检验是否为酷6网URL
		if (!isTrueUrl(m_url))
			return;
		
		//debug
		System.out.println("Ku6Solver start working...");
		
		if(contentBuffer == null)
		{
			System.out.println("Ku6Solver end work, can not get the web content.");
			return;
		}
		//如果网页为视频网页，分析之，退出程序
		if (analyzeFileUrl(m_url))
		{
			//debug
			System.out.println("url为视频网页，Ku6Solve end work, with input URL a file URL.");
			System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
			
			return;
		}
		
		//网页不是视频播放网页，获取普通URL
		Vector<URL> pageUrls = super.getMatchedUrls(contentBuffer, 
				"", "http://news.ku6.com/news_(news|paike|society|local)/", "");
		for(int i=0;i<pageUrls.size();i++)
			m_page_que.put(pageUrls.get(i));
		
		try {
			if(m_url.equals(new URL("http://news.ku6.com/news_news/")) || m_url.equals(new URL("http://news.ku6.com/news_local/")) || m_url.equals(new URL("http://news.ku6.com/news_society/")))
			{
				pageUrls = getNewsTag(m_url);
				for(int i=0;i<pageUrls.size();i++)
					m_page_que.put(pageUrls.get(i));
			}
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//否则，在网页中查找视频网页链接，加入page队列"http://v.ku6.com/(show/|special/show_)\\S+.html"
		Vector<URL> videoUrls = super.getMatchedUrls(contentBuffer,
				"","http://v.ku6.com/(show/|special/show_)\\S+.html","");
		for (int i=0; i < videoUrls.size(); ++i)
			m_page_que.put(videoUrls.get(i));
			
		//debug
		System.out.println("url为普通网页，Ku6Solver end work, with input URL a page URL.");
		System.out.println("two storages' sizes are " + m_page_que.size() + " and " + m_file_que.size());
	}
	
	//ku6分页获取0-39页
	public Vector<URL> getNewsTag(URL url)
	{
		Vector<URL> pageNewsTagUrls = new Vector<URL>();
		int max = 40;
		String page;
		
		for(int i = max-1;i >= 0;i--)
		{
			String tempUrl = url.toString();
			if(0 == i)
				tempUrl = tempUrl + "index.shtml";
			else 
			{
				page = String.valueOf(i);
				tempUrl = tempUrl + "index_"+ page +".shtml";
			}
			try {
				pageNewsTagUrls.add(new URL(tempUrl));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return pageNewsTagUrls;
	}
	
	public boolean analyzeFileUrl(URL fileUrl)
	{
		
		//判断是否为文件URL
		if (!super.checkUrl(fileUrl,"http://v.ku6.com/\\S+.html"))
			return false;
		
		//以下操作数据库
		//更新点击率和评论数
		count = getVideoClickRateOrComments(fileUrl.toString(),",count:\"(.*?)\"","<em>(.*?)</em>");
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
			if(minus > 0){
				//评论数有增加
				videoCommentStr = getCommentContent(fileUrl.toString(), "<p>(.*?)</p>",videoCommentStr,minus);
				String sqlUpdateCommentContent = "update videoInf set vCommentContent ='" + videoCommentStr +"' where vUrl='" + fileUrl.toString() + "'";
				dbmanager.executeUpdate(sqlUpdateCommentContent);
			}
			
			
			System.out.println("该记录已经存在,更新点击率和评论数");
			dbmanager.executeUpdate(sqlVideoInfUpdate);
			return true;
		}
		
		//读取网页内容，将其他视频播放地址插入m_page_que
		Vector<URL> sub2Urls = super.getMatchedUrls(contentBuffer,
				"","http://v.ku6.com/(show/|special/show_)\\S+.html","");
		for(int i=0;i < sub2Urls.size();i++)
			m_page_que.put(sub2Urls.get(i));
		
		//返回真
		return true;
	}
	
	public String getVideoTag(StringBuffer contentString,String regex1,String regex2)
	{
		String tag = ""; 
		int count = 0;
		String div = getContent(contentString, regex1);
		Pattern pattern = Pattern.compile(regex2,Pattern.CANON_EQ);
		Matcher matcher = pattern.matcher(div);
		while(matcher.find())
		{ 	if(count == 3)
				tag = matcher.group(1).toString().trim();
			count++;
		}
		
		List<String> list = new ArrayList<String>();  
		pattern = Pattern.compile("<a href=.*?>(.*?)</a>",Pattern.CANON_EQ);
		matcher = pattern.matcher(tag);
		while(matcher.find())
		{
			list.add(matcher.group());
		}
		
		tag = "";
		
		if(list.size() == 0)
			return tag;
		
		for(int i=0;i<list.size();i++)
			tag += list.get(i).replaceAll("<.*?>", "").replaceAll("\\pP", "")+"#";
		
		return tag.replaceAll("[a-z]|[A-Z]|[0-9]|[：]|[~]|[～]", "");
	}
	
	//获得视频上传日期
	 public String getVideoDate(StringBuffer contentString,String regex1,String regex2)
	    {
	        Date date = new Date();
	        SimpleDateFormat dn = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	        String divStr = getContent(contentString, regex1);
	        if(divStr == null)
	            return dn.format(date);
	        String dateStr = getContent(new StringBuffer(divStr), regex2);
	        if(dateStr == null)
	            return dn.format(date);
	        long num = Integer.parseInt(getVideoTitle(new StringBuffer(dateStr), "([0-9])"));
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
	        Date timeData = new Date(mm);
	        dateStr = dn.format(timeData);
	        return dateStr;
	    }
	
	//获得视频点击率、评论数
	public int[] getVideoClickRateOrComments(String strUrl,String regex1,String regex2)
	{
		
		int num[] = {0,0,0};
		//点击率
		String tempStr = strUrl.substring(strUrl.lastIndexOf("/")+1, strUrl.lastIndexOf("."));
		String clickRateUrl = "http://v0.stat.ku6.com/dostatv.do?method=getVideoPlayCount&v=" + tempStr;
		URL newUrl = null;
		try {
			newUrl = new URL(clickRateUrl);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		StringBuffer conteBuffer = super.getContent(newUrl,"GBK");
		
		if (getContent(conteBuffer, regex1) == null) 
			num[0] = 0;
		else
			num[0] = Integer.parseInt(getContent(conteBuffer, regex1));
		
		//评论数
		tempStr = strUrl.substring(strUrl.lastIndexOf("/")+1);
		String commentUrl = "http://comment.ku6.com/podv-" + tempStr;
		try {
			newUrl = new URL(commentUrl);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		conteBuffer = super.getContent(newUrl,"GBK");
		if(getContent(conteBuffer, regex2) == null)
			return num;
		else
			num[1] = Integer.parseInt(getContent(conteBuffer, regex2));
		return num;
	}
	
	public String getCommentContent(String videoUrlStr,String regex,String content,int minus)
	{
		int countFlag = 1;//评论计数
		
		String result = "";
		String tempStr = videoUrlStr.substring(videoUrlStr.lastIndexOf("/")+1);
		String commentContentUrlStr = "http://comment.ku6.com/podv-" + tempStr;
		try {
			URL commentContentUrl = new URL(commentContentUrlStr);
			StringBuffer contentBuffer = getContent(commentContentUrl,"GBK");
			Pattern pp = Pattern.compile(regex);
			Matcher mm = pp.matcher(contentBuffer);
			while (mm.find()) 
			{
				countFlag++;
				
				String temp = mm.group(1).trim();
				temp = temp.replaceAll("<br />", "").replaceAll("<.*?>", "").replaceAll("[\\pP]|[a-z]|[A-Z]|[\\pN]|[\\pS]", " ");
				temp = temp.replaceAll("\\s+", " ");
				if(!temp.equals("") && !result.contains(temp) && !content.contains(temp) && temp.length() > 6)
					result += temp + "#";
				
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
	
	//get author
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
	
	public String getVideoInf(URL url1,StringBuffer contentBuffer)
	{
		
		videoUrl = url1.toString();	//视频播放地址
		String sqlText = "";
		//视频主题
		vTitle = getVideoTitle(contentBuffer, "<title>.*?</title>");
		if(vTitle.length() > 13)//截取前面字段
			vTitle = vTitle.substring(0, vTitle.length()-13);
		vTitle = vTitle.replaceAll("[\\pP]|[a-z]|[A-Z]|[0-9]|[：]|[\\|]|[~]|[～]", "");
		
		//上传日期
		vDatatime = getVideoDate(contentBuffer, "<div class=\"infoBox cfix\">(.*?)</div>","<em>(.*?)</em>");
		//标签
		vTag = getVideoTag(contentBuffer, "<div class=\"infoBox cfix\">(.*?)</div>","<dd>(.*?)</dd>");
		if(vTag.equals("") || vTitle.equals(""))
			return sqlText;
		vCommentContent = getCommentContent(videoUrl, "<p>(.*?)</p>","",-1);
		//上传用户
		vUploadUserName = getUploadUsers(contentBuffer, "<a.*?class=\"author\">(.*?)</a>");
		sqlText = "insert into videoInf(vUrl,vTitle,vTitleResult,vTag,vTagResult,vCommentContent,vCommentContentResult,vDatatime,vUploadUser,vDownFlag,vComeFrom,vClass,vHot) values('" 
			+ videoUrl + "','" + vTitle + "','','" + vTag + "','','" + vCommentContent 
			+"','','"+ vDatatime +"','"+ vUploadUserName +"',0,'酷6','',0)";
		return sqlText;
	}
	
	
}