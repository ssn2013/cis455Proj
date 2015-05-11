#Remove everything
echo "REMOVING EVERYTHING"
rm -rf /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps/pagerankmaster /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps/pagerankmaster.war
rm -rf /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps/pagerankworker /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps/pagerankworker.war
rm -rf /home/sruthi/Downloads/apache-tomcat-instance2/webapps/pagerankworker /home/sruthi/Downloads/apache-tomcat-instance2/webapps/pagerankworker.war
#rm -rf /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker3/webapps/worker /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker3/webapps/worker.war
#rm -rf /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker4/webapps/worker /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker4/webapps/worker.war

#Build master and first worker
echo "DEPLOYING MASTER AND FIRST WORKER"
ant clean
cp testConf/web-worker1.xml target/worker/WEB-INF/web.xml
ant all
mv pagerankmaster.war /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps
mv pagerankworker.war /home/sruthi/Downloads/apache-tomcat-6.0.43/webapps

#Build second worker
echo "DEPLOYING SECOND WORKER"
cp testConf/web-worker2.xml target/worker/WEB-INF/web.xml
ant war-worker
mv pagerankworker.war /home/sruthi/Downloads/apache-tomcat-instance2/webapps

#Build third worker
#echo "DEPLOYING THIRD WORKER"
#cp testConf/web-worker3.xml target/worker/WEB-INF/web.xml
#ant war-worker
#mv worker.war /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker3/webapps

#Build fourth worker
#echo "DEPLOYING FOURTH WORKER"
#cp testConf/web-worker4.xml target/worker/WEB-INF/web.xml
#ant war-worker
#mv worker.war /home/cis455/Downloads/apache-tomcat-multiple/tomcat-worker4/weba
