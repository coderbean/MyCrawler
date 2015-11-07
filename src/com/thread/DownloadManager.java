package com.thread;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Vector;

import com.db.Dbmanager;
import com.sitesolver.SiteSolver;


public class DownloadManager extends Thread {
	
	protected URLQueue m_file_que;
	protected int m_maxThreads;
	protected ThreadMessage m_thrd_msg;
	protected String m_file_path;	//下载文件所在路径
	protected RandomAccessFile m_log_file;
	protected FileDownloader m_file_down;
	protected Dbmanager dbmanager;
	protected static Vector<URL> tempUrls = new Vector<URL>();
	protected static URL flagUrl = null;
	
	public DownloadManager()
	{
		m_file_path = System.getProperty("user.dir") + System.getProperty("file.separator") + "download";
	}
	
	public DownloadManager(URLQueue fileUrls,ThreadMessage threadMsg)
	{
		//成员变量初始化
		this.m_file_que = fileUrls;
		this.m_maxThreads = 0;
		this.m_thrd_msg = threadMsg;
		
		//创建下载文件夹
		m_file_path = System.getProperty("user.dir") + System.getProperty("file.separator") + "download";
		File path = new File(m_file_path);
		if (!path.isDirectory())	//如果文件夹不存在，则创建新文件夹
			path.mkdir();
		
		//创建日志文件
		String logPath = System.getProperty("user.dir") + System.getProperty("file.separator") + "log";
		String logFile = logPath + System.getProperty("file.separator") + "dealingFileUrl.log";
		try {
			File commonFile = new File(logPath);
			if (!commonFile.isDirectory())	//生成日志文件夹
				commonFile.mkdir();
			commonFile = new File(logFile);
			if (commonFile.exists())	//删除原日志文件
				commonFile.delete();
			commonFile.createNewFile();	//创建新日志文件
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			m_log_file = new RandomAccessFile(logFile,"rw");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			m_log_file = null;
		}
	}
	
	public DownloadManager(URLQueue fileUrls,ThreadMessage threadMsg,int maxThreads)
	{
		this(fileUrls,threadMsg);
		setMaxThreads(maxThreads);
		
	}
	
