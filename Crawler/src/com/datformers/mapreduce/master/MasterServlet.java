package com.datformers.mapreduce.master;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.datformers.mapreduce.master.resources.WorkerStatusMap;
import com.datformers.mapreduce.util.HttpClient;
import com.datformers.mapreduce.util.JobDetails;

/*
 * Master servlet acting as the master node
 * PLEASE NOTE: All URLs are relative to the Apache Tomcat server (i.e master would be http://<ip>:<port>/master/<path of request>
 */
public class MasterServlet extends HttpServlet {

	static final long serialVersionUID = 455555001;
	private Map<String, WorkerStatusMap> workerStatusMaps = new HashMap<String, WorkerStatusMap>(); //map of all worker nodes and their informations
	private JobDetails presentMapJob; //details of present running job

	/*
	 * doGet method of servlet
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws java.io.IOException
	{
		System.out.println("MasterServlet:doGet: Got GET request "+request.getPathInfo());
		if(request.getPathInfo().contains("workerstatus")) { //redirect worker status calls
			processWorkerStatusRequest(request, response);
		} else if(request.getPathInfo().contains("status")) {  //redirect status calls to corresponding method
			processStatusRequest(request, response);
		} else {
			response.setContentType("text/html"); //regular information to print for general calls
			PrintWriter out = response.getWriter();
			out.println("<html><head><title>Master</title></head>");
			out.println("<body>Hi, I am the master!</body></html>");
		}
	}

	/*
	 * doPost method of servlet
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		System.out.println("Post request received: "+request.getPathInfo());
		if(request.getPathInfo().contains("status")) { //redirect status calls (from the form) to corresponding method
			processFormSubmissionPost(request, response);
		}
	}

	/*
	 * Method handling /status POST request from the form
	 */
	private void processFormSubmissionPost(HttpServletRequest request,
			HttpServletResponse response) {
		//Getting all values from form
		String className = request.getParameter("class");
		String inputDir = request.getParameter("inputDir");
		String outputDir = request.getParameter("outputDir");
		int noMapThreads = Integer.parseInt(request.getParameter("noMapThreads"));
		int noReduceThreads = Integer.parseInt(request.getParameter("noReduceThreads"));
		JobDetails requestJob = new JobDetails(); //temporary object to store details fetched from the form
		requestJob.setJob(className);
		requestJob.setInputDir(inputDir);
		requestJob.setOutputDir(outputDir);
		requestJob.setNumMapThreads(noMapThreads);
		requestJob.setNumReduceThreads(noReduceThreads);
		
		String htmlString = "<html><body>";

		//Sending data to the worker
		StringBuffer dataToSend = new StringBuffer("job="+className); //forming the data part for /runmap
		dataToSend.append("&input="+inputDir);
		dataToSend.append("&numThreads="+noMapThreads);
		int i =1;
		List<String> availableWorkers = new ArrayList<String>();
		for(String key: workerStatusMaps.keySet()) { //go through list of all worker and look for idle ones
			if(workerStatusMaps.get(key).getStatus().trim().equals("idle")) {
				availableWorkers.add(key);
				dataToSend.append("&worker"+i+"="+key);
				i++;
			}
		}
		requestJob.setNumWorkers(availableWorkers.size()); //keep track of worker working on the job
		dataToSend.append("&numWorkers="+availableWorkers.size());
		if(availableWorkers.size()==0) { //if not idle threads are found, display message and exit
			System.out.println("MasterServlet:processFormSubmissionPost: Insufficient worker counts");
			htmlString += "<p>No available workers. Try again later :(</p></body></html>";
			try {
				response.getWriter().println(htmlString);
				response.getWriter().flush();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				return;
			}
		}
		presentMapJob = requestJob; //if there are threads, set this to current job
		String data = dataToSend.toString();
		System.out.println("MasterServlet:processFormSubmissionPost: Datat being sent: "+data); 
		for(String key: availableWorkers) {
			String urlString = "http://"+key.trim()+"/worker/runmap";
			System.out.println("URL STRING: "+urlString);

			//Custom httpclient 
			HttpClient httpClient = new HttpClient();
			InputStream responseBody = httpClient.makePostRequest(urlString, Integer.parseInt(key.split(":")[1].trim()),"application/x-www-form-urlencoded",data);
			if(httpClient.getResponseCode()==200) {
				System.out.println("MasterServlet:processFormSubmissionPost: Successfully sent /runmap call to: "+key);
				htmlString+="<p>Job Started on: "+key+"</p><br/>";
			} else {	
				System.out.println("MasterServlet:processFormSubmissionPost: Unseccesful /runmap call to: "+key+" response: "+httpClient.getResponseCode());
			}
		}	
		try {
			htmlString+="</body></html>";
			response.getWriter().println(htmlString);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Method to handle /status GET request
	 */
	private void processStatusRequest(HttpServletRequest request,
			HttpServletResponse response) {
		try {
			response.setContentType("text/html");
			StringBuffer stringBufferHTML = new StringBuffer();//forming response body i.e form
			stringBufferHTML.append("<html><body><h1>Server Status Page</h1></br><h2>Status of Worker</h2>");
			stringBufferHTML.append("<table><tr><th>IP:Port</th><th>Status</th><th>Job</th><th>Keys Read</th><th>Keys Written</th></tr>");
			for(String key: workerStatusMaps.keySet()) {
				stringBufferHTML.append("<tr>");
				WorkerStatusMap workerStatusMap = workerStatusMaps.get(key); //fetch details of given worker from maps
				if(workerStatusMap!=null) { //display details in table
					stringBufferHTML.append("<td>"+workerStatusMap.getIPPort()+"</td>");
					stringBufferHTML.append("<td>"+workerStatusMap.getStatus()+"</td>");
					stringBufferHTML.append("<td>"+workerStatusMap.getJob()+"</td>");
					stringBufferHTML.append("<td>"+workerStatusMap.getKeysRead()+"</td>");
					stringBufferHTML.append("<td>"+workerStatusMap.getKeysWritten()+"</td>");
				} else {
					stringBufferHTML.append("<tr><td>"+workerStatusMap+"</td><td colspan=\"4\">Error fetching object</td></tr>");
				}
				stringBufferHTML.append("</tr>");
			}
			//Create form for adding job
			stringBufferHTML.append("</table><br/><h2>Submit a job</h2><form method=\"post\" action=\"status\">"
					+ "<table><tr><td>Class: </td><td><input type=\"text\" name=\"class\"></td></tr>"
					+ "<tr><td>Input Dir:</td><td><input type=\"text\" name=\"inputDir\"></td></tr>"
					+ "<tr><td>Output Dir:</td><td><input type=\"text\" name=\"outputDir\"></td></tr>"
					+ "<tr><td>Number of Map Threads:</td><td><input type=\"text\" name=\"noMapThreads\"></td></tr>"
					+ "<tr><td>No of Reduce Threads:</td><td><input type=\"text\" name=\"noReduceThreads\"></td></tr>"
					+ "<tr><td colspan=\"2\"><input type=\"submit\" value=\"Run\"></td></tr></table></form>"
					+ "<br/><p>Coded by: Sruthi Nair (sruthin@seas.upenn.edu)</p>");
			response.getWriter().println(stringBufferHTML.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Method to process /workerstatus POST requests
	 */
	private void processWorkerStatusRequest(HttpServletRequest request,
			HttpServletResponse response) {
		try {
			//fetch all details from the requst and put into the map
			WorkerStatusMap workerStatusMap = new WorkerStatusMap();
			int port = Integer.parseInt(request.getParameter("port"));
			String status = request.getParameter("status").trim();
			workerStatusMap.setStatus(status);
			String job = request.getParameter("job").trim();
			workerStatusMap.setJob(job);
			int keysRead = Integer.parseInt(request.getParameter("keysRead"));
			workerStatusMap.setKeysRead(keysRead);
			int keysWritten = Integer.parseInt(request.getParameter("keysWritten"));
			workerStatusMap.setKeysWritten(keysWritten);
			String ipAddress = request.getHeader("X-FORWARDED-FOR");
			if (ipAddress == null) {  
				ipAddress = request.getRemoteAddr();  
			}
			workerStatusMap.setIPPort(ipAddress+":"+port);
			workerStatusMaps.put(workerStatusMap.getIPPort(), workerStatusMap); //put details into the server's map of all workers' details
			System.out.println("WORKER UPDATE:- Port: "+port+" Status: "+status+" Job: "+job+" keysRead: "+keysRead+" keysWritten: "+keysWritten
					+" ipAddress: "+ipAddress+" Put into map: "+workerStatusMaps.get(workerStatusMap.getIPPort()).getJob());
			checkAndRunReduce(job); //check if relevant threads are waiting and run reduce
			response.setStatus(200);
		} catch (Exception e) {
			System.out.println("EXCEPTION: MasterServlet:processWorkerStatusRequest "+e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	 * Method called at every /workerstatus request to check if all workers are "waiting" and run reduce
	 */
	private void checkAndRunReduce(String jobName) {
		int count = 0;
		for(String key: workerStatusMaps.keySet()) { //for the given job, take a count of all the waiting workers
			WorkerStatusMap map = workerStatusMaps.get(key);
			if(map.getJob().equals(jobName)&&map.getStatus().equals("waiting"))
				count++;
		}

		if(count==presentMapJob.getNumWorkers()) { //if the count equals the number of workers initially assigned to the job, start reduce phase
			//make the body String
			StringBuffer buf = new StringBuffer();
			buf.append("job="+presentMapJob.getJob());
			buf.append("&output="+presentMapJob.getOutputDir());
			buf.append("&numThreads="+presentMapJob.getNumReduceThreads());
			String body = buf.toString();

			//for each given worker make /runreduce call
			for(String key: workerStatusMaps.keySet()) {
				HttpClient client = new HttpClient(); 
				String url = "http://"+key+"/worker/runreduce";
				client.makePostRequest(url, Integer.parseInt(key.split(":")[1].trim()), "application/x-www-form-urlencoded", body);
			}
		}
	}
}

