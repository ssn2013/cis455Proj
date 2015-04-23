package com.datformers.master.servlets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import com.datformers.master.resources.CrawlerStatus;
import com.datformers.resources.HttpClient;

public class MasterServlet extends HttpServlet{
	private Map<String, CrawlerStatus> crawlerStatusMap = new HashMap<String, CrawlerStatus>();
	private String seedFileName;
	private int maxRequests;
	public void init(ServletConfig servletConfig) throws javax.servlet.ServletException {
		super.init(servletConfig);
		seedFileName = getServletConfig().getInitParameter("SeedURlFile");
		maxRequests = Integer.parseInt(getServletConfig().getInitParameter("MaxRequests"));
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException  {
		try{
			if(request.getPathInfo()!=null&&request.getPathInfo().contains("workerstatus")) {
				String ipAddress = request.getHeader("X-FORWARDED-FOR");
				if (ipAddress == null) {  
					ipAddress = request.getRemoteAddr();  
				}
				int port = Integer.parseInt(request.getParameter("port").trim());
				String status = request.getParameter("status");
				System.out.println("port: "+port+" status: "+status+" ipaddress: "+ipAddress);

				//set crawler status
				CrawlerStatus crawlerStatus = new CrawlerStatus();
				crawlerStatus.setIpAddress(ipAddress);
				crawlerStatus.setPort(port);
				crawlerStatus.setStatus(status);

				System.out.println("IPAddres: "+crawlerStatus.getIpAddress()
						+"\nPORT: "+crawlerStatus.getPort()
						+"\nSTATUS: "+crawlerStatus.getStatus());

				//add to map
				crawlerStatusMap.put(crawlerStatus.getIpPortString(), crawlerStatus);
				
				//Check if time for checkpointing
				if(checkForCheckpoiting())
					callForCheckpoint();
			} else if(request.getPathInfo()!=null&&request.getPathInfo().contains("startCrawling")) {
				//Make crawl requests to all crawlers
				makeCrawlRequests();
			} else if(request.getPathInfo()!=null&&request.getPathInfo().contains("stopCrawling")) {
				//Stop all crawling();
				stopCrawling();
			} else {
				StringBuffer htmlBuffer = new StringBuffer("<html><body>");
				htmlBuffer.append("<form method=\"get\" action=\"master/startCrawling\"><input type=\"submit\" value=\"Start Crawling\"></form><br/>");
				htmlBuffer.append("<form method=\"get\" action=\"master/stopCrawling\"><input type=\"submit\" value=\"Stop Crawling\"></form>");
				htmlBuffer.append("</body></html>");
				response.getWriter().println(htmlBuffer.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
	}

	private void callForCheckpoint() {
		for(String key: crawlerStatusMap.keySet()) {
			HttpClient client = new HttpClient();

			System.out.println("SENDING: STOP CRAWL TO: "+"http://"+key+"/checkpoint"
					+"\nPORT: "+ crawlerStatusMap.get(key).getPort());
			client.makeRequest("http://"+key+"/checkpoint", crawlerStatusMap.get(key).getPort(), new HashMap<String, String>());
		}
	}

	private boolean checkForCheckpoiting() {
		int countOfDoneWorkers = 0;
		for(String key: crawlerStatusMap.keySet()) {
			if(crawlerStatusMap.get(key).getStatus().equals("done")) 
				countOfDoneWorkers++;
		}
		if(countOfDoneWorkers == crawlerStatusMap.keySet().size())
			return true;
		else 
			return false;
	}

	private void stopCrawling() {
		//Make request to stop crawling
		for(String key: crawlerStatusMap.keySet()) {
			HttpClient client = new HttpClient();

			System.out.println("SENDING: STOP CRAWL TO: "+"http://"+key+"/stopcrawler"
					+"\nPORT: "+ crawlerStatusMap.get(key).getPort());
			client.makeRequest("http://"+key+"/stopcrawler", crawlerStatusMap.get(key).getPort(), new HashMap<String, String>());
		}
	}

	private void makeCrawlRequests() {
		//Read seed URLs
		FileReader fileReader;
		try {
			fileReader = new FileReader(new File(seedFileName));
			BufferedReader br = new BufferedReader(fileReader);
			List<String> seedUrls = new ArrayList<String>();
			String line = null;
			while((line = br.readLine())!=null) {
				seedUrls.add(line);
			}

			//Divide URLS among crawlers
			//TODO: implement URL hashing
			int urlsPerCrawlerCount = seedUrls.size()/crawlerStatusMap.keySet().size();
			Map<String, String[]> crawlerToUrlMap = new HashMap<String, String[]>();
			int ind = 0;
			for(String key: crawlerStatusMap.keySet()) {
				String urls[] = new String[urlsPerCrawlerCount];
				for(int i = 0; i<urlsPerCrawlerCount; i++) {
					urls[i] = seedUrls.get(ind++);
				}
				crawlerToUrlMap.put(key, urls);
			}

			//Form Json object for the request
			JSONArray crawlerList = new JSONArray(crawlerStatusMap.keySet().toArray(new String[crawlerStatusMap.keySet().size()]));			

			//Send seed URLs to each crawler
			for(String key: crawlerStatusMap.keySet()) {
				HttpClient client = new HttpClient();
				JSONObject requestObject = new JSONObject();

				requestObject.put("urls", new JSONArray(crawlerToUrlMap.get(key)));
				requestObject.put("crawler", crawlerList);
				requestObject.put("maxRequests", maxRequests);

				System.out.println("SENDING: TO:"+"http://"+key+"/startcrawler"
						+"\nPORT: "+crawlerStatusMap.get(key).getPort()
						+"\nCONTENT TYPE: "+"application/json"
						+"\nBODY STRING: "+requestObject.toString());

				client.makePostRequest("http://"+key+"/startcrawler", crawlerStatusMap.get(key).getPort(), "application/json", requestObject.toString());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws java.io.IOException {

	}
}
