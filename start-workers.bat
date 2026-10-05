@echo off
echo =========================================
echo Building job-worker...
echo =========================================
cd job-worker
call .\gradlew.bat build -x test

echo.
echo =========================================
echo Starting 3 Worker Instances...
echo =========================================
start "Worker 1" java -jar build\libs\job-worker-0.0.1-SNAPSHOT.jar
start "Worker 2" java -jar build\libs\job-worker-0.0.1-SNAPSHOT.jar
start "Worker 3" java -jar build\libs\job-worker-0.0.1-SNAPSHOT.jar

cd ..
echo Done! Three new windows should have popped up, each running a worker!
