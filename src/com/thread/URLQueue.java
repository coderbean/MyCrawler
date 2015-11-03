/**
 构建URL队列、创建日志文件
 */
package com.thread;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.net.URL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;


/**
存储队列，存储URL（在队列生存期内检验重复）
*/
public class URLQueue {
	protected Set<URL> m_url_rec;
	protected LinkedBlockingQueue<URL> m_url_que;//url队列
	protected RandomAccessFile m_log_file;//日志文件
	
	//构造函数
	public URLQueue()
	{
		m_url_rec = Collections.synchronizedSet(new HashSet<URL>());	//同步的哈希表
		m_url_que = new LinkedBlockingQueue<URL>();	//URL队列
	}
	
	//重构
	public URLQueue(String logFile)
	{
		this();

		//创建日志文件
		//System.getProperty("user.dir")  --获得用户当前目录
		//System.getProperty("file.separator")  --文件分隔符
		String logPath = System.getProperty("user.dir") + System.getProperty("file.separator") + "log";
		logFile = logPath + System.getProperty("file.separator") + logFile;
		try {
			File commonFile = new File(logPath);
			//生成日志文件夹,isDirectory()当且仅当此抽象路径名表示的文件存在且 是一个目录时，返回 true；否则返回 false
			if (!commonFile.isDirectory())	
				commonFile.mkdir();          //创建此抽象路径名指定的目录。
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
	
	
	
	
	//url放入队列
	public void put(URL url)
	{

		//检验记录中是否有此URL
		if (m_url_rec.add(url))		//note:使用Hash查询URL是否重复//.clear()用于清除set元素
		{
			try {
				m_url_que.put(url);	//在队列中加入此URL
				if (m_log_file!=null)
				{
					m_log_file.writeBytes("" + m_url_rec.size() + "\t");
					GregorianCalendar calendar = new GregorianCalendar(TimeZone.getDefault());
					m_log_file.writeBytes("" +
							calendar.get(Calendar.YEAR) + "-" +
							(calendar.get(Calendar.MONTH)+1) + "-" +
							calendar.get(Calendar.DAY_OF_MONTH) + " " + 
							calendar.get(Calendar.HOUR_OF_DAY) + ":" +
							calendar.get(Calendar.MINUTE) + ":" + 
							calendar.get(Calendar.SECOND) + "." +
							calendar.get(Calendar.MILLISECOND) + "\t");
					m_log_file.writeBytes(url.toString().trim()+"\n");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
	}
	
	//获取并移除此队列的URL
	public URL take()
	{
		try
		{
			return m_url_que.take();
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
			return null;
		}
	}
	
	//获取但不移除此队列的头；如果此队列为空，则返回 null。
	public URL peek()
	{
		return m_url_que.peek();
	}
	
	//获取队列的长度
	public int size()
	{
		return m_url_que.size();
	}
	
	//检查队列是否为空
	public boolean isEmpty()
	{
		return m_url_que.isEmpty();
	}
}
