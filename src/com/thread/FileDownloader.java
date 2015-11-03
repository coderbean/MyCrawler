package com.thread;

import java.io.*;
import java.net.*;

import com.db.Dbmanager;


public class FileDownloader extends Thread {
	
	protected RandomAccessFile m_file;
	protected URL m_url;
	protected URL m_videoUrl;
	protected ThreadMessage m_thrd_msg;
	protected Dbmanager dbmanager;
	
	public FileDownloader(URL fileUrl,URL videoUrl,String filename,ThreadMessage threadMsg) throws FileNotFoundException
	{
		//debug
		System.out.println("initializing FileDownloader with URL: " + fileUrl.toString());
		
		this.m_url = fileUrl;
		this.m_videoUrl = videoUrl;
		this.m_file = new RandomAccessFile(filename, "rw");//读取和写入模式
		this.m_thrd_msg = threadMsg;
	}
	
	public void run()
	{
		dbmanager = new Dbmanager();
		m_thrd_msg.addThread();
		HttpURLConnection httpConnection = null;
		int responseCode = -1;
		int flag = -1; //0为未下载、1为开始下载、2为下载完成、-1为下载失败
		try
		{
			//debug
			System.out.println("FileDownloader now dealing with URL: " + m_url.toString());

			// 打开连接
			httpConnection = (HttpURLConnection) m_url.openConnection();
			httpConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");//IE代理进行下载
			//debug
			System.out.println("first connect working...");
			
			//获得网页返回信息码
			
			responseCode = httpConnection.getResponseCode();//下载模块问题出在这，访问拒绝：没办法，那就不下载
			//debug
			System.out.println(m_url.toString() + " #get response code: " + responseCode);
			
			if (responseCode >= 400)	//请求失败
			{
				//断开网络连接
				httpConnection.disconnect();
				m_thrd_msg.decThread();
				return;
			}
	
			//获得文件长度
			long fileSize = 0;
			String strHeader;
			for (int i = 1;(strHeader = httpConnection.getHeaderFieldKey(i)) != null; i++)
			{
				if (strHeader.equals("Content-Length"))//找到内容长度字段
				{
					fileSize = Integer.parseInt(httpConnection.getHeaderField(strHeader));
					break;
				}
			}
			//debug
			System.out.println("first connect ended. get content length: " + fileSize);
			
			//获得开始下载位置	//note:在此处添加断点续传功能，先读已下载部分大小，再从断点继续下载
			long startPos = m_file.length();
			//判断是否需要下载
			if (startPos >= fileSize)
			{
				
				httpConnection.disconnect();
				m_thrd_msg.decThread();
				return;
			}
			//debug
			System.out.println("second connect working...");
		
			// 读取网络文件,写入指定的文件中
			System.out.println("reading file now...");
			//debug--开始下载标记
			String sqlText = "UPDATE videoinf set vDownFlag = 1 where vUrl='"+ m_videoUrl.toString()+"'";
			dbmanager.executeUpdate(sqlText);
			
			InputStream istream = httpConnection.getInputStream();
			m_file.seek(startPos);			//设置文件开始读取的位置
			byte[] buf = new byte[1024];	//设置1M缓存
			int readNum = 0;
			while ((readNum = istream.read(buf)) > 0 && startPos < fileSize)
			{
				if (m_thrd_msg.getStopOrder())	//进程中止命令
				{
					m_thrd_msg.decThread();
					return;
				}
				
				m_file.write(buf, 0, readNum);
				startPos += readNum;
				sleep(100);	//要有道德，别忘了sleep，否则会把人家网站下瘫了
			}
			
			System.out.println("file reading completed.");
			flag = 2;//下载成功
		} catch (Exception e) {
			e.printStackTrace();
			//debug
			System.out.println("FileDownloader unable to download this file due to exception." + m_url.toString());
		} 
		//断开网络连接
		httpConnection.disconnect();
		m_thrd_msg.decThread();
		
		//debug
		String sqlText = "UPDATE videoinf set vDownFlag = "+flag+" where vUrl='"+ m_videoUrl.toString()+"'";
		dbmanager.executeUpdate(sqlText);
		System.out.println("更新下载视频标记：" + sqlText);
		//debug
		System.out.println("FileDownloader work complete.");
		System.out.println("FileDownloader end work with thread number: " + m_thrd_msg.getThreadNumber());
	
	}
}