	public void run()
	{
		while (true)
		{
			//debug
			System.out.println("hello DownloadManager, with m_file_que.size() = "
					+ m_file_que.size() + " and thread number is " + m_thrd_msg.getThreadNumber() + " of " + m_maxThreads);
			
			dbmanager = new Dbmanager();
			//判断是否有进程终止命令
			if (m_thrd_msg.getStopOrder())
			{
				if (m_log_file == null)
					break;
				
				//写入剩余的未处理网址，关闭日志文件
				try {
					m_log_file.write("\n#not measured URLs:\n".getBytes());
					while (!m_file_que.isEmpty())
						m_log_file.write((m_file_que.take().toString() + "\n").getBytes());
					m_log_file.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
			
			//从文件URL存储队列取出一个URL，新开一个进程，尝试下载文件，记录下载日志
			if (m_thrd_msg.getThreadNumber() < m_maxThreads && !m_file_que.isEmpty())
			{
				//开启新进程
				if(tempUrls.size() == 0)
				{
					URL videoUrl = m_file_que.take();
					flagUrl = videoUrl;//标记该视频是否已经下载
					Vector<URL> fileUrl = new Vector<URL>();
					fileUrl = convert_videoUrl_to_downloadUrl(videoUrl);
					
					if(fileUrl.size() <= 5 && fileUrl.size() > 0)
						tempUrls = fileUrl;//视频下载地址
					else if(fileUrl.size() > 5 || fileUrl.isEmpty())
					{
						String sqlText = "delete from videoinf where vUrl ='"+ videoUrl.toString() +"'";
						System.out.println("delete videoInf:" + sqlText);
						dbmanager.executeUpdate(sqlText);
						continue;
					}
					//将信息插入数据库videoDownloadInf表
					for(int i = 0;i < fileUrl.size();i++)
					{
						String sqlText = getVideoDownloadInf(videoUrl, fileUrl.get(i),i+1);
						if(sqlText != "")
						{
							System.out.println("videoDownloadInf:" + sqlText);
							dbmanager.executeUpdate(sqlText);
						}
						
					}
					
					//记录下载日志
					try {
						if (m_log_file != null)
						{
							GregorianCalendar calendar = new GregorianCalendar(TimeZone.getDefault());
							m_log_file.writeBytes("" +
									calendar.get(Calendar.YEAR) + "-" +
									(calendar.get(Calendar.MONTH)+1) + "-" +
									calendar.get(Calendar.DAY_OF_MONTH) + " " + 
									calendar.get(Calendar.HOUR_OF_DAY) + ":" +
									calendar.get(Calendar.MINUTE) + ":" + 
									calendar.get(Calendar.SECOND) + "." +
									calendar.get(Calendar.MILLISECOND) + "\t");
							m_log_file.write((videoUrl.toString() + "\n").getBytes());
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				} else{
					URL downloadUrl = tempUrls.remove(0);
					//开始下载视频
					try {
						m_file_down = new FileDownloader(downloadUrl,flagUrl,getFilename(downloadUrl),m_thrd_msg);
						m_file_down.start();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					
				}
			}//if
			
			//休眠一定时间
			try {
				sleep(1000*5);	//note:在此设置下载进程等待时间
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public String getFilename(URL fileUrl)
	{
		//解析路径和文件名
		String urlStr = fileUrl.toString().toLowerCase();
		
		String name = "";
		
		if (urlStr.contains("youku"))
		{
			name = (urlStr.substring(urlStr.lastIndexOf('=')+1))+".flv";
			name = name.replaceAll(":", "").replaceAll(",", "");
		}
		else if(urlStr.contains("sina"))
		{
			int begin = urlStr.lastIndexOf('/')+1;
			int end = urlStr.lastIndexOf('.');
			name = urlStr.substring(begin,end) + ".hlv";
		}
		else if(urlStr.contains("ku6.com")){
			int begin = urlStr.lastIndexOf('/')+1;
			int end = urlStr.lastIndexOf('.');
			name = urlStr.substring(begin,end) + ".f4v";
		}
		else
			name = urlStr.substring(urlStr.lastIndexOf('/')+1);
		String path = m_file_path + System.getProperty("file.separator");
		
		//不重命名文件
		//
		//System.out.println("name::" + name);
		return path.replaceAll("\\\\", "/") + name;
		
		
		//如果文件名不存在，返回该文件名
		/*if (!new File(path + name).exists())
			return path+name;
		
		//如果文件名存在，模仿迅雷重命名重复文件
		int counter = 0;
		String testName;
		do{
			testName = path + name.substring(0,name.lastIndexOf('.')) + "(" + counter + ")"+ name.substring(name.lastIndexOf('.'));	//Pay attention!别忘了++
		}while(new File(testName).exists());
		return testName;*/
		
	}
	
	//get video length
	public long getFileSize(URL url)
	{
		long fileSize = 0;
		HttpURLConnection httpConnection = null;
		// 打开连接
		try{
			httpConnection = (HttpURLConnection) url.openConnection();
			httpConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");//IE代理进行下载
			
			//获得网页返回信息码
			int responseCode = httpConnection.getResponseCode();
			
			System.out.println("DownloadManager responseCode: "+responseCode);
			if (responseCode >= 400)	//请求失败
			{
				System.out.println("请求失败:get response code: "+ responseCode);
				//断开网络连接
				httpConnection.disconnect();
				return fileSize;
			}
	
			//获得文件长度
			String strHeader;
			for (int i = 1;(strHeader = httpConnection.getHeaderFieldKey(i)) != null; i++)
			{
				if (strHeader.equals("Content-Length"))//找到内容长度字段
				{
					fileSize = Integer.parseInt(httpConnection.getHeaderField(strHeader));
					break;
				}
			}
			
			//断开网络连接
			httpConnection.disconnect();
		}catch (Exception e) {
			// TODO: handle exception
			System.out.println("fileSize is error!");
			//断开网络连接
			httpConnection.disconnect();
		}
		
		return fileSize;
	}
	
	//获取下载地址
	public Vector<URL> convert_videoUrl_to_downloadUrl(URL url)
	{
		String videoUrlString = "http://www.flvcd.com/parse.php?kw=" + url.toString() + "&go=1";
		String regex = "";
		if(videoUrlString.contains("youku"))
			regex = "<a href=\"(http://\\S*/flv/\\S*)\"";
		else if(videoUrlString.contains("sina"))
			regex = "<a href\\s*=\\s*\"(http://\\S*?.hlv\\S*)\"";
		else if(videoUrlString.contains("ku6"))
			regex = "<a href\\s*=\\s*\"(http://\\S*-f4v-\\S*?)\"";
		else if(videoUrlString.contains("cntv"))
			regex = "<a href\\s*=\\s*\"(http://\\S*?.mp4)\"";
		else if(videoUrlString.contains("163"))
			regex = "<a href=\"(http://flv4.bn.netease.com/\\S*.flv)\"";
		Vector<URL> sub1Urls = new Vector<URL>();
		try {
			StringBuffer newBuffer = SiteSolver.getContent(new URL(videoUrlString));
			if(newBuffer == null)
				return sub1Urls;
			sub1Urls = SiteSolver.getMatchedUrls_Group1(newBuffer, "", regex, "");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sub1Urls;
	}
	
	
	public String getVideoDownloadInf(URL url1,URL url2,int flag)
	{
		
		String videoUrl = url1.toString();	//视频播放地址
		
		DownloadManager download = new DownloadManager();
		String vPath = download.getFilename(url2);//从下载地址中获取文件名
		//vPath = vPath.replace("\\", "\\\\");//文件路径
		String sqlText = "";
		//文件大小
		long vFileSize = download.getFileSize(url2);
		if(vFileSize == 0)
			return sqlText;
		sqlText = "insert into videoDownloadInf(vUrl,vPath,vFileSize,vSequence,vProcessing) values('" 
			+ videoUrl + "','"+ vPath +"',"+ vFileSize +","+ flag +",0)";
		// TODO Auto-generated catch block
		
		return sqlText;
	}
	
	public void setMaxThreads(int maxThreads)
	{
		this.m_maxThreads = maxThreads;
	}
	
	public int getMaxThreads()
	{
		return this.m_maxThreads;
	}
}
