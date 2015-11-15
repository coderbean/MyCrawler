package com.seeds;

/**
 * 2015年11月7日,修改获取标签的正则表达式
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.db.Dbmanager;
import com.sitesolver.SiteSolver;


public class InitTagUrl {
	
	protected Dbmanager dbmanager;
	private String sqlText = "";
	private String fileName = "";			//种子文件的路径名
	
	
	public InitTagUrl()
	{
		dbmanager = new Dbmanager();
	}
	
	//获取给定监控网站的标签目录
	public void initSeedsUrl(URL url,String regex1,String regex2,String charset)
	{
		Pattern pattern = Pattern.compile(regex1);
	    Matcher matcher = pattern.matcher(SiteSolver.getContent(url,charset));
	    if(!matcher.find())
	    	 return;
	     
	    String containStr =  matcher.group(0);
	    Pattern pattern2 = Pattern.compile(regex2);
	    Matcher matcher2 = pattern2.matcher(containStr);
	    while(matcher2.find())
	    {
	    	String tagUrl ="";
	    	if(url.toString().contains("cntv"))
	    		tagUrl = "http://xiyou.cntv.cn" + matcher2.group(1).trim();
	    	else 
	    		tagUrl = matcher2.group(1).trim();
	    	
	    	String tagStr = matcher2.group(2).replaceAll("<.*?>", "").trim();
	    	sqlText = "insert into seeds values('"+ tagUrl +"','" + tagStr +"',false)";
	    	System.out.println(sqlText);
	    	String checkSqlStr = "select * from seeds where seedsUrl ='"+ tagUrl +"'";
	    	ArrayList list = dbmanager.executeQuery(checkSqlStr);
			if(list.size() == 1)
				System.out.println("该记录已经存在");
			else
				dbmanager.executeUpdate(sqlText);
	    }
	}
	
	//初始化种子URL
	public void initTagWithSeeds()
	{
		fileName = System.getProperty("user.dir") + System.getProperty("file.separator")
			+"src/com/seeds/seeds.txt";
		File file = new File(fileName);
        BufferedReader reader = null;
        try {
        	//debug
            System.out.println("以行为单位读取文件内容，一次读一整行：");
            
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // debug
                System.out.println(tempString);
                if(tempString.contains("youku"))
					// 标签的获取正则已经过时,更新日期2015年11月7日By张博
                	initSeedsUrl(new URL(tempString),
							"<div class=\"yk-nav\"><div class=\"yk-box\"><div class=\"yk-nav-second\">",
                			"<li .*?><a .*?href=\"(.*?)\"\\s*.*?>(.*?)</a></li>","UTF-8");
                else if(tempString.contains("ku6"))
                	initSeedsUrl(new URL(tempString), 
                			"<div class=\"yl-tl.*?clear-float",
                			"<div class=\"yl-tl.*?clear-float\"><span><SPAN>(.*?)</span> </SPAN>","GBK");
                else if(tempString.contains("cntv"))
                	initSeedsUrl(new URL(tempString), "<ul class=\"dropdown\">([\\s\\S]*?)</ul>",
                			"<li ><a href=\"/video/index-hot-\\d.html\">.*?</a></li>", "UTF-8");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
	}
	
	/**
     * A方法追加文件：使用RandomAccessFile
     * 为种子文件seeds.txt写入文件留个接口
     */
    public static void appendMethodA(String fileName, String content) {
        try {
            // 打开一个随机访问文件流，按读写方式
            RandomAccessFile randomFile = new RandomAccessFile(fileName, "rw");
            // 文件长度，字节数
            long fileLength = randomFile.length();
            //将写文件指针移到文件尾。
            randomFile.seek(fileLength);
            randomFile.writeBytes(content);
            randomFile.close();
        } catch (IOException e) {
        	e.printStackTrace();
        }
    }
    
    /**
     * 初始化数据库videoInf表中vDownDlag=1的记录
     * 因为爬虫重启会中断正在下载的视频，故需要更新videoInf中vDownFlag=0，videoInfDownload表中有该视频记录的清楚，
     * 并删除下载文件中相应的视频
     */
    public void initVideoInfDownFlag(){
		String sqlText_CheckDownFlag = "SELECT vDownID,vPath FROM videoDownloadInf,videoInf WHERE videoDownloadInf.vUrl=videoInf.vUrl AND vDownFlag=1;";
		ArrayList CheckDownFlagSet = new ArrayList();
		CheckDownFlagSet = dbmanager.executeQuery(sqlText_CheckDownFlag);
		String sqlText_updateVideoInfDownload = "";
		String sqlText_updateVideoInf = "UPDATE videoInf SET vDownFlag=0 WHERE vDownFlag=1;";
		
		for(int i=0;i < CheckDownFlagSet.size();i++){
			
			HashMap record = (HashMap)CheckDownFlagSet.get(i);
			int vDownID = Integer.parseInt(record.get("vDownID").toString());
			String vPath = record.get("vPath").toString();
			//删除文件
			boolean flag = DeleteFolder(vPath);
			if(flag){
				System.out.println("File have deleted!");
			}else {
				System.out.println("File can't find!");
			}
			sqlText_updateVideoInfDownload = "DELETE from videoDownloadInf where vDownID = "+ vDownID +";";
			dbmanager.executeUpdate(sqlText_updateVideoInfDownload);
		}
		dbmanager.executeUpdate(sqlText_updateVideoInf);
		System.out.println("update finished!");
	}

    //删除文件方法
	public static boolean DeleteFolder(String sPath) {  
	    boolean flag = false;  
	    File file = new File(sPath);  
	    // 判断目录或文件是否存在  
	    if (file.isFile() && file.exists()) {  // 不存在返回 false  
	        file.delete();
	        flag = true;
	    }  
	    return flag;
	}

}
