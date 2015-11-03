package com.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class Dbmanager {
	// 驱动程序名
    private final String driver = "com.mysql.jdbc.Driver";
    // URL指向要访问的数据库名yuqing
    private final String url = "jdbc:mysql://localhost:3306/yuqing?useUnicode=true&characterEncoding=GBK";
    // MySQL配置时的用户名
    private final String user = "root";
    // MySQL配置时的密码
    private final String password = "";
    //连接数据库
    private Connection conn = null;
    private Statement st = null;
    private ResultSet rs = null;
	public Dbmanager()
	{
        
	}
	
	//链接数据库
	public Connection startConn(Connection conn){
		 try {
			 	//加载驱动程序
			 	Class.forName(driver);
			 	//链接数据库
			 	conn = DriverManager.getConnection(url,user, password);
		  } catch (Exception e) {
			  e.printStackTrace();
			  System.out.println("连接数据库时出现错误");
		  }
		  return conn;
	}
	
	//执行查询语句，返回数据集
	public ArrayList  executeQuery(String sqlString)
	{
		
		ArrayList listSet = new ArrayList();
		ResultSetMetaData rsmd = null;//获取数据库列名
		Map rsTree;
		int numberOfColumns;//返回集的列数
		try {
			conn = startConn(conn);
			st = conn.createStatement();
			rs = st.executeQuery(sqlString);
			rsmd = rs.getMetaData();	//取数据库的列名 我觉得名比1，2，3..更好用
			numberOfColumns = rsmd.getColumnCount();	//获得列数
			while(rs.next())
			{
				rsTree = new HashMap(numberOfColumns);
				
				for(int i = 1;i <= numberOfColumns;i++)
				{
					rsTree.put(rsmd.getColumnName(i), rs.getObject(i));
				}
				
				listSet.add(rsTree);
			}
		} catch (SQLException e) {
			System.out.println("查询数据库数据时发生错误!");
		}finally{
			closeConn(conn, st, rs);
		}
		
		return listSet;
	}
	
	//执行非查询语句，只用于更新、删除、插入数据,返回0为不成功
	public int executeUpdate(String sqlString)
	{
		
		int count = 0;
		try {
			conn = startConn(conn);
			st = conn.createStatement();
			count = st.executeUpdate(sqlString);
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("修改，插入或者删除数据库数据时发生错误!");
		}finally{
			closeConn(conn,st);
		}
		
		return count;
	}
	
	public void closeConn(Connection connection,Statement statement)
	{
		try {
			   if(statement != null){
				   statement.close();
			   }
			   if(connection != null){
				   connection.close();
			   }
			 } catch (SQLException e) {
			   // TODO Auto-generated catch block
			   System.out.println("关闭数据库的时候发生错误!");
			 }
	}
	
	public void closeConn(Connection connection,Statement statement,ResultSet resultSet)
	{
		try{
			if(resultSet != null)
				resultSet.close();
			if(statement != null)
				statement.close();
			if(connection != null)
				connection.close();
		}catch(SQLException e)
		{
			e.printStackTrace();
			System.out.println("关闭数据库的时候发生错误!");
		}
	}
}
