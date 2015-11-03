package com.main;

import java.net.*;
import java.util.ArrayList;

import com.db.Dbmanager;
import com.seeds.InitTagUrl;
import com.thread.DownloadManager;
import com.thread.ThreadMessage;
import com.thread.URLProcessManager;
import com.thread.URLQueue;

/**
 * The Program is web video crawler which gathers hits,
 *  	the number of comments, comments content, the title,
 *  	upload time, upload the author, video
 * @author ninja E-mail:ninjashen88@gmail.com
 * @version 20120708
 */

public class MyCrawler extends Thread {

	protected URLQueue m_page_que;			//URL页面队列
	protected URLQueue m_file_que;			//文件队列
	protected URLProcessManager m_proc_mgr;	//进程管理
	protected DownloadManager m_dnld_mgr;	//下载管理
	protected ThreadMessage m_proc_msg;		//进程数量信息
	protected ThreadMessage m_dnld_msg;		//下载数量信息
	protected static InitTagUrl m_init_Tag;		//初始化网站标签
	protected Dbmanager dbmanager;
	
	/**
	构造函数
	*/
	public MyCrawler()
	{
		//变量初始化
		m_page_que = new URLQueue("pageUrlQueue.log");//初始化页面队列、写入日志文件pageUrlQueue.log
		m_file_que = new URLQueue("fileUrlQueue.log");
		m_proc_msg = new ThreadMessage();
		m_dnld_msg = new ThreadMessage();
		m_proc_mgr = new URLProcessManager(m_page_que,m_file_que,m_proc_msg);
		m_dnld_mgr = new DownloadManager(m_file_que,m_dnld_msg);
		dbmanager = new Dbmanager();
	}
	   
	public void initCrawlerWithSeeds(String sqlTest)
	{
		ArrayList list = dbmanager.executeQuery(sqlTest);
		for(int i=0;i < list.size();i++)
		{
			URL url = null;
			try {
				String temp = list.get(i).toString();
				temp = temp.substring(10,temp.length()-1);
				url = new URL(temp);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			m_page_que.put(url);
		}
		
	}
	/**
	向网页URL队列中添加元素
	*/
	public void PushPageQueue(URL url)
	{
		m_page_que.put(url);
	}

	/**
	设置最大网址分析线程数
	*/
	public void setMaxURLProcThreads(int maxThreads)
	{
		m_proc_mgr.setMaxThreads(maxThreads);
	}
	
	/**
	设置最大文件下载线程数
	*/
	public void setMaxDwonloadThreads(int maxThreads)
	{
		m_dnld_mgr.setMaxThreads(maxThreads);
	}
	
	/**
	执行进程
	*/
	public void run()
	{	
		//启动调度进程
		m_proc_mgr.start();
		m_dnld_mgr.start();
		
		//进程自动终止
//		try {
//			sleep(1000*60*1);	//运行5分钟，自动停止
//			terminate();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		
		//debug
		System.out.println("all processes will terminate a short time later.");
		 
		}
	
	/**
	终止进程命令，使程序所包含所有进程终止
	 */
	public void terminate()
	{
		m_proc_msg.setStopOrder(true);
		m_dnld_msg.setStopOrder(true);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		try {
			MyCrawler myCrawler = new MyCrawler();
			//初始化被中断的文件
			m_init_Tag = new InitTagUrl();
			m_init_Tag.initVideoInfDownFlag();
//			m_init_Tag.initTagWithSeeds();
//			String sqlText = "select seedsUrl from seeds where seedsFlag = false";
//			myCrawler.initCrawlerWithSeeds(sqlText);
			myCrawler.PushPageQueue(new URL("http://news.youku.com/"));
			myCrawler.PushPageQueue(new URL("http://news.sina.com.cn/bn/shyfz/"));
			myCrawler.PushPageQueue(new URL("http://v.163.com/zixun/V7M3CBCH5/1/rank_1.html"));
			myCrawler.PushPageQueue(new URL("http://news.ku6.com/"));
			myCrawler.PushPageQueue(new URL("http://xiyou.cntv.cn/video/index-new-2.html"));
			//debug
			System.out.println("init size of m_page_que is " + myCrawler.m_page_que.size());
			
			myCrawler.setMaxURLProcThreads(5);
			myCrawler.setMaxDwonloadThreads(5);
			myCrawler.start();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
