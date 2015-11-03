/**
 * 对线程数量的控制
 */
package com.thread;

public class ThreadMessage {

	protected boolean stopOrder;
	protected int threadNumber;
	
	public ThreadMessage()
	{
		threadNumber = 0;
		stopOrder = false;
	}
	
	//设置停止命令
	public void setStopOrder(boolean shouldStop)
	{
		stopOrder = shouldStop;
	}
	
	//获得是否停止命令
	public boolean getStopOrder()
	{
		return stopOrder;
	}
	
	//设定线程数量
	public void setThreadNumber(int num)
	{
		threadNumber = num;
	}
	
	//获得线程数量
	public int getThreadNumber()
	{
		return threadNumber; 
	}
	
	//增加线程数量
	public void addThread()
	{
		++threadNumber;
	}
	
	//减少线程数量
	public void decThread()
	{
		--threadNumber;
	}
	
	
}
